import { Select } from 'antd';
import type { ModelProviderView } from '@/service/api';
/** 默认模型是实际配置的标记，不再生成一个虚拟的0号模型选项。 */
export default function ChatModelSelect({providers,value,onChange,disabled=false,loading=false}:{providers:ModelProviderView[];value:number|null;onChange:(id:number)=>void;disabled?:boolean;loading?:boolean}) {
 const enabled=providers.filter(p=>p.enabled);const defaultModel=enabled.find(p=>p.isDefault);
 return <Select aria-label="对话模型" style={{width:'100%',minWidth:0}} size="small" value={value??defaultModel?.id} placeholder={loading?'加载模型…':enabled.length?'选择模型':'尚未配置模型'} loading={loading} disabled={disabled||loading||!enabled.length} optionFilterProp="label" showSearch options={enabled.map(p=>({value:p.id,label:`${p.name}${p.isDefault?' · 默认':''}`,title:`${p.name}（${p.model}）`}))} onChange={onChange}/>;
}
