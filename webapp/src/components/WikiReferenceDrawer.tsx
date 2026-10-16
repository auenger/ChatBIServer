import { useEffect, useState } from 'react';
import { Alert, Button, Drawer, Space, Spin, Typography } from 'antd';
import { history } from 'umi';
import { wikiApi, type WikiReference, type ReadResult } from '@/service/wiki';

/** 从工具实际读取记录打开同一发布版本，后台重新校验作用域与结构有效性。 */
export default function WikiReferenceDrawer({ reference, onClose }: { reference:WikiReference|null;onClose:()=>void }) {
 const [result,setResult]=useState<ReadResult|null>(null);const [error,setError]=useState('');
 const [start,setStart]=useState(1);const [loading,setLoading]=useState(false);
 useEffect(()=>{setStart(reference?.startLine??1);setResult(null);},[reference]);
 useEffect(()=>{if(!reference)return;let alive=true;setLoading(true);setError('');wikiApi.read(reference,start).then(r=>{if(alive)setResult(r);}).catch(e=>{if(alive)setError(e.message);}).finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};},[reference,start]);
 return <Drawer open={!!reference} onClose={onClose} width={680} title={reference?`${reference.title} · v${reference.revision}`:'参考知识'}>
  {error&&<Alert type="warning" showIcon message={error}/>}
  <Spin spinning={loading}><Typography.Paragraph type="secondary">实际读取的发布版本 · {reference?.path}</Typography.Paragraph>
   {result&&<><pre style={{whiteSpace:'pre-wrap',lineHeight:1.8,background:'#fafafa',padding:16}}>{result.content}</pre><Space>
    <Button disabled={start<=1} onClick={()=>setStart(Math.max(1,start-100))}>上一段</Button>
    <Button disabled={!result.truncated} onClick={()=>setStart(start+result.content.trimEnd().split('\n').length)}>下一段</Button>
    <Typography.Text type="secondary">共 {result.totalLines} 行</Typography.Text>
   </Space></>}
  </Spin>
  {reference&&<Button style={{marginTop:24}} onClick={()=>{history.push(`/datawiki?${reference.datasourceId ? `ds=${reference.datasourceId}` : `dataset=${reference.scopeId}`}&space=${reference.spaceId??''}&page=${reference.pageId}`);onClose();}}>打开知识管理页</Button>}
 </Drawer>;
}
