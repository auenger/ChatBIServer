import { Button, Table } from 'antd';
import { BulbOutlined, LoadingOutlined, ToolOutlined } from '@ant-design/icons';
import ChatMarkdown from './ChatMarkdown';
import type { WikiReference } from '@/service/wiki';
import './ChatUi.less';
export interface ChatToolCall { request:string; result?:string|null }
export interface ChatMessageView { role:'user'|'assistant'; content:string; reasoning?:string; tools?:ChatToolCall[] }
/** 分析页与知识浮窗共享正文、思考、工具回执和流式等待状态。 */
export default function ChatMessageBubble({message,streaming=false,onReference,onChanges}:{message:ChatMessageView;streaming?:boolean;onReference?:(reference:WikiReference)=>void;onChanges?:()=>void}) {
 const m=message;
 function result(text:string){
  try {
   if(text.startsWith('WIKI_REFERENCE:')){const r=JSON.parse(text.slice('WIKI_REFERENCE:'.length)) as WikiReference;return <Button size="small" type="link" onClick={()=>onReference?.(r)} disabled={!onReference}>已读取：{r.title} · v{r.revision}</Button>;}
   if(text.startsWith('WIKI_DRAFT_CHANGE:')){const r=JSON.parse(text.slice('WIKI_DRAFT_CHANGE:'.length)) as {title:string;draftVersion:number};return <Button size="small" type="link" onClick={onChanges} disabled={!onChanges}>已保存：{r.title} · 草稿 v{r.draftVersion}</Button>;}
   if(text.startsWith('ANALYSIS_RESULT:')){const r=JSON.parse(text.slice('ANALYSIS_RESULT:'.length)) as {operation:string;columns:string[];sample:(string|null)[][];rowCount:number;truncated:boolean;sources:{source:string;executionId:number;queriedAt:string}[]};return <details><summary>服务端结果：{r.operation} · {r.rowCount} 行{r.truncated?'（已截断）':''}</summary><div>{r.sources.map(s=>`${s.source} · 执行#${s.executionId} · ${new Date(s.queriedAt).toLocaleString()}`).join('；')}</div><Table size="small" pagination={false} scroll={{x:true}} columns={r.columns.map((c,index)=>({title:c,key:String(index),render:(_:unknown,row:(string|null)[])=>row[index]??'NULL'}))} dataSource={r.sample} rowKey={(_,i)=>String(i)}/><div>最多显示30行样本，计算使用服务端完整结果。</div></details>;}
  }catch{return '工具回执无法解析';}
  return text;
 }
 return <article className={`chat-message-bubble ${m.role==='user'?'chat-message-user':'chat-message-assistant'}`} aria-label={m.role==='user'?'你的消息':'助手消息'}>
  {m.role==='user'?m.content:<>
   {m.reasoning&&<details className="chat-message-reasoning" open={!m.content}><summary><BulbOutlined/> 思考过程</summary><div className="chat-message-reasoning-text">{m.reasoning}</div></details>}
   {!!m.tools?.length&&<details className="chat-message-tools"><summary><ToolOutlined/> 工具记录 · {m.tools.length} 次</summary>{m.tools.map((t,i)=><div key={i} className="chat-message-tool"><code>{t.request}</code>{t.result&&<div className={t.result.startsWith('失败')?'chat-message-tool-error':'chat-message-tool-result'}>{result(t.result)}</div>}</div>)}</details>}
   {m.content&&<div className="chat-markdown-body"><ChatMarkdown content={m.content} streaming={streaming}/></div>}
   {streaming&&!m.content&&<div className="chat-message-pending" role="status"><LoadingOutlined/> {m.tools?.length?'正在处理工具结果…':m.reasoning?'正在思考…':'正在回复…'}</div>}
   {!streaming&&!m.content&&!m.reasoning&&!m.tools?.length&&<span className="chat-message-pending">本轮未返回正文</span>}
  </>}
 </article>;
}
