import { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Descriptions, Drawer, Empty, Form, Input, List, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd';
import { BookOutlined, DatabaseOutlined, EditOutlined, HistoryOutlined, SearchOutlined, SyncOutlined, PlusOutlined, SaveOutlined, MenuFoldOutlined, MenuUnfoldOutlined, InfoCircleOutlined, ExpandOutlined, SettingOutlined } from '@ant-design/icons';
import { history, useLocation } from 'umi';
import ChatMarkdown from '@/components/ChatMarkdown';
import WikiKnowledgeChat from '@/components/WikiKnowledgeChat';
import { datasetApi, wikiSpaceApi, type DatasetView, type SourceSpaces } from '@/service/datasets';
import WikiReferenceDrawer from '@/components/WikiReferenceDrawer';
import { datasourceApi, metadataApi, type DataSourceProfile } from '@/service/api';
import { wikiApi, type Binding, type Hit, type PageView, type Revision, type Semantics, type SyncRun, type WikiColumn, type WikiObject, type WikiPage, type WikiReference } from '@/service/wiki';
import './index.less';

const TYPES:Record<string,string>={STRUCTURE:'数据库结构',TABLE:'表说明',METRIC:'指标口径',GLOSSARY:'业务术语',RELATIONSHIP:'关联规则',RULE:'分析规则'};
const LABELS:Record<string,string>={businessName:'业务名称',description:'业务描述',grain:'数据粒度',aliases:'业务别名',unit:'单位',enums:'枚举含义',timeRule:'时间口径',filterRule:'常用过滤条件',notes:'注意事项'};
const TEMPLATES:Record<string,string>={
 METRIC:'## 业务定义\n\n## 计算口径\n\n## 单位与统计粒度\n\n## 时间口径\n\n## 过滤条件\n\n## 注意事项\n',
 GLOSSARY:'## 术语解释\n\n## 常用别名\n\n## 使用场景\n',
 RELATIONSHIP:'## 关联对象与字段\n\n## 关联基数\n一对一 / 一对多 / 多对多（请确认）\n\n## 连接条件\n\n## 重复统计风险\n',
 RULE:'## 适用范围\n\n## 业务规则\n\n## 例外情况\n\n## 注意事项\n',
};
const bindingKey=(b:Binding)=>`${b.objectId}|${b.column}`;
const parseBinding=(v:string):Binding=>({objectId:Number(v.slice(0,v.indexOf('|'))),column:v.slice(v.indexOf('|')+1)});
function Status({page}:{page:WikiPage}) {return <Space size={4} wrap>
 <Tag color={page.disabled?'default':page.publishedRevision?'green':'default'}>{page.disabled?'已停用':page.publishedRevision?`已发布 v${page.publishedRevision}`:page.type==='TABLE'&&!page.dirty?'业务说明待补充':'未发布'}</Tag>
 {page.dirty&&<Tag color="blue">有草稿</Tag>}{page.needsReview&&<Tag color="orange">结构需复核</Tag>}
 </Space>;}
function SemanticForm({value,onChange,keys}:{value:Semantics;onChange:(value:Semantics)=>void;keys:string[]}) {
 return <Form layout="vertical" className="wiki-form">{keys.map(key=><Form.Item key={key} label={LABELS[key]}>
  {['description','enums','notes','filterRule'].includes(key)?<Input.TextArea autoSize={{minRows:2,maxRows:7}} maxLength={8000} value={value[key]??''} onChange={e=>onChange({...value,[key]:e.target.value})}/>
   :<Input maxLength={8000} value={value[key]??''} onChange={e=>onChange({...value,[key]:e.target.value})} placeholder={key==='grain'?'例如：一行代表一个订单':key==='unit'?'例如：分、元、秒、件':undefined}/>}
 </Form.Item>)}</Form>;
}

/** DataWiki 管理工作台：草稿编辑与正式知识明确分开，原数据库结构只读。 */
export default function DataWikiPage() {
 const [directoryCollapsed,setDirectoryCollapsed]=useState(()=>localStorage.getItem('meper.wiki.directoryCollapsed')==='true');
 const [directoryWidth,setDirectoryWidth]=useState(()=>Math.min(420,Math.max(180,Number(localStorage.getItem('meper.wiki.directoryWidth'))||235)));
 const resizeCleanup=useRef<(()=>void)|null>(null);
 useEffect(()=>()=>resizeCleanup.current?.(),[]);
 function resizeDirectory(e:React.PointerEvent<HTMLDivElement>){if(directoryCollapsed)return;e.preventDefault();resizeCleanup.current?.();const start=e.clientX;const width=directoryWidth;let latest=width;const move=(event:PointerEvent)=>{latest=Math.min(420,Math.max(180,width+event.clientX-start));setDirectoryWidth(latest);};const end=()=>{localStorage.setItem('meper.wiki.directoryWidth',String(latest));window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',end);resizeCleanup.current=null;};window.addEventListener('pointermove',move);window.addEventListener('pointerup',end);resizeCleanup.current=end;}
 const [infoCollapsed,setInfoCollapsed]=useState(()=>localStorage.getItem('meper.wiki.infoCollapsed')==='true');
 const toggleDirectory=()=>setDirectoryCollapsed(v=>{localStorage.setItem('meper.wiki.directoryCollapsed',String(!v));return !v;});
 const toggleInfo=()=>setInfoCollapsed(v=>{localStorage.setItem('meper.wiki.infoCollapsed',String(!v));return !v;});
 const location=useLocation();const initial=new URLSearchParams(location.search);
 const [scopeKind,setScopeKind]=useState<'DATABASE'|'DATASET'>(()=>new URLSearchParams(location.search).has('dataset')?'DATASET':'DATABASE');
 const [datasets,setDatasets]=useState<DatasetView[]>([]);const [sourceSpaces,setSourceSpaces]=useState<SourceSpaces|null>(null);
 const [spaceOverride,setSpaceOverride]=useState<number|undefined>(()=>Number(new URLSearchParams(location.search).get('space'))||undefined);
 const [rootSpace,setRootSpace]=useState<number>();const [scopeVersion,setScopeVersion]=useState('');
 const [aiOpen,setAiOpen]=useState(false);
 const [selection,setSelection]=useState<number[]>([]);const [batchViews,setBatchViews]=useState<PageView[]>([]);const [batchOpen,setBatchOpen]=useState(false);
 const [batchHistory,setBatchHistory]=useState<{batchId:string;occurredAt:string;detail:string}[]>([]);
 const [spacesOpen,setSpacesOpen]=useState(()=>new URLSearchParams(location.search).get('configureWiki')==='1');const [newSpaceName,setNewSpaceName]=useState('');
 const [sources,setSources]=useState<DataSourceProfile[]>([]);const [ds,setDs]=useState<number>();
 const [pages,setPages]=useState<WikiPage[]>([]);const [objects,setObjects]=useState<WikiObject[]>([]);const [runs,setRuns]=useState<SyncRun[]>([]);
 const [mode,setMode]=useState('overview');const [pageId,setPageId]=useState<number>();const [view,setView]=useState<PageView|null>(null);
 const [title,setTitle]=useState('');const [content,setContent]=useState('');const [semantics,setSemantics]=useState<Semantics>({});
 const [columns,setColumns]=useState<Record<string,Semantics>>({});const [bindings,setBindings]=useState<Binding[]>([]);
 const [dirty,setDirty]=useState(false);const [busy,setBusy]=useState(false);const [loading,setLoading]=useState(false);const [error,setError]=useState('');
 const [filter,setFilter]=useState('');const [fieldFilter,setFieldFilter]=useState('all');const [fieldSearch,setFieldSearch]=useState('');const [field,setField]=useState<WikiColumn|null>(null);
 const [tab,setTab]=useState('business');const [revisions,setRevisions]=useState<Revision[]>([]);const [revision,setRevision]=useState<Revision|null>(null);
 const [search,setSearch]=useState('');const [pattern,setPattern]=useState('/**');const [hits,setHits]=useState<Hit[]>([]);const [cursor,setCursor]=useState<number|null>(null);const [searched,setSearched]=useState(false);
 const [reference,setReference]=useState<WikiReference|null>(null);const [publishOpen,setPublishOpen]=useState(false);
 const [syncOpen,setSyncOpen]=useState(false);const [namespaces,setNamespaces]=useState<string[]>([]);const [namespace,setNamespace]=useState('');const [target,setTarget]=useState('');const [tableOptions,setTableOptions]=useState<string[]>([]);
 const [createOpen,setCreateOpen]=useState(false);const [createType,setCreateType]=useState('METRIC');const [createTitle,setCreateTitle]=useState('');
 const epoch=useRef(0);const openedRoute=useRef('');
 const activeEditor=useRef({pageId,dirty});activeEditor.current={pageId,dirty};
 const api=scopeKind==='DATASET'&&ds?wikiApi.inDataset(ds):wikiApi;
 const pageApi=wikiApi.inSpace(view?.page.spaceId??rootSpace??1);
 const docApi=scopeKind==='DATABASE'&&spaceOverride?wikiApi.inSpace(spaceOverride):api;
 const scopeName=(scopeKind==='DATASET'?datasets.find(x=>x.dataset.id===ds)?.dataset.name:sources.find(x=>x.id===ds)?.name)??'未选择';
 const documentSpace=sourceSpaces?.spaces.find(x=>x.id===rootSpace);
 const defaultDocumentSpace=sourceSpaces?.spaces.find(x=>x.id===sourceSpaces.defaultSpaceId);
 const documentScope=scopeKind==='DATASET'?`数据集「${scopeName}」的专属文档`:`数据库「${scopeName}」 / ${documentSpace?.name??'加载文档空间中'}`;
 const route=(owner=ds)=>scopeKind==='DATASET'?`/datawiki?dataset=${owner}`:`/datawiki?ds=${owner}${spaceOverride?`&space=${spaceOverride}`:''}`;
 const fail=(e:unknown)=>message.error((e as Error).message);
 useEffect(()=>{Promise.all([datasourceApi.list(),datasetApi.list()]).then(([s,d])=>{setSources(s);setDatasets(d);const isDataset=initial.has('dataset');const selected=Number(initial.get(isDataset?'dataset':'ds'));setScopeKind(isDataset?'DATASET':'DATABASE');setDs(isDataset?(d.some(x=>x.dataset.id===selected)?selected:d[0]?.dataset.id):(s.some(x=>x.id===selected)?selected:s[0]?.id));}).catch(fail);},[]);
 useEffect(()=>{const q=new URLSearchParams(location.search);const kind=q.has('dataset')?'DATASET':'DATABASE';const id=Number(q.get(kind==='DATASET'?'dataset':'ds'));if(id&&((kind==='DATABASE'&&sources.some(s=>s.id===id))||(kind==='DATASET'&&datasets.some(d=>d.dataset.id===id)))&&(kind!==scopeKind||id!==ds)){setScopeKind(kind);setDs(id);setSpaceOverride(Number(q.get('space'))||undefined);}},[location.search,sources,datasets]);
 async function reviewBatch(ids:number[]){if(!ds)return;setBusy(true);try{const chosen=pages.filter(p=>ids.includes(p.id)&&(p.dirty||p.needsReview));if(!chosen.length){message.info('请选择待发布或待复核项');return;}const views=await Promise.all(chosen.map(p=>wikiApi.inSpace(p.spaceId).page(ds,p.id)));setBatchViews(views);setBatchOpen(true);}catch(e){fail(e);}finally{setBusy(false);}}
 async function publishBatch(){if(!ds)return;setBusy(true);try{const result=await api.batch(ds,batchViews.map(v=>({pageId:v.page.id,expectedVersion:v.page.draftVersion})));setBatchOpen(false);setSelection([]);await refresh();const errors=result.outcomes.filter(o=>!o.success);if(errors.length)Modal.warning({title:`已发布 ${result.outcomes.length-errors.length} 项，${errors.length} 项失败`,content:errors.map(e=>e.message).join('；')});else message.success(`已统一发布 ${result.outcomes.length} 项`);}catch(e){fail(e);}finally{setBusy(false);}}
 function populate(v:PageView) {setView(v);setTitle(v.page.title);setContent(v.page.draftContent);setSemantics(v.object?.semantics??{});setColumns(Object.fromEntries((v.object?.columns??[]).map(c=>[c.name,c.semantics])));setBindings(v.bindings);setDirty(false);setField(null);}
 async function refresh(source=ds) {
  if(!source)return;const e=epoch.current;
  let p:WikiPage[],o:WikiObject[],r:SyncRun[];
  if(scopeKind==='DATASET'){
   const dataset=await datasetApi.get(source);const data=await Promise.all([api.pages(source),api.objects(source),datasetApi.scope(source)]);p=data[0];o=data[1];r=[];
   if(e===epoch.current){setRootSpace(dataset.wikiSpaceId);setSourceSpaces(null);setScopeVersion(data[2].version);}
  }else{
   const info=await wikiSpaceApi.source(source);if(spaceOverride&&!info.spaces.some(x=>x.id===spaceOverride))throw new Error('知识空间不属于所选数据库');
   const data=await Promise.all([wikiApi.pages(source),wikiApi.objects(source),wikiApi.runs(source)]);p=data[0];o=data[1];r=data[2];
   if(spaceOverride){const chosen=await wikiApi.inSpace(spaceOverride).pages(source);p=[...p.filter(x=>x.type==='TABLE'||x.type==='STRUCTURE'),...chosen.filter(x=>x.type!=='TABLE'&&x.type!=='STRUCTURE')];}
   if(e===epoch.current){setSourceSpaces(info);setRootSpace(spaceOverride??info.defaultSpaceId);}
  }
  if(e!==epoch.current)return;setPages(p.filter(x=>x.type!=='STRUCTURE'));setObjects(o);setRuns(r);

 }
 useEffect(()=>{const e=++epoch.current;setPages([]);setObjects([]);setView(null);setPageId(undefined);setMode('overview');setError('');setDirty(false);setHits([]);setSearched(false);setRuns([]);setRevisions([]);setRootSpace(undefined);setSelection([]);setNamespaces([]);setNamespace('');setLoading(!!ds);
  if(!ds)return;refresh(ds).catch(x=>{if(e===epoch.current)setError(x.message);}).finally(()=>{if(e===epoch.current)setLoading(false);});
  if(scopeKind==='DATABASE')metadataApi.namespaces(ds).then(n=>{if(e===epoch.current){setNamespaces(n);const requested=initial.get('namespace');setNamespace(n.includes(requested??'')?requested!:n[0]??'');}}).catch(fail);
 },[ds,scopeKind,spaceOverride]);
 useEffect(()=>{if(!ds||!namespace||scopeKind!=='DATABASE')return;let alive=true;metadataApi.tables(ds,namespace).then(t=>{if(alive)setTableOptions(t.map(x=>x.name));}).catch(fail);return()=>{alive=false;};},[ds,namespace]);
 useEffect(()=>{if(!ds||!pageId)return;const e=epoch.current;let alive=true;setLoading(true);setError('');wikiApi.inSpace(pages.find(p=>p.id===pageId)?.spaceId??rootSpace??1).page(ds,pageId).then(v=>{if(alive&&e===epoch.current)populate(v);}).catch(x=>{if(alive)setError(x.message);}).finally(()=>{if(alive)setLoading(false);});
  wikiApi.inSpace(pages.find(p=>p.id===pageId)?.spaceId??rootSpace??1).revisions(ds,pageId).then(r=>{if(alive)setRevisions(r);}).catch(fail);
  return()=>{alive=false;};
 },[ds,pageId]);
 useEffect(()=>{if(!ds||!pages.length)return;const route=`${ds}:${location.search}`;if(openedRoute.current===route)return;const q=new URLSearchParams(location.search);const id=Number(q.get('page'));
  const requested=pages.find(p=>p.id===id)??pages.find(p=>{const o=objects.find(x=>x.object.id===p.objectId);return o?.object.namespace===q.get('namespace')&&o?.object.name===q.get('table');});
  if(requested){openedRoute.current=route;setMode('page');setPageId(requested.id);}
 },[pages,objects,location.search]);
 function navigate(action:()=>void) {if(dirty)Modal.confirm({title:'当前修改尚未保存',content:'离开将放弃本次编辑，已保存的草稿会保留。',okText:'放弃修改并离开',cancelText:'继续编辑',onOk:()=>{if(view)populate(view);else setDirty(false);action();}});else action();}
 function openPage(p:WikiPage){navigate(()=>{setMode('page');setTab('business');setPageId(p.id);if(ds)history.replace(route()+`&page=${p.id}`);});}
 function editSemantics(v:Semantics){setSemantics(v);setDirty(true);}
 function editColumn(name:string,key:string,value:string){setColumns(prev=>({...prev,[name]:{...prev[name],[key]:value}}));setDirty(true);}
 async function save(){if(!ds||!view)return;setBusy(true);try{const v=await pageApi.save(ds,view.page.id,{expectedVersion:view.page.draftVersion,title,content,semantics,columns:Object.fromEntries((view.object?.columns??[]).filter(c=>c.state==='ACTIVE').map(c=>[c.name,columns[c.name]??{}])),bindings});populate(v);await refresh();message.success('草稿已保存；Agent 仍使用正式发布内容');}catch(e){fail(e);}finally{setBusy(false);}}
 async function publish(){if(!ds||!view)return;setBusy(true);try{populate(await pageApi.publish(ds,view.page.id,view.page.draftVersion));await refresh();setRevisions(await pageApi.revisions(ds,view.page.id));setPublishOpen(false);message.success('发布成功，Agent 可按需检索新版本');}catch(e){fail(e);}finally{setBusy(false);}}
 async function performSync(){if(!ds||!namespace)return;setBusy(true);try{const r=await wikiApi.sync(ds,namespace,target||undefined);await refresh();if(view)populate(await pageApi.page(ds,view.page.id));message.success(`同步完成：新增 ${r.added}、变化 ${r.changed}、消失 ${r.missing}`);setSyncOpen(false);}catch(e){fail(e);await refresh().catch(fail);}finally{setBusy(false);}}
 async function performSearch(next=0){if(!ds)return;setLoading(true);try{const r=await api.search(ds,search,pattern,next);setHits(next?[...hits,...r.hits]:r.hits);setCursor(r.nextCursor);setSearched(true);setMode('search');}catch(e){fail(e);}finally{setLoading(false);}}
 const optionBindings=objects.filter(o=>o.object.state==='ACTIVE').flatMap(o=>[{value:bindingKey({objectId:o.object.id,column:''}),label:`${o.object.namespace}.${o.object.name}（整表）`},...o.columns.filter(c=>c.state==='ACTIVE').map(c=>({value:bindingKey({objectId:o.object.id,column:c.name}),label:`${o.object.namespace}.${o.object.name}.${c.name}`}))]);
 const shownPages=pages.filter(p=>{const object=objects.find(o=>o.object.id===p.objectId);return `${p.title} ${p.path} ${object?.semantics.businessName??''}`.toLowerCase().includes(filter.toLowerCase());});
 const tableGroups=Array.from(new Set(objects.map(o=>o.object.namespace)));
 const activeColumns=view?.object?.columns.filter(c=>c.state==='ACTIVE')??[];
 const shownColumns=activeColumns.filter(c=>(fieldFilter!=='missing'||!(columns[c.name]?.description??'').trim())&&(fieldFilter!=='unit'||!(columns[c.name]?.unit??'').trim())&&`${c.name} ${columns[c.name]?.businessName??''} ${columns[c.name]?.description??''}`.toLowerCase().includes(fieldSearch.toLowerCase()));
 const reviewPages=pages.filter(p=>p.needsReview);const columnsTotal=objects.filter(o=>o.object.state==='ACTIVE').flatMap(o=>o.columns.filter(c=>c.state==='ACTIVE'));
 const missingColumns=columnsTotal.filter(c=>!c.semantics.description?.trim()).length;
 function refsLabel(ref:Binding){const o=objects.find(x=>x.object.id===ref.objectId);return `${o?.object.namespace??''}.${o?.object.name??'对象已删除'}${ref.column?'.'+ref.column:''}`;}
 function bindingPicker(){return <Select mode="multiple" showSearch optionFilterProp="label" style={{width:'100%'}} placeholder="选择文档依赖的表或字段，发布前必填" value={bindings.map(bindingKey)} options={optionBindings} onChange={v=>{setBindings(v.map(parseBinding));setDirty(true);}}/>;}
 const business=<><Form layout="vertical"><Form.Item label="知识标题"><Input value={title} maxLength={255} onChange={e=>{setTitle(e.target.value);setDirty(true);}}/></Form.Item></Form>
  {view?.object&&<><Descriptions size="small" bordered column={2}><Descriptions.Item label="物理对象">{view.object.object.namespace}.{view.object.object.name}</Descriptions.Item><Descriptions.Item label="结构状态">{view.object.object.state==='ACTIVE'?'正常':'对象已消失'}</Descriptions.Item><Descriptions.Item label="数据库原始备注" span={2}>{view.object.object.remarks||'数据库未提供备注'}</Descriptions.Item></Descriptions>
   <div style={{marginTop:20}}><SemanticForm value={semantics} onChange={editSemantics} keys={['businessName','description','grain','aliases','timeRule','filterRule','notes']}/></div></>}
  {!view?.object&&<Form layout="vertical"><Form.Item label="关联资源">{bindingPicker()}</Form.Item></Form>}
  <Form layout="vertical"><Form.Item label={view?.object?'补充业务规则（Markdown）':'文档正文（Markdown）'}><Input.TextArea value={content} onChange={e=>{setContent(e.target.value);setDirty(true);}} autoSize={{minRows:10,maxRows:30}} maxLength={60000}/></Form.Item></Form>
 </>;
 const fieldList=<><Space wrap style={{marginBottom:16}}><Input.Search placeholder="查找字段或业务名称" value={fieldSearch} onChange={e=>setFieldSearch(e.target.value)} allowClear/>
  <Select value={fieldFilter} onChange={setFieldFilter} options={[{value:'all',label:'全部字段'},{value:'missing',label:'缺少描述'},{value:'unit',label:'缺少单位'}]}/>
  <Typography.Text type="secondary">简单说明可直接编辑，枚举与规则点击「详情」</Typography.Text></Space>
  <Table<WikiColumn> rowKey="name" size="small" pagination={{pageSize:20,showSizeChanger:true}} scroll={{x:1000}} dataSource={shownColumns} columns={[
   {title:'物理字段',dataIndex:'name',width:180,render:(name,c)=><Space><Typography.Text code>{name}</Typography.Text>{c.structure.primaryKey&&<Tag>PK</Tag>}</Space>},
   {title:'类型',width:140,render:(_,c)=>c.structure.typeName},
   {title:'业务名称',width:180,render:(_,c)=><Input aria-label={`${c.name}业务名称`} value={columns[c.name]?.businessName??''} onChange={e=>editColumn(c.name,'businessName',e.target.value)}/>},
   {title:'业务描述',width:280,render:(_,c)=><Input.TextArea aria-label={`${c.name}业务描述`} value={columns[c.name]?.description??''} autoSize={{minRows:1,maxRows:4}} onChange={e=>editColumn(c.name,'description',e.target.value)}/>},
   {title:'单位',width:100,render:(_,c)=><Input aria-label={`${c.name}单位`} value={columns[c.name]?.unit??''} onChange={e=>editColumn(c.name,'unit',e.target.value)}/>},
   {title:'枚举',width:85,render:(_,c)=>columns[c.name]?.enums?'已填写':'—'},
   {title:'操作',width:80,fixed:'right',render:(_,c)=><Button type="link" size="small" onClick={()=>setField(c)}>详情</Button>},
  ]}/>
  {!!view?.object?.columns.some(c=>c.state==='MISSING')&&<Alert type="warning" showIcon message="消失字段的说明仍保留在历史镜像中，不进入新发布文档" description={view.object.columns.filter(c=>c.state==='MISSING').map(c=>c.name).join('、')}/>}
 </>;
 const versions=<><Alert type="info" showIcon message="历史版本不可变。恢复内容会生成新草稿，确认后重新发布。" style={{marginBottom:16}}/>
  <Table<Revision> rowKey="revision" dataSource={revisions} columns={[{title:'版本',render:(_,r)=>`v${r.revision}`},{title:'发布人',dataIndex:'publishedBy'},{title:'发布时间',render:(_,r)=>new Date(r.publishedAt).toLocaleString()},
   {title:'操作',render:(_,r)=><Space><Button type="link" onClick={()=>setRevision(r)}>查看</Button><Button type="link" disabled={dirty||busy} onClick={()=>Modal.confirm({title:`恢复 v${r.revision} 到草稿？`,content:'当前结构不回退，仍存在的字段说明将恢复；不会立即发布。',onOk:async()=>{if(!ds||!view)return;try{populate(await pageApi.restore(ds,view.page.id,r.revision,view.page.draftVersion));await refresh();message.success('已恢复为新草稿，请复核发布');}catch(e){fail(e);}}})}>恢复到草稿</Button></Space>}]} />
 </>;
 const overview=<><div className="wiki-section-title"><Typography.Title level={4}>数据源知识概览</Typography.Title><Typography.Text type="secondary">结构镜像与业务说明保存在 ChatBI，发布后供 Agent 按需检索</Typography.Text></div>
  <div className="wiki-stats">{[
   ['已采集对象',objects.filter(o=>o.object.state==='ACTIVE').length,'tables'],['已发布知识',pages.filter(p=>p.publishedRevision&&!p.disabled&&!p.needsReview).length,'published'],['缺少描述的字段',missingColumns,'missing'],['待复核知识',reviewPages.length,'review'],
  ].map(([label,value,key])=><button key={String(key)} onClick={()=>setMode(String(key))}><span>{label}</span><strong>{value}</strong></button>)}</div>
  {!pages.length?<Empty description="尚未采集知识：同步结构 → 补充表字段说明 → 发布"><Button type="primary" onClick={()=>setSyncOpen(true)}>开始同步结构</Button></Empty>:<>
   <Typography.Title level={5}>待处理事项</Typography.Title>
   <List dataSource={pages.filter(p=>p.needsReview||p.dirty).slice(0,20)} locale={{emptyText:'当前没有待发布或待复核的知识'}} renderItem={p=><List.Item actions={[<Button key="open" type="link" onClick={()=>openPage(p)}>处理</Button>]}><List.Item.Meta title={<Space>{p.title}<Status page={p}/></Space>} description={p.needsReview?'依赖结构发生变化，正式内容暂不参与 Agent 检索':p.publishedRevision?'草稿尚未发布，Agent 使用现有正式版本':'补充业务说明后发布供 Agent 使用'}/></List.Item>}/>
   <Typography.Title level={5}>最近同步</Typography.Title>{runs[0]?<Typography.Paragraph>{new Date(runs[0].startedAt).toLocaleString()} · {runs[0].namespace} · {runs[0].status==='SUCCESS'?'成功':runs[0].status==='FAILED'?'失败':'进行中'}</Typography.Paragraph>:<Typography.Text type="secondary">尚无同步记录</Typography.Text>}
  </>}
 </>;
 const pageTable=(items:WikiPage[]) => <Table<WikiPage> rowKey="id" dataSource={items} rowSelection={{selectedRowKeys:selection,onChange:keys=>setSelection(keys.map(Number))}} onRow={p=>({onDoubleClick:()=>openPage(p)})} columns={[
  {title:'知识名称',dataIndex:'title',render:(v,p)=><Button type="link" onClick={()=>openPage(p)}>{v}</Button>},{title:'类型',render:(_,p)=>TYPES[p.type]},{title:'状态',render:(_,p)=><Status page={p}/>},{title:'更新人',dataIndex:'updatedBy'},{title:'更新时间',render:(_,p)=>new Date(p.updatedAt).toLocaleString()},
 ]}/>;
 return <div className="wiki-workspace">
  <header className="wiki-toolbar"><Space className="wiki-toolbar-scope" wrap><BookOutlined/><Typography.Text strong>DataWiki</Typography.Text><Select aria-label="知识范围" value={scopeKind} style={{width:100}} options={[{value:'DATABASE',label:'数据库'},{value:'DATASET',label:'数据集'}]} onChange={kind=>navigate(()=>{setScopeKind(kind);setSpaceOverride(undefined);const id=kind==='DATABASE'?sources[0]?.id:datasets[0]?.dataset.id;setDs(id);history.replace(kind==='DATABASE'?`/datawiki?ds=${id??''}`:`/datawiki?dataset=${id??''}`);})}/><Select aria-label="数据源" placeholder="选择数据源" value={ds} options={scopeKind==='DATABASE'?sources.map(s=>({value:s.id,label:s.name})):datasets.map(d=>({value:d.dataset.id,label:d.dataset.name}))} onChange={v=>navigate(()=>{setDs(v);setSpaceOverride(undefined);history.replace(scopeKind==='DATASET'?`/datawiki?dataset=${v}`:`/datawiki?ds=${v}`);})} style={{minWidth:180}}/></Space>
   <Input.Search aria-label="搜索知识" className="wiki-global-search" placeholder="搜索业务名、字段、指标……" value={search} onChange={e=>setSearch(e.target.value)} onSearch={()=>navigate(()=>performSearch())} enterButton={<SearchOutlined/>}/>
   <div className="wiki-toolbar-actions" role="group" aria-label="知识操作">
    <div className="wiki-toolbar-tools">
     <Button type="text" icon={<ExpandOutlined/>} title="专注编辑" onClick={()=>{setDirectoryCollapsed(true);setInfoCollapsed(true);window.dispatchEvent(new CustomEvent('meper.layout.focus'));localStorage.setItem('meper.wiki.directoryCollapsed','true');localStorage.setItem('meper.wiki.infoCollapsed','true');}}>专注编辑</Button>
     <Button type="text" icon={<SettingOutlined/>} disabled={!sourceSpaces||scopeKind!=='DATABASE'} onClick={()=>setSpacesOpen(true)}>空间设置</Button>
     <Button type="text" icon={<SyncOutlined/>} disabled={!ds||busy||scopeKind==='DATASET'} onClick={()=>navigate(()=>{setTarget('');if(view?.object){setNamespace(view.object.object.namespace);setTarget(view.object.object.name);}setSyncOpen(true);})}>同步结构</Button>
    </div>
    <div className="wiki-toolbar-create">
     <Button icon={<PlusOutlined/>} disabled={!rootSpace||loading} onClick={()=>setCreateOpen(true)}>新建文档</Button>
     <Button className="wiki-ai-button" icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m10 3 2.3 6.7L19 12l-6.7 2.3L10 21l-2.3-6.7L1 12l6.7-2.3L10 3Z" fill="currentColor"/><path d="m19 1 1.1 3.4L23.5 5.5l-3.4 1.1L19 10l-1.1-3.4-3.4-1.1 3.4-1.1L19 1Z" fill="currentColor"/></svg>} disabled={!rootSpace||dirty} onClick={()=>setAiOpen(true)}>AI 补充知识</Button>
    </div>
   </div>
  </header>
  <div style={{padding:'8px 20px',borderBottom:'1px solid #e3e5e8',display:'flex',gap:12,alignItems:'center',flexWrap:'wrap'}}><Typography.Text type="secondary">{documentScope}</Typography.Text>{scopeKind==='DATABASE'&&rootSpace&&<Tag color={rootSpace===sourceSpaces?.defaultSpaceId?'blue':'orange'}>{rootSpace===sourceSpaces?.defaultSpaceId?'数据库默认文档空间':'临时查看 · 未修改数据库默认'}</Tag>}<Button size="small" disabled={busy||!selection.length} onClick={()=>reviewBatch(selection)}>发布选中（{selection.length}）</Button><Button size="small" disabled={busy||!pages.some(p=>p.dirty||p.needsReview)} onClick={()=>reviewBatch(shownPages.filter(p=>p.dirty||p.needsReview).map(p=>p.id))}>发布当前目录范围</Button><Button size="small" onClick={()=>setMode('pending')}>待发布清单</Button>{scopeKind==='DATASET'&&ds&&<Button size="small" onClick={()=>history.push('/datasets')}>配置成员范围</Button>}</div>
  {!sources.length?<Empty style={{margin:'auto'}} description="请先登记一个测试数据源"><Button onClick={()=>history.push('/datasources')}>去数据源管理</Button></Empty>:<div className="wiki-body">
   <aside className={`wiki-directory ${directoryCollapsed?'collapsed':''}`} style={{width:directoryCollapsed?44:directoryWidth}}><Button type="text" aria-label={directoryCollapsed?'展开知识目录':'收起知识目录'} icon={directoryCollapsed?<MenuUnfoldOutlined/>:<MenuFoldOutlined/>} onClick={toggleDirectory}/><div className="wiki-directory-content"><Input placeholder="过滤知识目录" value={filter} onChange={e=>setFilter(e.target.value)} allowClear/>
    {[['overview','知识概览'],['search','搜索知识'],['review','待复核'],['pending','待发布'],['batches','发布记录'],['runs','同步记录']].map(([key,label])=><button className={mode===key?'active':''} key={key} onClick={()=>navigate(()=>setMode(key))}>{label}{key==='review'&&reviewPages.length>0&&<Badge count={reviewPages.length}/>}</button>)}
    <div className="wiki-directory-title"><DatabaseOutlined/> 库表知识</div>
    {tableGroups.map(ns=><details key={ns} open><summary>{ns}</summary>{shownPages.filter(p=>p.type==='TABLE'&&objects.find(o=>o.object.id===p.objectId)?.object.namespace===ns).map(p=><button key={p.id} className={mode==='page'&&pageId===p.id?'active':''} onClick={()=>openPage(p)} title={p.title}>{scopeKind==='DATASET'?'['+(sources.find(s=>s.id===p.datasourceId)?.name??p.datasourceId)+'] ':''}{p.title}{p.needsReview&&<span className="wiki-review-dot" aria-label="需复核"/>}</button>)}</details>)}
    <div className="wiki-directory-title"><BookOutlined/> {scopeKind==='DATABASE'?'数据库知识文档':'数据集与成员库文档'}</div><div className="wiki-document-scope" title={documentScope}>{scopeKind==='DATABASE'?`${scopeName} · ${documentSpace?.name??'加载中'}`:'本数据集文档与成员数据库继承文档'}</div>
    {Object.entries(TYPES).filter(([key])=>!['TABLE','STRUCTURE'].includes(key)).map(([key,label])=><details key={key} open><summary>{label}</summary>{shownPages.filter(p=>p.type===key).map(p=><button key={p.id} className={mode==='page'&&pageId===p.id?'active':''} onClick={()=>openPage(p)}>{scopeKind==='DATASET'?(p.datasourceId?'['+(sources.find(s=>s.id===p.datasourceId)?.name??p.datasourceId)+'] ':'[数据集] '):''}{p.title}</button>)}</details>)}
   </div>{!directoryCollapsed&&<div className="wiki-directory-resizer" role="separator" aria-label="调整知识目录宽度" aria-orientation="vertical" tabIndex={0} onPointerDown={resizeDirectory} onKeyDown={e=>{if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();const next=Math.min(420,Math.max(180,directoryWidth+(e.key==='ArrowRight'?16:-16)));setDirectoryWidth(next);localStorage.setItem('meper.wiki.directoryWidth',String(next));}}}/>}</aside>
   <main className="wiki-main"><Spin spinning={loading}>{error?<Alert type="error" showIcon message={error} action={<Button onClick={()=>refresh().then(()=>setError('')).catch(fail)}>重试</Button>}/>:<>
    {mode==='overview'&&overview}
    {['tables','published','review','missing','pending'].includes(mode)&&<><Typography.Title level={4}>{({tables:'库表知识',published:'已发布知识',review:'结构待复核',missing:'字段说明待补充',pending:'待发布与批量处理'} as Record<string,string>)[mode]}</Typography.Title>{pageTable(pages.filter(p=>mode==='tables'?p.type==='TABLE':mode==='published'?p.publishedRevision>0&&!p.disabled&&!p.needsReview:mode==='review'?p.needsReview:mode==='pending'?p.dirty||p.needsReview:objects.find(o=>o.object.id===p.objectId)?.columns.some(c=>c.state==='ACTIVE'&&!c.semantics.description?.trim())))}</>}
    {mode==='batches'&&<><Typography.Title level={4}>发布记录</Typography.Title><Button onClick={()=>{if(rootSpace)wikiApi.inSpace(rootSpace).batches(ds!).then(setBatchHistory).catch(fail);}}>加载发布记录</Button><List dataSource={batchHistory} locale={{emptyText:'点击加载，查看当前知识范围的批量发布回执'}} renderItem={b=><List.Item><div><Typography.Text strong>{new Date(b.occurredAt).toLocaleString()}</Typography.Text><Typography.Paragraph type="secondary">批次 {b.batchId}</Typography.Paragraph>{(()=>{try{const d=JSON.parse(b.detail) as {outcomes:{pageId:number;success:boolean;message:string}[]};return d.outcomes.map(o=><div key={o.pageId}>页面 #{o.pageId} · {o.success?'已发布':o.message}</div>);}catch{return '回执解析失败';}})()}</div></List.Item>}/></>}
    {mode==='runs'&&<><Typography.Title level={4}>结构同步记录</Typography.Title><Alert type="info" message="只有完整成功的指定范围采集才判断对象消失；失败保留上次镜像。" style={{marginBottom:16}}/><Table<SyncRun> rowKey="id" dataSource={runs} columns={[{title:'范围',render:(_,r)=>`${r.namespace}${r.table?'.'+r.table:'（完整命名空间）'}`},{title:'状态',render:(_,r)=><Tag color={r.status==='SUCCESS'?'green':r.status==='FAILED'?'red':'blue'}>{r.status==='SUCCESS'?'成功':r.status==='FAILED'?'失败':'进行中'}</Tag>},{title:'时间',render:(_,r)=>new Date(r.startedAt).toLocaleString()},{title:'结果',dataIndex:'detail',render:(v:string)=>{try{const x=JSON.parse(v);return `采集 ${x.total} · 新增 ${x.added} · 变化 ${x.changed} · 消失 ${x.missing}`;}catch{return v;}}}]} /></>}
    {mode==='search'&&<><Typography.Title level={4}>搜索正式知识</Typography.Title><Space wrap style={{marginBottom:16}}><Input value={search} onChange={e=>setSearch(e.target.value)} placeholder="业务术语、字段或指标" onPressEnter={()=>performSearch()}/><Button type="primary" onClick={()=>performSearch()}>搜索</Button><details><summary>高级路径过滤</summary><Input value={pattern} onChange={e=>setPattern(e.target.value)} placeholder="/**"/></details></Space>
     <Alert type="info" showIcon message="仅搜索已发布且结构有效的知识；未发布、停用和待复核内容不参与检索。" style={{marginBottom:16}}/>
     <List dataSource={hits} locale={{emptyText:searched?'没有匹配的正式知识，请调整关键词或补充并发布说明':'输入关键词开始搜索，也可留空发现正式文档'}} renderItem={h=><List.Item><List.Item.Meta title={<Space><Button type="link" onClick={()=>setReference({datasourceId:h.datasourceId,spaceId:h.spaceId,...(scopeKind==='DATASET'?{scopeType:'DATASET',scopeId:ds,scopeVersion}:{}),pageId:h.pageId,path:h.path,title:h.title,revision:h.revision,startLine:Math.max(1,h.line-3)})}>{h.title}</Button><Tag>{TYPES[h.type]}</Tag><Typography.Text type="secondary">v{h.revision} · 第 {h.line} 行</Typography.Text></Space>} description={<><div>{h.excerpt}</div><Typography.Text type="secondary">{h.path}</Typography.Text></>}/></List.Item>}/>
     {cursor!==null&&<Button onClick={()=>performSearch(cursor)}>加载更多结果</Button>}
    </>}
    {mode==='page'&&view&&<>{scopeKind==='DATASET'&&view.page.datasourceId>0&&<Alert type="info" showIcon message="这是成员数据库的共享知识，修改和发布会影响其他继承该知识的数据集。" style={{marginBottom:16}}/>}<div className="wiki-page-heading"><div><Typography.Title level={4}>{view.page.title}</Typography.Title><Status page={view.page}/></div><Button icon={<HistoryOutlined/>} onClick={()=>setTab('versions')}>版本历史</Button></div>
     {view.page.needsReview&&<Alert showIcon type="warning" message="结构发生变化，请核对字段与关联口径。复核发布前，Agent 不读取本页。" style={{marginBottom:16}}/>}
     {view.page.disabled&&<Alert showIcon type="info" message="本页已停用，Agent 不参与检索。重新发布可恢复使用。" style={{marginBottom:16}}/>}
     <Tabs activeKey={tab} onChange={setTab} items={[{key:'business',label:view.object?'业务说明':'文档编辑',children:business},...(view.object?[{key:'fields',label:`字段 (${activeColumns.length})`,children:fieldList}]:[]),{key:'preview',label:'文档预览',children:<><Alert message={dirty?'先保存草稿以生成最新预览':'这是草稿预览，正式版本在发布差异中查看'} type="info" style={{marginBottom:16}}/><ChatMarkdown content={view.preview}/></>},{key:'versions',label:'版本历史',children:versions}]}/>
    </>}
   </>}</Spin></main>
   {mode==='page'&&view&&<aside className={`wiki-inspector ${infoCollapsed?'collapsed':''}`}><Button type="text" aria-label={infoCollapsed?'展开页面信息':'收起页面信息'} icon={<InfoCircleOutlined/>} onClick={toggleInfo}/><div className="wiki-inspector-content"><Typography.Title level={5}>页面信息</Typography.Title><Descriptions size="small" column={1}><Descriptions.Item label="知识归属">{view.page.datasourceId?`数据库：${sources.find(x=>x.id===view.page.datasourceId)?.name??view.page.datasourceId}`:`数据集：${scopeName}`}</Descriptions.Item><Descriptions.Item label="知识空间">{sourceSpaces?.spaces.find(x=>x.id===view.page.spaceId)?.name??(view.page.datasourceId?'成员数据库知识空间':'数据集专属空间')}</Descriptions.Item><Descriptions.Item label="最后编辑">{view.page.updatedBy}</Descriptions.Item><Descriptions.Item label="更新时间">{new Date(view.page.updatedAt).toLocaleString()}</Descriptions.Item><Descriptions.Item label="正式版本">{view.page.publishedRevision?`v${view.page.publishedRevision}`:'尚未发布'}</Descriptions.Item><Descriptions.Item label="结构同步">{view.object?new Date(view.object.object.syncedAt).toLocaleString():'依赖对象的结构镜像'}</Descriptions.Item></Descriptions>
    {!!view.origins?.length&&<Alert type="info" message={`有 ${view.origins.filter(o=>o.origin==='AI_SUGGESTED').length} 项 AI 建议；发布前请复核`} description={view.origins.map(o=>`${o.column||'文档'}.${o.key}：${o.origin==='AI_SUGGESTED'?'AI 建议':'用户信息'}`).join('；')}/> }
    <Typography.Title level={5}>关联资源</Typography.Title>{bindings.map(b=><Typography.Paragraph key={bindingKey(b)}>{refsLabel(b)}</Typography.Paragraph>)}
    {view.object&&<><Typography.Title level={5}>待补充</Typography.Title><Typography.Paragraph>{activeColumns.filter(c=>!columns[c.name]?.description?.trim()).length} 个字段缺少描述</Typography.Paragraph><Typography.Paragraph>{activeColumns.filter(c=>!columns[c.name]?.unit?.trim()).length} 个字段未填写单位（按业务需要补充）</Typography.Paragraph></>}
    <Typography.Title level={5}>Agent 当前使用</Typography.Title><Typography.Paragraph>{view.page.disabled?'已停用':view.page.needsReview?'结构待复核，暂不提供':view.page.publishedRevision?`已发布 v${view.page.publishedRevision}`:'结构已可检索；业务说明待补充'}</Typography.Paragraph>
    {view.page.publishedRevision>0&&<Button danger size="small" disabled={busy||dirty} onClick={()=>Modal.confirm({title:view.page.disabled?'恢复当前发布内容？':'停用当前知识？',content:'历史版本保留，检索服务立即应用此状态。',onOk:async()=>{if(!ds)return;try{populate(await pageApi.disable(ds,view.page.id,!view.page.disabled));await refresh();}catch(e){fail(e);}}})}>{view.page.disabled?'恢复使用':'停用知识'}</Button>}
   </div></aside>}
  </div>}
  {mode==='page'&&view&&<footer className="wiki-footer"><Typography.Text type={dirty?'warning':'secondary'}>{dirty?'有未保存修改':view.page.type==='TABLE'&&!view.page.dirty&&!view.page.publishedRevision?'结构已同步 · 业务说明待补充':'草稿已保存'}{view.page.publishedRevision?` · 正式版本 v${view.page.publishedRevision}`:view.page.dirty?' · 可统一发布':''}</Typography.Text><Space><Button icon={<SaveOutlined/>} loading={busy} disabled={!dirty} onClick={save}>保存草稿</Button><Button type="primary" disabled={dirty||busy||(!view.page.dirty&&!view.page.needsReview&&!view.page.disabled)} onClick={()=>setPublishOpen(true)}>查看差异并发布</Button></Space></footer>}
  <Drawer zIndex={900} open={!!field} onClose={()=>setField(null)} width={560} title={field?`字段说明 · ${field.name}`:''}>
   {field&&<><Descriptions size="small" bordered column={1}><Descriptions.Item label="类型">{field.structure.typeName}</Descriptions.Item><Descriptions.Item label="原始备注">{field.structure.remarks||'无'}</Descriptions.Item><Descriptions.Item label="可空">{field.structure.nullable?'是':'否'}</Descriptions.Item><Descriptions.Item label="默认值">{field.structure.defaultValue??'无'}</Descriptions.Item></Descriptions><div style={{marginTop:20}}><SemanticForm value={columns[field.name]??{}} keys={['businessName','description','aliases','unit','enums','timeRule','notes']} onChange={v=>{setColumns(prev=>({...prev,[field.name]:v}));setDirty(true);}}/></div><Alert type="info" message="关闭抽屉后点击页面底部「保存草稿」，发布后 Agent 才可读取。"/></>}
  </Drawer>
  <Modal open={publishOpen} onCancel={()=>setPublishOpen(false)} width={1100} title="确认发布差异" okText="确认发布" onOk={publish} confirmLoading={busy}>
   <Alert type={view?.page.needsReview?'warning':'info'} showIcon message={view?.page.needsReview?'请确认新结构及所有业务口径仍然适用，发布即完成复核':'发布会生成不可变版本，草稿的表及字段说明一起生效'} style={{marginBottom:16}}/>
   {!bindings.length&&<Alert type="error" message="尚未关联资源，请先在文档编辑中选择依赖对象" style={{marginBottom:16}}/>}
   <div className="wiki-diff"><section><Typography.Title level={5}>当前正式版本 {view?.page.publishedRevision?`v${view.page.publishedRevision}`:''}</Typography.Title><ChatMarkdown content={view?.published?.content??'尚无正式版本'}/></section><section><Typography.Title level={5}>待发布草稿</Typography.Title><ChatMarkdown content={view?.preview??''}/></section></div>
  </Modal>
  <Modal open={syncOpen} onCancel={()=>!busy&&setSyncOpen(false)} title="同步数据库结构" okText="开始同步" onOk={performSync} confirmLoading={busy}>
   <Alert showIcon type="info" message="仅采集结构与原始备注，人工说明会保留。每次最多 200 张表。" style={{marginBottom:16}}/>
   <Form layout="vertical"><Form.Item label="库 / Schema"><Select value={namespace} options={namespaces.map(n=>({value:n,label:n}))} onChange={n=>{setNamespace(n);setTarget('');}}/></Form.Item><Form.Item label="采集范围"><Select value={target} options={[{value:'',label:'完整命名空间'},...tableOptions.map(t=>({value:t,label:t}))]} onChange={setTarget} showSearch/></Form.Item></Form>
  </Modal>
  <Modal open={createOpen} onCancel={()=>setCreateOpen(false)} title={scopeKind==='DATABASE'?'新建数据库知识文档':'新建数据集知识文档'} okText="创建草稿" onOk={async()=>{if(!ds||!rootSpace||loading)return;setBusy(true);try{const v=await docApi.create(ds,{type:createType,title:createTitle,content:TEMPLATES[createType],bindings:[]});await refresh();setCreateOpen(false);setCreateTitle('');openPage(v.page);}catch(e){fail(e);}finally{setBusy(false);}}} confirmLoading={busy}>
   <Alert type="info" showIcon message={`保存到：${documentScope}`} description={scopeKind==='DATABASE'?'文档归属当前数据库及所选文档空间。数据集可通过成员配置继承这些知识。':'跨库指标、关联与统一业务口径保存在本数据集，成员数据库文档仍归属各自数据库。'} style={{marginBottom:16}}/><Form layout="vertical"><Form.Item label="文档类型"><Select value={createType} onChange={setCreateType} options={Object.entries(TYPES).filter(([k])=>!['TABLE','STRUCTURE'].includes(k)).map(([value,label])=>({value,label}))}/></Form.Item><Form.Item label="标题"><Input value={createTitle} onChange={e=>setCreateTitle(e.target.value)} maxLength={255} placeholder="例如：净收入"/></Form.Item></Form>
  </Modal>
  <Drawer open={!!revision} onClose={()=>setRevision(null)} width={800} title={revision?`发布历史 · v${revision.revision}`:''}><Typography.Paragraph type="secondary">{revision?.publishedBy} · {revision&&new Date(revision.publishedAt).toLocaleString()}</Typography.Paragraph><ChatMarkdown content={revision?.content??''}/></Drawer>
  <Modal open={batchOpen} title={`统一确认发布（${batchViews.length} 项）`} width={950} onCancel={()=>setBatchOpen(false)} onOk={publishBatch} confirmLoading={busy} okText="发布全部选中草稿"><Alert type="warning" showIcon message="逐页版本校验，失败项保留草稿并显示原因；请核对业务口径和 AI 建议。"/><List dataSource={batchViews} renderItem={v=><List.Item><div style={{width:'100%'}}><Typography.Text strong>{v.page.title}</Typography.Text><Typography.Text type="secondary"> · 草稿 {v.page.draftVersion}{v.page.needsReview?' · 结构需复核':''}</Typography.Text><details><summary>查看当前正式内容与待发布内容</summary><div className="wiki-diff"><section><ChatMarkdown content={v.published?.content??'尚无业务发布版本'}/></section><section><ChatMarkdown content={v.preview}/></section></div></details></div></List.Item>}/></Modal>
  <Modal open={spacesOpen} title={`数据库知识配置 · ${scopeName}`} onCancel={()=>setSpacesOpen(false)} footer={null}><Alert type="info" showIcon message={`当前数据库：${scopeName} · 默认文档空间：${defaultDocumentSpace?.name??'加载中'}`} description="此处空间均归属当前数据库。选择空间可临时查看，点击「设为数据库默认」才会更新数据库配置；单库分析及继承默认值的数据集使用该默认空间。" style={{marginBottom:16}}/><Select style={{width:'100%',marginBottom:16}} value={spaceOverride??sourceSpaces?.defaultSpaceId} options={sourceSpaces?.spaces.map(s=>({value:s.id,label:`${s.name}${s.base?'（基础）':''}${s.id===sourceSpaces.defaultSpaceId?' · 默认':''}`}))} onChange={v=>navigate(()=>setSpaceOverride(v))}/><Button disabled={!spaceOverride||spaceOverride===sourceSpaces?.defaultSpaceId} onClick={()=>Modal.confirm({title:'修改数据库全局默认知识空间？',content:'继承默认知识的数据集也将使用新的文档空间；已有会话需重新开启。',onOk:async()=>{if(!ds||!spaceOverride)return;try{await wikiSpaceApi.setDefault(ds,spaceOverride);await refresh();message.success('默认知识空间已更新');}catch(e){fail(e);}}})}>设为数据库默认</Button><Space style={{marginTop:24}}><Input placeholder={`为当前数据库创建文档空间`} value={newSpaceName} onChange={e=>setNewSpaceName(e.target.value)}/><Button onClick={async()=>{if(!ds)return;try{await wikiSpaceApi.create(ds,newSpaceName);setNewSpaceName('');await refresh();}catch(e){fail(e);}}}>创建文档空间</Button></Space></Modal>
  <WikiKnowledgeChat open={aiOpen} spaceId={rootSpace??0} pageIds={mode==='page'&&pageId?[pageId]:selection}
   ready={!!rootSpace&&!loading&&(mode!=='page'||view?.page.id===pageId)} blocked={dirty||busy}
   context={{scope:(scopeKind==='DATASET'?datasets.find(x=>x.dataset.id===ds)?.dataset.name:sources.find(x=>x.id===ds)?.name)??'DataWiki',page:mode==='page'?(pages.find(x=>x.id===pageId)?.title??'加载页面中'):selection.length?`选中的 ${selection.length} 个知识页面`:({overview:'知识概览',tables:'库表知识',pending:'待发布知识',search:'知识搜索'} as Record<string,string>)[mode]??'知识文档',detail:mode==='page'?`${({business:'业务说明',fields:'字段说明',preview:'知识预览',versions:'版本历史'} as Record<string,string>)[tab]??tab}${field?` · 字段 ${field.name}`:''}`:'当前范围内的业务知识'}} onClose={()=>setAiOpen(false)} onChanged={()=>{const target=pageId;const currentEpoch=epoch.current;refresh().then(()=>{if(view&&target===view.page.id&&!activeEditor.current.dirty&&currentEpoch===epoch.current)pageApi.page(ds!,target).then(v=>{if(currentEpoch===epoch.current&&activeEditor.current.pageId===target&&!activeEditor.current.dirty)populate(v);}).catch(fail);}).catch(fail);}}/>
  <WikiReferenceDrawer reference={reference} onClose={()=>setReference(null)}/>
 </div>;
}
