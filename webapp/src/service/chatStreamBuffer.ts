export interface StreamTool { request:string;result?:string|null }
export interface ChatDelta { content:string;reasoning:string;tools?:StreamTool[] }
/** 两处对话共用80ms缓冲；工具记录跨刷新保留，专用回执不会被尾随的通用提示覆盖。 */
export function createChatStreamBuffer(apply:(delta:ChatDelta)=>void,delay=80){
 let content='',reasoning='',tools:StreamTool[]=[],toolsDirty=false;let timer:ReturnType<typeof setTimeout>|undefined;let disposed=false;
 function flush(){if(timer)clearTimeout(timer);timer=undefined;if(disposed)return;if(content||reasoning||toolsDirty){const delta={content,reasoning,...(toolsDirty?{tools:tools.map(t=>({...t}))}:{})};content='';reasoning='';toolsDirty=false;apply(delta);}}
 function handle(event:string,data:string){if(disposed)return;switch(event){case 'token':content+=data;break;case 'reasoning':reasoning+=data;break;case 'tool':tools.push({request:data});toolsDirty=true;break;case 'toolResult':{const tool=tools[tools.length-1];if(!tool)return;if(data==='已返回工具结果'&&tool.result)return;tool.result=data;toolsDirty=true;break;}default:return;}if(!timer)timer=setTimeout(flush,delay);}
 return {handle,flush,dispose:()=>{disposed=true;if(timer)clearTimeout(timer);timer=undefined;}};
}
