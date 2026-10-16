import { useEffect, useRef, useState, type ReactNode, type PointerEvent as ReactPointerEvent } from 'react';
import { createPortal } from 'react-dom';
import { Button, Tooltip } from 'antd';
import { CommentOutlined, CloseOutlined, DragOutlined, PushpinOutlined, PushpinFilled, ExpandOutlined, MinusOutlined } from '@ant-design/icons';
import './WikiKnowledgeChat.less';
type Edge = 'left' | 'right' | 'top' | 'bottom';
type Position = { x:number; y:number; width:number; height:number; edge?:Edge };
const KEY='meper.wiki.assistant.window';
function fit(p:Position):Position {
 const width=Math.min(Math.max(340,p.width),Math.max(1,window.innerWidth-16));
 const height=Math.min(Math.max(360,p.height),Math.max(1,window.innerHeight-16));
 return {...p,width,height,x:p.edge==='left'?8:p.edge==='right'?window.innerWidth-width-8:Math.max(8,Math.min(p.x,window.innerWidth-width-8)),y:p.edge==='top'?8:p.edge==='bottom'?window.innerHeight-height-8:Math.max(8,Math.min(p.y,window.innerHeight-height-8))};
}
function initial():Position {
 const fallback={x:window.innerWidth-472,y:window.innerHeight-588,width:456,height:564};
 try {const saved=JSON.parse(localStorage.getItem(KEY)||'null');if(saved&&['x','y','width','height'].every(k=>Number.isFinite(saved[k])))return fit({...saved,edge:['left','right','top','bottom'].includes(saved.edge)?saved.edge:undefined});} catch {}
 return fit(fallback);
}
/** 非模态知识助手：指针移动/缩放，靠边后自动折叠，位置按浏览器保存。 */
export default function WikiFloatingWindow({open,onClose,children,busy,hold}:{open:boolean;onClose:()=>void;children:ReactNode;busy:boolean;hold:boolean}) {
 const [position,setPosition]=useState(initial);const [collapsed,setCollapsed]=useState(false);const [pinned,setPinned]=useState(false);const [moving,setMoving]=useState(false);
 const panel=useRef<HTMLDivElement>(null);const timer=useRef<ReturnType<typeof setTimeout>>();const cleanup=useRef<(()=>void)|null>(null);
 const latest=useRef(position);latest.current=position;
 const cancel=()=>{if(timer.current)clearTimeout(timer.current);};
 const persist=(p:Position)=>{setPosition(p);latest.current=p;try{localStorage.setItem(KEY,JSON.stringify(p));}catch{}};
 useEffect(()=>{if(open)setCollapsed(false);},[open]);
 useEffect(()=>{const resize=()=>persist(fit(latest.current));window.addEventListener('resize',resize);return()=>{window.removeEventListener('resize',resize);cancel();cleanup.current?.();};},[]);
 useEffect(()=>{cancel();if(pinned||hold)setCollapsed(false);},[pinned,hold]);
 function schedule(){cancel();if(latest.current.edge&&!pinned&&!hold&&!moving)timer.current=setTimeout(()=>{if(!panel.current?.contains(document.activeElement))setCollapsed(true);},700);}
 function gesture(event:ReactPointerEvent,resize?:string){
  if(event.button!==0||(!resize&&(event.target as HTMLElement).closest('button,a,input')))return;
  event.preventDefault();cancel();cleanup.current?.();setMoving(true);setCollapsed(false);
  const start={...latest.current};const x=event.clientX,y=event.clientY;let next=start;
  const move=(e:PointerEvent)=>{const dx=e.clientX-x,dy=e.clientY-y;
   if(!resize)next=fit({...start,edge:undefined,x:start.x+dx,y:start.y+dy});
   else {let w=start.width,h=start.height;if(resize.includes('e'))w+=dx;if(resize.includes('s'))h+=dy;if(resize.includes('w'))w-=dx;if(resize.includes('n'))h-=dy;
    next=fit({...start,edge:undefined,width:w,height:h});if(resize.includes('w'))next=fit({...next,x:start.x+start.width-next.width});if(resize.includes('n'))next=fit({...next,y:start.y+start.height-next.height});}
   setPosition(next);latest.current=next;
  };
  const end=(e:PointerEvent)=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',end);window.removeEventListener('pointercancel',end);cleanup.current=null;setMoving(false);
   if(!resize&&e.type!=='pointercancel'){const distances=[['left',next.x],['right',window.innerWidth-next.x-next.width],['top',next.y],['bottom',window.innerHeight-next.y-next.height]] as [Edge,number][];const closest=distances.sort((a,b)=>a[1]-b[1])[0];if(closest[1]<=28)next=fit({...next,edge:closest[0]});}persist(next);
  };
  window.addEventListener('pointermove',move);window.addEventListener('pointerup',end);window.addEventListener('pointercancel',end);cleanup.current=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',end);window.removeEventListener('pointercancel',end);};
 }
 if(!open)return null;
 const rail=collapsed&&position.edge;
 const style=rail?{left:position.edge==='right'?undefined:position.edge==='left'?0:position.x,right:position.edge==='right'?0:undefined,top:position.edge==='bottom'?undefined:position.edge==='top'?0:position.y,bottom:position.edge==='bottom'?0:undefined}: {left:position.x,top:position.y,width:position.width,height:position.height};
 return createPortal(<div ref={panel} className={`wiki-assistant-window ${rail?`is-rail dock-${position.edge}`:''} ${moving?'is-moving':''}`} style={style} role="region" aria-label="DataWiki 业务知识助手" onMouseEnter={()=>{cancel();setCollapsed(false);}} onMouseLeave={schedule} onFocus={()=>{cancel();setCollapsed(false);}} onBlur={schedule}>
  {rail?<button className="wiki-assistant-rail" onClick={()=>setCollapsed(false)} aria-label="展开业务知识助手"><CommentOutlined/><span>业务知识助手</span>{busy&&<span className="wiki-assistant-dot"/>}</button>:<>
   <header className="wiki-assistant-header" onPointerDown={e=>gesture(e)} onKeyDown={e=>{if(e.target!==e.currentTarget)return;const offsets:Record<string,[number,number]>={ArrowLeft:[-20,0],ArrowRight:[20,0],ArrowUp:[0,-20],ArrowDown:[0,20]};if(offsets[e.key]){e.preventDefault();const [dx,dy]=offsets[e.key];persist(fit({...position,edge:undefined,x:position.x+dx,y:position.y+dy}));}}} tabIndex={0} aria-label="知识助手标题栏，可拖动，方向键移动">
    <DragOutlined/><strong>业务知识助手</strong><span className="wiki-assistant-header-actions">
     <Tooltip title={pinned?'取消固定，贴边自动收起':'固定展开'}><Button size="small" type="text" aria-label={pinned?'取消固定':'固定展开'} icon={pinned?<PushpinFilled/>:<PushpinOutlined/>} onClick={()=>setPinned(!pinned)}/></Tooltip>
     <Tooltip title="恢复默认大小"><Button size="small" type="text" aria-label="恢复默认大小" icon={<ExpandOutlined/>} onClick={()=>persist(fit({...position,width:456,height:564}))}/></Tooltip>
     <Tooltip title="收起到右侧"><Button size="small" type="text" aria-label="收起到右侧" icon={<MinusOutlined/>} onClick={()=>{setPinned(false);persist(fit({...position,edge:'right'}));setCollapsed(true);}}/></Tooltip>
     <Button size="small" type="text" aria-label="关闭知识助手" icon={<CloseOutlined/>} onClick={onClose}/>
    </span>
   </header>
   <div className="wiki-assistant-body">{children}</div>
   {['n','s','e','w','ne','nw','se','sw'].map(direction=><div key={direction} className={`wiki-assistant-resize resize-${direction}`} onPointerDown={e=>gesture(e,direction)} {...(direction==='se'?{role:'slider',tabIndex:0,'aria-label':'调整知识助手大小，方向键调整宽高','aria-valuetext':`${Math.round(position.width)} × ${Math.round(position.height)}`,'aria-valuenow':Math.round(position.width),'aria-valuemin':280,'aria-valuemax':window.innerWidth,onKeyDown:(e:React.KeyboardEvent)=>{const offsets:Record<string,[number,number]>={ArrowLeft:[-20,0],ArrowRight:[20,0],ArrowUp:[0,-20],ArrowDown:[0,20]};if(offsets[e.key]){e.preventDefault();const [dw,dh]=offsets[e.key];persist(fit({...position,width:position.width+dw,height:position.height+dh}));}}}: {})}/>)}
  </>}
 </div>,document.body);
}
