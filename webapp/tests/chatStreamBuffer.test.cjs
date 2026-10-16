const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const ts=require('typescript');
function load(file,dependencies={}) {
 const output=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/service',file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
 const module={exports:{}};
 new Function('require','module','exports',output)(name=>{if(name in dependencies)return dependencies[name];throw new Error('Unexpected import '+name);},module,module.exports);
 return module.exports;
}
const {createChatStreamBuffer}=load('chatStreamBuffer.ts');
test('late tool results remain attached after the request was flushed',()=>{
 const changes=[];const buffer=createChatStreamBuffer(d=>changes.push(d));
 buffer.handle('tool','read_knowledge_draft');buffer.flush();
 buffer.handle('toolResult','已返回工具结果');buffer.flush();
 assert.deepEqual(changes[1].tools,[{request:'read_knowledge_draft',result:'已返回工具结果'}]);
 buffer.handle('tool','apply_knowledge_updates');buffer.handle('toolResult','WIKI_DRAFT_CHANGE:{"pageId":1}');buffer.flush();
 buffer.handle('toolResult','已返回工具结果');buffer.flush();
 assert.equal(changes.at(-1).tools[1].result,'WIKI_DRAFT_CHANGE:{"pageId":1}');
 assert.equal(changes.at(-1).tools.length,2);buffer.dispose();
});
test('flush preserves token whitespace and batches reasoning separately; disposal cancels pending updates',async()=>{
 const changes=[];const buffer=createChatStreamBuffer(d=>changes.push(d),10);
 buffer.handle('token','  SELECT');buffer.handle('token','\n    id');buffer.handle('reasoning','核对字段');buffer.flush();
 assert.equal(changes[0].content,'  SELECT\n    id');assert.equal(changes[0].reasoning,'核对字段');
 buffer.handle('token','stale');buffer.dispose();buffer.handle('token','ignored');
 await new Promise(resolve=>setTimeout(resolve,25));assert.equal(changes.length,1);
});
test('shared SSE decoding preserves indentation, multiline payload and split UTF-8 across CRLF frames',async()=>{
 const {streamEvents}=load('sse.ts',{'./base':{getToken:()=>null}});
 const bytes=new TextEncoder().encode('event: token\r\ndata:  标题\r\ndata:    正文\r\n\r\nevent: final\r\ndata:完成\r\n\r\n');
 const previousFetch=global.fetch;const frames=[];
 global.fetch=async()=>new Response(new ReadableStream({start(controller){for(let i=0;i<bytes.length;i+=2)controller.enqueue(bytes.slice(i,i+2));controller.close();}}));
 try {await streamEvents('/fixture',{},(event,data)=>frames.push({event,data}));}
 finally {global.fetch=previousFetch;}
 assert.deepEqual(frames,[{event:'token',data:'  标题\n    正文'},{event:'final',data:'完成'}]);
});
