import { useEffect, useRef, useState } from 'react';
import { Button, Input, Progress, Select, Space, Tooltip, Typography, message } from 'antd';
import { ClearOutlined, DeleteOutlined, HistoryOutlined, SendOutlined, BulbOutlined, ToolOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Popconfirm } from 'antd';
import type { ChatSessionSummary, DataSourceProfile, ModelProviderView } from '@/service/api';
import { chatApi, chatApiExtra, datasourceApi, modelProviderApi } from '@/service/api';
import { getToken } from '@/service/base';

/** 上下文窗口（token）：按用户约定按 100 万上下文模型计算比例。 */
const CONTEXT_WINDOW = 1_000_000;
/** 历史懒加载每页条数。 */
const HISTORY_PAGE = 20;
/** SSE 直连后端：dev 下绕过 webpack dev server 代理（其压缩中间件会把流式攒批成一次性输出）。 */
const SSE_BASE = process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : '';

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
  userBubble: {
    alignSelf: 'flex-end', maxWidth: '72%', background: '#1677ff', color: '#fff',
    padding: '8px 12px', borderRadius: '12px 12px 2px 12px', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
  },
  assistantBubble: {
    alignSelf: 'flex-start', maxWidth: '85%', background: '#fff', border: '1px solid #f0f0f0',
    padding: '8px 12px', borderRadius: '12px 12px 12px 2px', wordBreak: 'break-word',
  },
  mdBody: {
    fontSize: 14, lineHeight: 1.6,
    '& p': { margin: '4px 0' },
    // 表格：边框 + 表头底色 + 斑马纹 + 悬停 + 横向滚动（宽表不撑破气泡）
    '& table': {
      borderCollapse: 'collapse', margin: '8px 0', fontSize: 13,
      display: 'block', overflowX: 'auto', maxWidth: '100%',
    },
    '& th, & td': { border: '1px solid #e5e7eb', padding: '6px 12px', textAlign: 'left' },
    '& thead th': { background: '#fafafa', fontWeight: 600, whiteSpace: 'nowrap' },
    '& tbody tr:nth-child(even)': { background: '#fafafa' },
    '& tbody tr:hover': { background: '#f0f7ff' },
    // 代码块与行内代码
    '& pre': { background: '#f6f8fa', padding: '10px 12px', borderRadius: 6, overflowX: 'auto', margin: '6px 0' },
    '& code': { background: '#f0f2f5', padding: '1px 5px', borderRadius: 4, fontSize: 13 },
    '& pre code': { background: 'transparent', padding: 0 },
    '& ul, & ol': { paddingLeft: 20, margin: '4px 0' },
    '& blockquote': { borderLeft: '3px solid #d9d9d9', margin: '6px 0', paddingLeft: 10, color: '#595959' },
  },
  toolTag: { alignSelf: 'flex-start' },
  reasoningInline: {
    marginBottom: 6, fontSize: 12, color: '#8c8c8c',
    background: '#fafafa', border: '1px dashed #e8e8e8', borderRadius: 8, padding: '4px 10px',
    '& code': { background: 'transparent', padding: 0, fontSize: 12 },
  },
  reasoningText: { whiteSpace: 'pre-wrap', borderLeft: '2px solid #e0e0e0', paddingLeft: 8, marginTop: 4, maxHeight: 180, overflowY: 'auto' },
  composer: { padding: '12px 16px', borderTop: '1px solid #f0f0f0', background: '#fff' },
});

interface ChatMsg {
  role: 'user' | 'assistant';
  content: string;
  reasoning?: string;
  tools?: { request: string; result?: string | null }[];
  seq?: number;
}

/** 解析 fetch 流式响应的 SSE（EventSource 不支持 POST，自行按空行分帧）。 */
async function consumeSse(resp: Response, onEvent: (event: string, data: string) => void) {
  const reader = resp.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';
    for (const frame of frames) {
      let event = 'message';
      const dataLines: string[] = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) dataLines.push(line.slice(5));
      }
      if (dataLines.length > 0 || event !== 'message') {
        onEvent(event, dataLines.join('\n'));
      }
    }
  }
}

/** AI 对话页（0.1）：持久化会话列表 + SSE 流式对话 + 真实 usage 上下文比例 + 历史懒加载。 */
export default function AgentChatPage() {
  const { styles, cx } = useStyles();
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
        const [ps, ds] = await Promise.all([modelProviderApi.list(), datasourceApi.list()]);
        const enabled = ps.filter((p) => p.enabled);
        setProviders(enabled);
        setDatasources(ds);
        if (enabled.length > 0) setProviderId(enabled[0].id);
        else message.info('尚未登记可用模型，请先到「模型设置」登记');
        if (ds.length > 0) {
          setDatasourceId(ds[0].id);
          setNamespace(ds[0].databaseName ?? '');
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
      setMessages((m) => [...page.map((x) => ({
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
    if (!providerId || !datasourceId) {
      message.warning('请先选择模型与数据源');
      return;
    }
    setDraft('');
    setStreaming(true);
    setMessages((m) => [...m, { role: 'user', content: text }, { role: 'assistant', content: '' }]);
    // token 缓冲节流：80ms 批量刷一次，降低 markdown 全量重渲染频率（表格成型更平滑）
    let tokenBuffer = '';
    let reasoningBuffer = '';
    let toolBuffer: { request: string; result?: string | null }[] = [];
    let flushTimer: ReturnType<typeof setTimeout> | null = null;
    const flushBuffers = () => {
      if (flushTimer) {
        clearTimeout(flushTimer);
        flushTimer = null;
      }
      if (tokenBuffer) {
        const chunk = tokenBuffer;
        tokenBuffer = '';
        setMessages((m) => {
          const next = [...m];
          next[next.length - 1] = { ...next[next.length - 1], content: next[next.length - 1].content + chunk };
          return next;
        });
      }
      if (reasoningBuffer) {
        const chunk = reasoningBuffer;
        reasoningBuffer = '';
        setMessages((m) => {
          const next = [...m];
          const last = next[next.length - 1];
          next[next.length - 1] = { ...last, reasoning: (last.reasoning ?? '') + chunk };
          return next;
        });
      }
      if (toolBuffer.length > 0) {
        const items = toolBuffer;
        toolBuffer = [];
        setMessages((m) => {
          const next = [...m];
          const last = next[next.length - 1];
          next[next.length - 1] = { ...last, tools: [...(last.tools ?? []), ...items] };
          return next;
        });
      }
    };
    try {
      const resp = await fetch(`${SSE_BASE}/api/agent/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
        body: JSON.stringify({
          sessionId: sessionRef.current, providerId, datasourceId, namespace: namespace || null, message: text,
        }),
      });
      if (!resp.ok || !resp.body) {
        const err = await resp.json().catch(() => ({ message: resp.statusText }));
        throw new Error(err.message ?? `HTTP ${resp.status}`);
      }
      await consumeSse(resp, (event, data) => {
        if (event === 'start') {
          sessionRef.current = data;
          setActiveSession(data);
        } else if (event === 'token') {
          tokenBuffer += data;
          if (!flushTimer) {
            flushTimer = setTimeout(flushBuffers, 80);
          }
        } else if (event === 'reasoning') {
          reasoningBuffer += data;
          if (!flushTimer) {
            flushTimer = setTimeout(flushBuffers, 80);
          }
        } else if (event === 'tool') {
          toolBuffer.push({ request: data });
          if (!flushTimer) {
            flushTimer = setTimeout(flushBuffers, 80);
          }
        } else if (event === 'toolResult') {
          for (let i = toolBuffer.length - 1; i >= 0; i--) {
            if (toolBuffer[i].result === undefined || toolBuffer[i].result === null) {
              toolBuffer[i].result = data;
              break;
            }
          }
          if (!flushTimer) {
            flushTimer = setTimeout(flushBuffers, 80);
          }
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
      message.error((e as Error).message);
    } finally {
      setStreaming(false);
    }
  };

  const contextPct = usage ? Math.min(100, (usage.promptTokens / CONTEXT_WINDOW) * 100) : 0;

  return (
    <div className={styles.wrap}>
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
          <Select
            style={{ minWidth: 190 }} placeholder="模型" value={providerId}
            onChange={setProviderId}
            options={providers.map((p) => ({ value: p.id, label: `${p.name}（${p.model}）` }))}
          />
          <Select
            style={{ minWidth: 170 }} placeholder="数据源" value={datasourceId}
            onChange={(v) => {
              setDatasourceId(v);
              startNew();
              const ds = datasources.find((d) => d.id === v);
              setNamespace(ds?.databaseName ?? '');
            }}
            options={datasources.map((d) => ({ value: d.id, label: d.name }))}
          />
          <Tooltip title="库（MySQL）/ Schema（PostgreSQL 等），留空 = 数据源默认库">
            <Input
              style={{ width: 150 }} placeholder="库名/Schema" value={namespace}
              onChange={(e) => setNamespace(e.target.value)}
            />
          </Tooltip>
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
          {messages.map((m, i) =>
            m.role === 'user' ? (
              <div key={i} className={styles.userBubble}>{m.content}</div>
            ) : (
              <div key={i} className={styles.assistantBubble}>
                {m.reasoning && (
                  <details className={styles.reasoningInline} open={!m.content}>
                    <summary><BulbOutlined /> 思考过程</summary>
                    <div className={styles.reasoningText}>{m.reasoning}</div>
                  </details>
                )}
                {(m.tools ?? []).map((t, ti) => (
                  <div key={`t${ti}`} className={styles.reasoningInline}>
                    <div><ToolOutlined /> <code>{t.request}</code></div>
                    {t.result && (
                      <div style={{ paddingLeft: 18, color: t.result.startsWith('失败') ? '#cf1322' : '#52c41a' }}>
                        → {t.result}
                      </div>
                    )}
                  </div>
                ))}
                {m.content && (
                  <div className={styles.mdBody}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
                  </div>
                )}
              </div>
            ),
          )}
          <div ref={bottomRef} />
        </div>
        <div className={styles.composer}>
          <Space.Compact style={{ width: '100%' }}>
            <Input.TextArea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  send();
                }
              }}
              placeholder="问一个数据问题…（Enter 发送，Shift+Enter 换行）"
              autoSize={{ minRows: 1, maxRows: 5 }}
              disabled={streaming}
            />
            <Button type="primary" icon={<SendOutlined />} loading={streaming} onClick={send}>
              发送
            </Button>
          </Space.Compact>
        </div>
      </div>
    </div>
  );
}
