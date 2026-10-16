import { Button, Input, Tooltip } from 'antd';
import { SendOutlined, StopOutlined } from '@ant-design/icons';
import './ChatUi.less';
/** 共用输入交互：Enter发送，Shift+Enter换行，输入法组字期间不触发提交。 */
export default function ChatComposer({value,onChange,onSend,onStop,streaming=false,disabled=false,placeholder='输入消息…',hint}:{value:string;onChange:(value:string)=>void;onSend:()=>void;onStop?:()=>void;streaming?:boolean;disabled?:boolean;placeholder?:string;hint?:string}) {
 return <div className="chat-composer"><Input.TextArea aria-label="对话消息" value={value} onChange={e=>onChange(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing&&!disabled&&!streaming){e.preventDefault();if(value.trim())onSend();}}} placeholder={placeholder} autoSize={{minRows:2,maxRows:5}} maxLength={28000} disabled={disabled||streaming}/><div className="chat-composer-footer"><span>{hint??'Enter 发送 · Shift+Enter 换行'}</span>{streaming&&onStop?<Tooltip title="停止回复"><Button size="small" aria-label="停止回复" icon={<StopOutlined/>} onClick={onStop}>停止</Button></Tooltip>:<Button type="primary" size="small" icon={<SendOutlined/>} loading={streaming} disabled={disabled||!value.trim()} onClick={onSend}>发送</Button>}</div></div>;
}
