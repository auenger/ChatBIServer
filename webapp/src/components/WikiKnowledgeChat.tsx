import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, List, Modal, Popover, Select, Tabs, Tag, Tooltip, Typography, message } from 'antd';
import { history } from 'umi';
import ChatMessageBubble, { type ChatMessageView } from './ChatMessageBubble';
import ChatComposer from './ChatComposer';
import ChatModelSelect from './ChatModelSelect';
import { BookOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import WikiReferenceDrawer from './WikiReferenceDrawer';
import type { WikiReference } from '@/service/wiki';
import { createChatStreamBuffer } from '@/service/chatStreamBuffer';
import WikiFloatingWindow from './WikiFloatingWindow';
import { streamEvents } from '@/service/sse';
import { modelProviderApi, type ModelProviderView } from '@/service/api';
import { post } from '@/service/base';
import { chatScopeApi } from '@/service/datasets';
interface Change { pageId:number;spaceId:number;datasourceId:number;title:string;draftVersion:number;origin:string;changes:{field:string;before:string;after:string}[] }

/** 对话批量维护知识，正式发布由用户一次确认变更清单，不交给模型执行。 */
export default function WikiKnowledgeChat({open,spaceId,pageIds,context,ready,blocked,onClose,onChanged}:{open:boolean;spaceId:number;pageIds:number[];context:{scope:string;page:string;detail:string};ready:boolean;blocked:boolean;onClose:()=>void;onChanged:()=>void}){
 const [editScope,setEditScope]=useState<'ALL'|'SELECTED'>('ALL');
 const [fixedPages,setFixedPages]=useState<number[]>([]);
 const editablePages=editScope==='SELECTED'?fixedPages:[];
 useEffect(()=>{setEditScope('ALL');setFixedPages([]);},[spaceId]);
 const [providers,setProviders]=useState<ModelProviderView[]>([]);const [provider,setProvider]=useState<number|null>(null);const [providersLoading,setProvidersLoading]=useState(true);const [settingsOpen,setSettingsOpen]=useState(false);const [activeTab,setActiveTab]=useState('chat');const [reference,setReference]=useState<WikiReference|null>(null);const [input,setInput]=useState('');const [messages,setMessages]=useState<ChatMessageView[]>([]);
 const [changes,setChanges]=useState<Change[]>([]);const [selected,setSelected]=useState<number[]>([]);const [running,setRunning]=useState(false);const [publishing,setPublishing]=useState(false);const [review,setReview]=useState(false);const [activity,setActivity]=useState('');
 const messageList=useRef<HTMLDivElement>(null);const followBottom=useRef(true);
 const session=useRef<string|null>(null);const abort=useRef<AbortController|null>(null);const lastTarget=useRef('');const generation=useRef(0);const latestChanged=useRef(onChanged);latestChanged.current=onChanged;
 useEffect(()=>{if(!open)return;let alive=true;setProvidersLoading(true);modelProviderApi.list().then(p=>{if(alive)setProviders(p.filter(x=>x.enabled));}).catch(e=>{if(alive)message.error(e.message);}).finally(()=>{if(alive)setProvidersLoading(false);});return()=>{alive=false;};},[open]);
 useEffect(()=>{const key=`${spaceId}:${editScope}:${editablePages.join(',')}`;if(key!==lastTarget.current){lastTarget.current=key;generation.current++;abort.current?.abort();setRunning(false);setActivity('');setReview(false);setActiveTab('chat');setReference(null);setInput('');session.current=null;setMessages([]);setChanges([]);setSelected([]);}},[spaceId,editScope,editablePages.join(',')]);
 useEffect(()=>()=>abort.current?.abort(),[]);
 useEffect(()=>{const list=messageList.current;if(list&&followBottom.current)list.scrollTop=list.scrollHeight;},[messages,activity]);
 useEffect(()=>{if(!ready||blocked){generation.current++;abort.current?.abort();setRunning(false);setActivity('');}},[ready,blocked]);
 async function send(){if(!input.trim()||running||!ready||blocked||providersLoading||(!provider&&!providers.some(p=>p.isDefault)))return;followBottom.current=true;setActiveTab('chat');const current=++generation.current;const text=input;setInput('');setMessages(old=>[...old,{role:'user',content:text},{role:'assistant',content:''}]);setRunning(true);abort.current=new AbortController();
  const buffer=createChatStreamBuffer(delta=>{if(current!==generation.current)return;setMessages(old=>{const next=[...old];const last=next[next.length-1];if(!last)return old;next[next.length-1]={...last,content:last.content+delta.content,reasoning:(last.reasoning??'')+delta.reasoning,...(delta.tools?{tools:delta.tools}:{})};return next;});});
  try{await streamEvents('/agent/chat/knowledge/stream',{sessionId:session.current,providerId:provider||null,spaceId,pageIds:editablePages,focusPageIds:pageIds,message:`当前工作台：${context.scope}；页面：${context.page}；关注位置：${context.detail}。请依据已保存的知识核对业务信息。\n\n${text}`},(name,data)=>{
   if(current!==generation.current)return;
   buffer.handle(name,data);
   if(name==='start'){session.current=data;chatScopeApi.get(data).then(c=>{if(current===generation.current)setProvider(c.providerId);}).catch(()=>{});}
   if(name==='tool')setActivity(data);
   if(name==='toolResult'&&data.startsWith('WIKI_DRAFT_CHANGE:')){try{const change=JSON.parse(data.slice('WIKI_DRAFT_CHANGE:'.length)) as Change;setChanges(old=>{const previous=old.find(c=>c.pageId===change.pageId);const fields=new Map(previous?.changes.map(x=>[x.field,x])??[]);for(const item of change.changes){const first=fields.get(item.field);fields.set(item.field,{...item,before:first?.before??item.before});}return [...old.filter(c=>c.pageId!==change.pageId),{...change,changes:[...fields.values()]}];});setSelected(old=>old.includes(change.pageId)?old:[...old,change.pageId]);latestChanged.current();}catch{message.error('草稿变更回执解析失败');}}
   if(name==='error'){buffer.flush();setMessages(old=>{const next=[...old];const last=next[next.length-1];if(!last)return old;next[next.length-1]={...last,content:last.content+(last.content?'\n\n':'')+data};return next;});message.error(data);}
  },abort.current.signal);}catch(e){buffer.flush();if(current===generation.current){const stopped=(e as Error).name==='AbortError';const text=stopped?'已停止回复':`回复中断：${(e as Error).message}`;setMessages(old=>{const next=[...old];const last=next[next.length-1];if(!last)return old;next[next.length-1]={...last,content:last.content+(last.content?'\n\n':'')+text};return next;});if(!stopped)message.error((e as Error).message);}}finally{buffer.flush();buffer.dispose();if(current===generation.current){setRunning(false);setActivity('');}}
 }
 async function publish(){if(!session.current||!ready||blocked)return;const current=generation.current;setPublishing(true);try{const chosen=changes.filter(c=>selected.includes(c.pageId));const r=await post<{outcomes:{pageId:number;success:boolean;message:string}[]}>('/agent/chat/knowledge/publish-batch',{sessionId:session.current,items:chosen.map(c=>({pageId:c.pageId,expectedVersion:c.draftVersion}))});if(current!==generation.current){latestChanged.current();return;}const success=r.outcomes.filter(o=>o.success).map(o=>o.pageId);setChanges(old=>old.filter(c=>!success.includes(c.pageId)));setSelected(old=>old.filter(id=>!success.includes(id)));setReview(false);latestChanged.current();const errors=r.outcomes.filter(o=>!o.success);if(errors.length)Modal.warning({title:`已发布 ${success.length} 项，${errors.length} 项需处理`,content:errors.map(e=>e.message).join('；')});else message.success(`已统一发布 ${success.length} 项`);}catch(e){message.error((e as Error).message);}finally{setPublishing(false);}}
 const defaultModel=providers.find(p=>p.isDefault);const effectiveModel=providers.find(p=>p.id===(provider??defaultModel?.id));
 const canChat=!providersLoading&&!!effectiveModel;
 function newConversation(){generation.current++;abort.current?.abort();session.current=null;setMessages([]);setChanges([]);setSelected([]);setInput('');setActiveTab('chat');setActivity('');}
 const settings=<div className="wiki-assistant-settings">
  <div className="wiki-assistant-setting-label"><strong>模型</strong><Button type="link" size="small" onClick={()=>{setSettingsOpen(false);history.push('/modelproviders');}}>模型设置</Button></div>
  <ChatModelSelect providers={providers} value={provider} loading={providersLoading} disabled={running||publishing} onChange={id=>{newConversation();setProvider(id);}}/>
  {!providersLoading&&!effectiveModel&&<div className="wiki-assistant-setting-note">{providers.length?'请选择本次使用的模型。':'先添加并启用一个模型。'}</div>}
  <div className="wiki-assistant-setting-label"><strong>编辑范围</strong></div>
  <Select size="small" aria-label="知识编辑范围" style={{width:'100%'}} value={editScope} disabled={running||publishing} options={[{value:'ALL',label:'当前数据库 / 数据集的知识'},{value:'SELECTED',label:editScope==='SELECTED'?`指定 ${fixedPages.length} 页及本会话新建文档`:'当前关注页面及本会话新建文档',disabled:!pageIds.length}]} onChange={v=>{setEditScope(v);if(v==='SELECTED')setFixedPages([...pageIds]);}}/>
  <div className="wiki-assistant-setting-note">切换模型或编辑范围会开启新对话，已保存草稿保留。</div>
 </div>;
 return <WikiFloatingWindow open={open} onClose={onClose} busy={running} hold={review||publishing||settingsOpen}>
  <WikiReferenceDrawer reference={reference} onClose={()=>setReference(null)}/>
  <div className="wiki-assistant-context">
   <div className="wiki-assistant-focus"><BookOutlined/><div><Tooltip title={`${context.scope} · ${context.page} · ${context.detail}`}><strong>{context.page}</strong></Tooltip><div className="wiki-assistant-scope-label" title={context.scope}>{context.scope} · {editScope==='ALL'?'全范围':`指定 ${fixedPages.length} 页`}</div></div></div>
   <div className="wiki-assistant-actions"><Popover content={settings} title="对话设置" trigger="click" open={settingsOpen} onOpenChange={setSettingsOpen} placement="bottomRight"><Button size="small" type="text" icon={<SettingOutlined/>} className="wiki-assistant-model-trigger" aria-label="对话设置" title={effectiveModel?`${effectiveModel.name}（${effectiveModel.model}）`:'选择模型与编辑范围'}>{providersLoading?'加载中':effectiveModel?.name??'选择模型'}</Button></Popover><Tooltip title="新对话 · 已保存草稿保留"><Button size="small" type="text" aria-label="新对话" icon={<PlusOutlined/>} disabled={running||publishing} onClick={newConversation}/></Tooltip></div>
  </div>
  {(!ready||blocked)&&<div className="wiki-assistant-notice" role="status">{blocked?'页面有未保存修改或正在处理，请稍后补充草稿。':'正在加载当前知识范围…'}</div>}
  <Tabs className="wiki-assistant-tabs" size="small" activeKey={activeTab} onChange={setActiveTab} items={[
   {key:'chat',label:'对话补全',children:<section className="wiki-assistant-conversation">
    <div ref={messageList} className="wiki-assistant-messages" onScroll={e=>{const list=e.currentTarget;followBottom.current=list.scrollHeight-list.scrollTop-list.clientHeight<64;}}>
     {messages.length===0?<div className="wiki-assistant-empty"><strong>一起补全业务知识</strong><p>描述规则，或让我检查当前页还缺哪些说明。</p><div className="wiki-assistant-starters">{['检查当前页缺少哪些知识','补充金额单位和状态含义','整理表间关联与统计口径'].map(text=><Button key={text} size="small" onClick={()=>setInput(text)} disabled={!ready||blocked||!canChat}>{text}</Button>)}</div></div>:messages.map((m,i)=><ChatMessageBubble key={i} message={m} streaming={running&&i===messages.length-1} onReference={setReference} onChanges={()=>setActiveTab('changes')}/>)}
    </div>
    <div className="wiki-assistant-composer">{running&&activity&&<div className="wiki-assistant-activity" role="status">正在处理：{activity}</div>}<ChatComposer value={input} onChange={setInput} onSend={send} onStop={()=>abort.current?.abort()} streaming={running} disabled={!ready||blocked||!canChat} placeholder={canChat?'描述业务规则，或提出补全要求…':'请先在右上角选择或配置模型'} hint="Enter 发送 · 修改保存为草稿"/></div>

   </section>},
   {key:'changes',label:`草稿变更（${changes.length}）`,children:<section className="wiki-assistant-changes"><List dataSource={changes} locale={{emptyText:'对话保存草稿后在这里查看变更'}} renderItem={c=><List.Item><div style={{width:'100%'}}><Checkbox checked={selected.includes(c.pageId)} onChange={e=>setSelected(old=>e.target.checked?[...old,c.pageId]:old.filter(id=>id!==c.pageId))}>{c.title}</Checkbox><Tag>{c.datasourceId?'数据库级共享知识':'数据集知识'}</Tag><Tag style={{marginLeft:8}} color={c.origin==='AI_SUGGESTED'?'orange':'blue'}>{c.origin==='AI_SUGGESTED'?'AI 建议':'引用用户信息'}</Tag>{c.changes.map((x,i)=><div key={i} style={{fontSize:13,margin:'8px 0',overflowWrap:'anywhere'}}><strong>{x.field}</strong><div style={{color:'#73777f'}}>原：{x.before||'未填写'}</div><div>新：{x.after}</div></div>)}</div></List.Item>}/><Button type="primary" disabled={running||!selected.length||!ready||blocked} onClick={()=>setReview(true)}>统一确认并发布（{selected.length}）</Button></section>}
  ]}/>
  <Modal open={review} title="确认发布本次选中的业务知识" onCancel={()=>setReview(false)} onOk={publish} okText="发布选中草稿" confirmLoading={publishing}><Alert type="warning" showIcon message="请核对 AI 建议及业务口径。发布后这些内容会成为 Agent 分析依据。"/><List dataSource={changes.filter(c=>selected.includes(c.pageId))} renderItem={c=><List.Item>{c.title} · 草稿版本 {c.draftVersion}</List.Item>}/></Modal>
 </WikiFloatingWindow>;
}
