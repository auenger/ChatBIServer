package com.meper.chatbi.agent.gateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.agent.chat.ChatToolContext;
import java.util.function.Supplier;

/** 同一轮工具共享预算，响应按结构化数据返回，不把知识拼接成系统指令。 */
public class ScopedToolSupport {
 protected final ChatToolContext ctx;protected final ObjectMapper json;
 private int calls,characters;
 public ScopedToolSupport(ChatToolContext ctx,ObjectMapper json){this.ctx=ctx;this.json=json;}
 public synchronized String invoke(String name,Supplier<?> action){
  ctx.notifyTool(name);if(++calls>40||characters>=64000){ctx.notifyToolResult("本轮工具预算已用完");return "本轮工具预算已用完，请回答当前已获得信息或说明还需哪些信息";}
  try{Object value=action.get();String encoded=json.writeValueAsString(value);if(encoded.length()+characters>64000){ctx.notifyToolResult("操作已完成，结果过长，请在工作台查看或缩小读取范围");return "操作已完成，结果超过剩余上下文预算；请在工作台查看或缩小读取范围";}characters+=encoded.length();ctx.notifyToolResult("已返回工具结果");return encoded;}
  catch(RuntimeException e){ctx.notifyToolResult("失败："+e.getMessage());return "失败："+e.getMessage();}catch(Exception e){ctx.notifyToolResult("结果编码失败");return "结果编码失败";}
 }
 public void detail(String prefix,Object value){try{ctx.notifyToolResult(prefix+json.writeValueAsString(value));}catch(Exception e){throw new IllegalStateException("工具回执编码失败",e);}}
}
