import { useLocation } from 'umi';
import { datasetApi, chatScopeApi, type DatasetView } from '@/service/datasets';
import { useEffect, useRef, useState } from 'react';
import { Button, Input, Progress, Select, Space, Tooltip, Typography, message } from 'antd';
import { ClearOutlined, DeleteOutlined, HistoryOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import ChatMessageBubble, { type ChatMessageView } from '@/components/ChatMessageBubble';
import ChatComposer from '@/components/ChatComposer';
import ChatModelSelect from '@/components/ChatModelSelect';
import { createChatStreamBuffer } from '@/service/chatStreamBuffer';
import { streamEvents } from '@/service/sse';
import WikiReferenceDrawer from '@/components/WikiReferenceDrawer';
import type { WikiReference } from '@/service/wiki';
import { Popconfirm } from 'antd';
import type { ChatSessionSummary, DataSourceProfile, ModelProviderView } from '@/service/api';
import { chatApi, chatApiExtra, datasourceApi, modelProviderApi } from '@/service/api';

/** 上下文窗口（token）：按用户约定按 100 万上下文模型计算比例。 */
const CONTEXT_WINDOW = 1_000_000;
/** 历史懒加载每页条数。 */
const HISTORY_PAGE = 20;
const useStyles = createStyles({
  wrap: { display: 'flex', height: '100%' },
  sidebar: {
    width: 230, borderRight: '1px solid #f0f0f0', background: '#fff',
    display: 'flex', flexDirection: 'column', flexShrink: 0,
  },
  sessionItem: {
    padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f5f5f5',
    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
    '&:hover': { background: '#f5f8ff' },
  },
  sessionActive: { background: '#e6f0ff' },
  chat: { display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 },
  header: {
    display: 'flex', gap: 12, alignItems: 'center', padding: '12px 16px',
    borderBottom: '1px solid #f0f0f0', background: '#fff', flexWrap: 'wrap',
  },
  messages: { flex: 1, overflowY: 'auto', padding: '20px 16px', display: 'flex', flexDirection: 'column', gap: 12 },
  composer: { padding: '12px 16px', borderTop: '1px solid #f0f0f0', background: '#fff' },
});

interface ChatMsg extends ChatMessageView { seq?:number }

/** AI 对话页（0.1）：持久化会话列表 + SSE 流式对话 + 真实 usage 上下文比例 + 历史懒加载。 */
export default function AgentChatPage() {
  const { styles, cx } = useStyles();
  const location=useLocation();
  const [scopeKind,setScopeKind]=useState<'DATABASE'|'DATASET'>(()=>new URLSearchParams(location.search).has('dataset')?'DATASET':'DATABASE');
  const [datasets,setDatasets]=useState<DatasetView[]>([]);
  const [datasetId,setDatasetId]=useState<number|null>(null);
  const [wikiReference, setWikiReference] = useState<WikiReference|null>(null);
  const [providers, setProviders] = useState<ModelProviderView[]>([]);
  const [datasources, setDatasources] = useState<DataSourceProfile[]>([]);
  const [providerId, setProviderId] = useState<number | null>(null);
  const [datasourceId, setDatasourceId] = useState<number | null>(null);
  const [namespace, setNamespace] = useState('');
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [activeSession, setActiveSession] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [usage, setUsage] = useState<{ promptTokens: number; completionTokens: number } | null>(null);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState(false);
  const sessionRef = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const topRef = useRef<HTMLDivElement>(null);

  const loadSessions = async () => {
    try {
      setSessions(await chatApi.sessions());
    } catch {
      /* 会话列表失败不打断主流程 */
    }
  };

  useEffect(() => {
    (async () => {
      try {
        const [ps, ds, sets] = await Promise.all([modelProviderApi.list(), datasourceApi.list(), datasetApi.list()]);
        setDatasets(sets);
        const selected=Number(new URLSearchParams(location.search).get('dataset'));
        setDatasetId(sets.some(d=>d.dataset.id===selected)?selected:sets.find(d=>d.dataset.enabled)?.dataset.id??null);
        const enabled = ps.filter((p) => p.enabled);
        setProviders(enabled);
        setDatasources(ds);
        if (!enabled.some(p=>p.isDefault)) message.info('请选择本次模型，或在「模型设置」指定默认模型');
        if (ds.length > 0) {
          setDatasourceId(ds[0].id);
          setNamespace(ds[0].type==='MYSQL'?(ds[0].databaseName??''):'');
        }
        await loadSessions();
      } catch (e) {
        message.error((e as Error).message);
      }
    })();
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  /** 打开历史会话：先加载最近一页（懒加载更早），并恢复该会话最近一轮的真实用量。 */
  const openSession = async (sessionId: string) => {
    if (streaming) return;
    setActiveSession(sessionId);
    sessionRef.current = sessionId;
    setLoadingHistory(true);
    try {
      try{const c=await chatScopeApi.get(sessionId);setScopeKind(c.scopeType);if(c.scopeType==='DATASET')setDatasetId(c.scopeId);else setDatasourceId(c.scopeId);setProviderId(c.providerId);setNamespace(c.namespace??'');}catch{ /* 旧会话由服务端兼容检查，用户可明确重新选择范围 */ }
      const page = await chatApi.messages(sessionId, undefined, HISTORY_PAGE);
      setMessages(page.map((m) => ({
        role: m.role === 'USER' ? 'user' : 'assistant',
        content: m.content,
        reasoning: m.reasoning ?? undefined,
        tools: m.tools ?? undefined,
        seq: m.seq,
      })));
      setHasMore(page.length === HISTORY_PAGE);
      const lastUsage = [...page].reverse().find((m) => m.promptTokens != null);
      setUsage(lastUsage ? { promptTokens: lastUsage.promptTokens!, completionTokens: lastUsage.completionTokens ?? 0 } : null);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoadingHistory(false);
    }
  };

  /** 向上懒加载更早的消息。 */
  const loadEarlier = async () => {
    const oldest = messages.find((m) => m.seq !== undefined);
    if (!activeSession || oldest?.seq === undefined) return;
    setLoadingHistory(true);
    try {
      const page = await chatApi.messages(activeSession, oldest.seq, HISTORY_PAGE);
      const scrollTop = topRef.current?.parentElement?.scrollTop ?? 0;
      setMessages((m) => [...page.map<ChatMsg>((x) => ({
        role: x.role === 'USER' ? 'user' : 'assistant',
        content: x.content,
        reasoning: x.reasoning ?? undefined,
        tools: x.tools ?? undefined,
        seq: x.seq,
      })), ...m]);
      setHasMore(page.length === HISTORY_PAGE);
      requestAnimationFrame(() => {
        if (topRef.current?.parentElement) {
          topRef.current.parentElement.scrollTop = scrollTop + 120;
        }
      });
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoadingHistory(false);
    }
  };

  const startNew = () => {
    if (streaming) return;
    setActiveSession(null);
    sessionRef.current = null;
    setMessages([]);
    setHasMore(false);
    setUsage(null);
  };

  const send = async () => {
    const text = draft.trim();
    if (!text || streaming) return;
    if (!(scopeKind==='DATASET'?datasetId:datasourceId) || (providerId===null&&!providers.some(p=>p.isDefault))) {
      message.warning('请选择分析范围，并配置默认模型或选择本次模型');
      return;
    }
    setDraft('');
    setStreaming(true);
    setMessages((m) => [...m, { role: 'user', content: text }, { role: 'assistant', content: '' }]);
    const buffer=createChatStreamBuffer(delta=>setMessages(old=>{const next=[...old];const last=next[next.length-1];if(!last)return old;next[next.length-1]={...last,content:last.content+delta.content,reasoning:(last.reasoning??'')+delta.reasoning,...(delta.tools?{tools:delta.tools}:{})};return next;}));
    const flushBuffers=buffer.flush;
    try {
      await streamEvents('/agent/chat/stream',{
        sessionId:sessionRef.current,providerId,scopeType:scopeKind,scopeId:scopeKind==='DATASET'?datasetId:datasourceId,datasourceId:scopeKind==='DATABASE'?datasourceId:null,namespace:scopeKind==='DATABASE'?(namespace||null):null,message:text,
      },(event,data)=>{
        buffer.handle(event,data);
        if (event === 'start') {
          chatScopeApi.get(data).then(c=>setProviderId(c.providerId)).catch(()=>{});
          sessionRef.current = data;
          setActiveSession(data);
        } else if (event === 'usage') {
          try {
            const u = JSON.parse(data) as { promptTokens: number; completionTokens: number };
            setUsage(u);
          } catch { /* 忽略坏帧 */ }
        } else if (event === 'error') {
          flushBuffers();
          setMessages((m) => {
            const next = [...m];
            const last = next[next.length - 1];
            const prefix = last.content ? '\n\n' : '';
            next[next.length - 1] = { ...last, content: last.content + prefix + `⚠️ ${data}` };
            return next;
          });
        }
      });
      flushBuffers();
      loadSessions();
    } catch (e) {
      flushBuffers();
      setMessages(old=>{const next=[...old];const last=next[next.length-1];if(!last)return old;next[next.length-1]={...last,content:last.content+(last.content?'\n\n':'')+`回复中断：${(e as Error).message}`};return next;});
      message.error((e as Error).message);
    } finally {
      buffer.dispose();
      setStreaming(false);
    }
  };

  const contextPct = usage ? Math.min(100, (usage.promptTokens / CONTEXT_WINDOW) * 100) : 0;

  return (
    <div className={styles.wrap}>
      <WikiReferenceDrawer reference={wikiReference} onClose={()=>setWikiReference(null)} />
      <div className={styles.sidebar}>
        <div style={{ padding: 12 }}>
          <Button block icon={<ClearOutlined />} onClick={startNew}>新对话</Button>
        </div>
        <div style={{ flex: 1, overflowY: 'auto' }}>
          {sessions.length === 0 && (
            <Typography.Text type="secondary" style={{ padding: '8px 12px', display: 'block', fontSize: 12 }}>
              暂无历史会话
            </Typography.Text>
          )}
          {sessions.map((s) => (
            <div
              key={s.sessionId}
              className={cx(styles.sessionItem, activeSession === s.sessionId && styles.sessionActive)}
              style={{ display: 'flex', alignItems: 'center' }}
            >
              <Tooltip title={`${s.messageCount} 条消息 · ${s.lastAt}`}>
                <span
                  style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}
                  onClick={() => openSession(s.sessionId)}
                >
                  <HistoryOutlined style={{ marginRight: 6 }} />
                  {s.title || '（无标题会话）'}
                </span>
              </Tooltip>
              <Popconfirm
                title="删除该会话？"
                onConfirm={async () => {
                  try {
                    await chatApiExtra.deleteSession(s.sessionId);
                    if (activeSession === s.sessionId) startNew();
                    loadSessions();
                  } catch (e) {
                    message.error((e as Error).message);
                  }
                }}
              >
                <DeleteOutlined style={{ color: '#bfbfbf', marginLeft: 4 }} />
              </Popconfirm>
            </div>
          ))}
        </div>
      </div>
      <div className={styles.chat}>
        <div className={styles.header}>
          <Typography.Text strong>AI 对话</Typography.Text>
          <div style={{width:210}}><ChatModelSelect providers={providers} value={providerId} disabled={streaming} onChange={id=>{setProviderId(id);startNew();}}/></div>
          <Select style={{width:110}} value={scopeKind} disabled={streaming} options={[{value:'DATABASE',label:'数据库'},{value:'DATASET',label:'数据集'}]} onChange={kind=>{setScopeKind(kind);startNew();}}/>
          {scopeKind==='DATASET'?<Select style={{minWidth:190}} value={datasetId} disabled={streaming} placeholder="选择数据集" options={datasets.filter(d=>d.dataset.enabled).map(d=>({value:d.dataset.id,label:d.dataset.name}))} onChange={id=>{setDatasetId(id);startNew();}}/>:<Select
            disabled={streaming}
            style={{ minWidth: 170 }} placeholder="数据源" value={datasourceId}
            onChange={(v) => {
              setDatasourceId(v);
              startNew();
              const ds = datasources.find((d) => d.id === v);
              setNamespace(ds?.type==='MYSQL'?(ds.databaseName??''):'');
            }}
            options={datasources.map((d) => ({ value: d.id, label: d.name }))}
          />}
          {scopeKind==='DATABASE'&&<Tooltip title="库（MySQL）/ Schema（PostgreSQL 等），留空 = 数据源默认库">
            <Input
              style={{ width: 150 }} placeholder="默认库/Schema（可留空）" value={namespace}
              disabled={streaming} onChange={(e) => {setNamespace(e.target.value);startNew();}}
            />
          </Tooltip>}
          <Tooltip
            title={usage
              ? `最近一轮真实用量：输入 ${usage.promptTokens} + 输出 ${usage.completionTokens} tokens（按 100 万上下文窗口计）`
              : '发起一轮对话后显示真实 token 用量'}
          >
            <div style={{ width: 150 }}>
              <Progress
                percent={contextPct}
                size="small"
                strokeColor={contextPct > 95 ? '#ff4d4f' : contextPct > 80 ? '#faad14' : '#1677ff'}
                format={() => usage
                  ? `${(usage.promptTokens / 1000).toFixed(1)}k / 1M（${contextPct.toFixed(1)}%）`
                  : '上下文 —'}
              />
            </div>
          </Tooltip>
          <div style={{ flex: 1 }} />
        </div>
        <div className={styles.messages}>
          {hasMore && (
            <Button size="small" type="dashed" loading={loadingHistory} onClick={loadEarlier} style={{ alignSelf: 'center' }}>
              加载更早的消息
            </Button>
          )}
          {messages.length === 0 && (
            <Typography.Text type="secondary" style={{ alignSelf: 'center', margin: 'auto' }}>
              用自然语言问数据问题，例如「有哪些表？」「users 表里有多少行？」
            </Typography.Text>
          )}
          {messages.map((m,i)=><ChatMessageBubble key={m.seq??i} message={m} streaming={streaming&&i===messages.length-1} onReference={setWikiReference}/>)}
          <div ref={bottomRef} />
        </div>
        <div className={styles.composer}>
          <ChatComposer value={draft} onChange={setDraft} onSend={send} streaming={streaming} placeholder="问一个数据问题…"/>
        </div>
      </div>
    </div>
  );
}
