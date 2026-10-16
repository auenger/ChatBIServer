import { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Grid, Input, Modal, Select, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { history } from 'umi';
import { datasetApi, wikiSpaceApi, type DatasetView, type Member, type SourceSpaces } from '@/service/datasets';
import { datasourceApi, type DataSourceProfile } from '@/service/api';
import { wikiApi, type WikiObject } from '@/service/wiki';
import './index.less';

/** 数据集组织明确的数据范围和知识选择；成员不会随同步自动扩展。 */
export default function DatasetsPage(){
 const screens=Grid.useBreakpoint();const [loading,setLoading]=useState(false);
 const [list,setList]=useState<DatasetView[]>([]);const [sources,setSources]=useState<DataSourceProfile[]>([]);
 const [editing,setEditing]=useState<DatasetView|null>(null);const [open,setOpen]=useState(false);const [busy,setBusy]=useState(false);
 const [name,setName]=useState('');const [description,setDescription]=useState('');const [enabled,setEnabled]=useState(true);const [members,setMembers]=useState<Member[]>([]);
 const [objects,setObjects]=useState<Record<number,WikiObject[]>>({});const [spaces,setSpaces]=useState<Record<number,SourceSpaces>>({});
 const fail=(e:unknown)=>message.error((e as Error).message);
 const load=async()=>{setLoading(true);try{const [d,s]=await Promise.all([datasetApi.list(),datasourceApi.list()]);setList(d);setSources(s);}catch(e){fail(e);}finally{setLoading(false);}};
 useEffect(()=>{load();},[]);
 async function loadSource(ds:number){try{const [o,s]=await Promise.all([wikiApi.objects(ds),wikiSpaceApi.source(ds)]);setObjects(old=>({...old,[ds]:o}));setSpaces(old=>({...old,[ds]:s}));}catch(e){fail(e);}}
 function edit(d:DatasetView|null){setEditing(d);setName(d?.dataset.name??'');setDescription(d?.dataset.description??'');setEnabled(d?.dataset.enabled??true);setMembers(d?.members.map(m=>({...m,resources:[...m.resources]}))??[]);setOpen(true);d?.members.forEach(m=>loadSource(m.datasourceId));}
 function change(index:number,patch:Partial<Member>){setMembers(old=>old.map((m,i)=>i===index?{...m,...patch}:m));}
 async function save(){setBusy(true);try{const body={name,description,enabled,expectedRevision:editing?.dataset.revision??0,members};if(editing)await datasetApi.update(editing.dataset.id,body);else await datasetApi.create(body);setOpen(false);await load();message.success('数据集已保存；历史会话保留原范围记录，变更后请开启新会话');}catch(e){fail(e);}finally{setBusy(false);}}
 function memberLabel(m:Member){return `${m.alias} · ${sources.find(s=>s.id===m.datasourceId)?.name??`数据库 #${m.datasourceId}`} · ${m.resources.length} 张表`;}
 function memberRow(m:Member){return <div key={m.id||m.datasourceId} className="dataset-member-row"><Tooltip title={m.alias}><span className="dataset-member-alias">{m.alias}</span></Tooltip><Tooltip title={sources.find(s=>s.id===m.datasourceId)?.name??String(m.datasourceId)}><span className="dataset-member-source">{sources.find(s=>s.id===m.datasourceId)?.name??`数据库 #${m.datasourceId}`}</span></Tooltip><span className="dataset-member-count">{m.resources.length} 张表</span></div>;}
 return <div className="datasets-page"><div className="datasets-toolbar"><Typography.Title level={4} style={{margin:0}}>数据集</Typography.Title><Space wrap><Button icon={<ReloadOutlined/>} loading={loading} onClick={load}>刷新</Button><Button type="primary" icon={<PlusOutlined/>} onClick={()=>edit(null)}>创建数据集</Button></Space></div>
  <Alert type="info" showIcon message="按业务组合多个数据库与具体表；继承库表知识，在数据集内维护统一指标和跨库关联。" style={{marginBottom:20}}/>
  <Table<DatasetView> rowKey={d=>d.dataset.id} loading={loading} dataSource={list} tableLayout="fixed" scroll={{x:1260}} pagination={{defaultPageSize:10,showSizeChanger:true,showTotal:total=>`共 ${total} 个数据集`}} columns={[
   {title:'数据集',width:270,render:(_,d)=><div className="dataset-name"><Tooltip title={d.dataset.name}><span>{d.dataset.name}</span></Tooltip><Tag color={d.dataset.enabled?'green':undefined}>{d.dataset.enabled?'启用':'停用'}</Tag></div>},
   {title:'业务说明',width:300,render:(_,d)=>d.dataset.description?<Typography.Paragraph className="dataset-description" ellipsis={{rows:2,tooltip:d.dataset.description}}>{d.dataset.description}</Typography.Paragraph>:<Typography.Text type="secondary">—</Typography.Text>},
   {title:'成员与范围',width:360,render:(_,d)=>d.members.length?<div><div className="dataset-range-summary">{d.members.length} 个数据库 · {d.members.reduce((sum,m)=>sum+m.resources.length,0)} 张表</div>{d.members.slice(0,2).map(memberRow)}{d.members.length>2&&<Tooltip trigger={['hover','focus']} title={<div style={{maxHeight:240,overflow:'auto'}}>{d.members.slice(2).map(m=><div key={m.id||m.datasourceId}>{memberLabel(m)}</div>)}</div>}><button className="dataset-more-members" type="button">另有 {d.members.length-2} 个数据库</button></Tooltip>}</div>:<Typography.Text type="secondary">尚未配置成员</Typography.Text>},
   {title:'配置版本',width:100,render:(_,d)=><Typography.Text code>v{d.dataset.revision}</Typography.Text>},
   {title:'操作',width:230,fixed:screens.lg?'right':undefined,render:(_,d)=><div className="dataset-actions"><Button size="small" onClick={()=>edit(d)}>配置</Button><Button size="small" onClick={()=>history.push(`/datawiki?dataset=${d.dataset.id}`)}>DataWiki</Button><Button size="small" disabled={!d.dataset.enabled||!d.members.length} onClick={()=>history.push(`/agentchat?dataset=${d.dataset.id}`)}>分析</Button></div>},
  ]}/>
  <Modal open={open} title={editing?'配置数据集':'创建数据集'} onCancel={()=>setOpen(false)} width={900} onOk={save} confirmLoading={busy} okText="保存数据集">
   <Form layout="vertical"><Form.Item label="业务名称"><Input value={name} onChange={e=>setName(e.target.value)} maxLength={100} placeholder="例如：销售经营数据集"/></Form.Item><Form.Item label="业务描述"><Input.TextArea value={description} onChange={e=>setDescription(e.target.value)} maxLength={8000}/></Form.Item><Form.Item label="启用"><Switch checked={enabled} onChange={setEnabled}/></Form.Item></Form>
   <Typography.Title level={5}>成员数据库与知识绑定</Typography.Title>
   {members.map((m,i)=><Card key={i} size="small" style={{marginBottom:16}} title={`成员 ${i+1}`} extra={<Button danger type="text" onClick={()=>setMembers(old=>old.filter((_,index)=>index!==i))}>移除成员</Button>}>
    <Form layout="vertical"><Space align="start" wrap><Form.Item label="数据库"><Select style={{width:240}} value={m.datasourceId||undefined} placeholder="选择数据库" options={sources.filter(s=>s.id===m.datasourceId||!members.some(x=>x.datasourceId===s.id)).map(s=>({value:s.id,label:s.name}))} onChange={id=>{change(i,{datasourceId:id,alias:`db_${id}`,resources:[],wikiSpaceId:null});loadSource(id);}}/></Form.Item><Form.Item label="成员别名"><Input value={m.alias} onChange={e=>change(i,{alias:e.target.value})} placeholder="例如：交易库、财务库"/></Form.Item><Form.Item label="使用的业务知识空间"><Select style={{width:260}} value={m.wikiSpaceId??0} options={[{value:0,label:`继承数据库默认${spaces[m.datasourceId]?`（${spaces[m.datasourceId].spaces.find(s=>s.id===spaces[m.datasourceId].defaultSpaceId)?.name}）`:''}`},...(spaces[m.datasourceId]?.spaces??[]).map(s=>({value:s.id,label:s.name}))]} onChange={id=>change(i,{wikiSpaceId:id||null})}/></Form.Item></Space>
     <Form.Item label="明确选择数据范围（先在 DataWiki 同步结构）"><Select mode="multiple" style={{width:'100%'}} showSearch optionFilterProp="label" value={m.resources.map(r=>JSON.stringify(r))} options={(objects[m.datasourceId]??[]).filter(o=>o.object.state==='ACTIVE').map(o=>({value:JSON.stringify({namespace:o.object.namespace,table:o.object.name}),label:`${o.object.namespace}.${o.object.name}`}))} onChange={v=>change(i,{resources:v.map(r=>JSON.parse(r))})}/></Form.Item>
     <Space><Button size="small" disabled={!objects[m.datasourceId]?.length} onClick={()=>change(i,{resources:(objects[m.datasourceId]??[]).filter(o=>o.object.state==='ACTIVE').map(o=>({namespace:o.object.namespace,table:o.object.name}))})}>选择当前已同步的全部表</Button><Typography.Text type="secondary">后续新增表不会自动进入数据集</Typography.Text></Space>
    </Form>
   </Card>)}
   <Button type="dashed" block onClick={()=>setMembers(old=>[...old,{id:0,datasourceId:0,alias:'',resources:[],wikiSpaceId:null}])}>添加成员数据库</Button>
   <Alert style={{marginTop:16}} type="info" message="成员知识选择只影响本数据集。数据库的全局默认 Wiki 在 DataWiki 的知识空间设置中修改。"/>
  </Modal>
 </div>;
}
