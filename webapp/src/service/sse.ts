import { getToken } from './base';
/** 同一SSE契约供分析和知识编辑使用；dev直连避免开发代理压缩攒批。 */
export async function streamEvents(path:string,body:unknown,onEvent:(name:string,data:string)=>void,signal?:AbortSignal){
 const base=process.env.NODE_ENV==='development'?'http://localhost:8080':'';
 const response=await fetch(base+'/api'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${getToken()}`},body:JSON.stringify(body),signal});
 if(!response.ok||!response.body){const error=await response.json().catch(()=>({message:response.statusText}));throw new Error(error.message??`HTTP ${response.status}`);}
 const reader=response.body.getReader();const decoder=new TextDecoder();let buffer='';
 try{for(;;){const {value,done}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true});const parts=buffer.split(/\r?\n\r?\n/);buffer=parts.pop()??'';for(const part of parts){let name='message';const lines:string[]=[];for(const line of part.split(/\r?\n/)){if(line.startsWith('event:'))name=line.slice(6).trim();else if(line.startsWith('data:'))lines.push(line.slice(5));}onEvent(name,lines.join('\n'));}}}finally{reader.releaseLock();}
}
