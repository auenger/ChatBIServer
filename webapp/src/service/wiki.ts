import { datasetApi } from './datasets';
import { get, post, put } from './base';
import type { ColumnInfo } from './api';
export type Semantics = Record<string,string>;
export interface WikiObject { object: { id:number; datasourceId:number; namespace:string; name:string; type:string; remarks:string|null; state:string; syncedAt:string }; semantics:Semantics; columns:WikiColumn[] }
export interface WikiColumn { name:string; ordinal:number; structure:ColumnInfo; semantics:Semantics; state:string }
export interface WikiPage { id:number; datasourceId:number; spaceId:number; objectId:number|null; type:string; path:string; title:string; draftContent:string; draftVersion:number; publishedRevision:number; dirty:boolean; needsReview:boolean; disabled:boolean; updatedBy:string; updatedAt:string }
export interface Binding { objectId:number; column:string }
export interface Revision { pageId:number; revision:number; title:string; content:string; publishedBy:string; publishedAt:string }
export interface PageView { page:WikiPage; object:WikiObject|null; bindings:Binding[]; preview:string; published:Revision|null; origins:{column:string;key:string;origin:string;evidence:string|null}[] }
export interface Hit { pageId:number; path:string; title:string; type:string; revision:number; line:number; excerpt:string; spaceId:number; datasourceId:number }
export interface SearchResult { hits:Hit[]; nextCursor:number|null; truncated:boolean }
export interface SyncRun { id:number; namespace:string; table:string|null; status:string; detail:string; createdBy:string; startedAt:string; finishedAt:string|null }
export interface WikiReference { datasourceId:number; pageId:number; path:string; title:string; revision:number; startLine:number; spaceId?:number; scopeType?:string; scopeId?:number; scopeVersion?:string }
export interface ReadResult { pageId:number; title:string; revision:number; path:string; startLine:number; totalLines:number; content:string; truncated:boolean }
const makeWikiApi=(root:(ds:number)=>string)=>({
 pages:(ds:number)=>get<WikiPage[]>(root(ds)+'/pages'),
 objects:(ds:number)=>get<WikiObject[]>(root(ds)+'/objects'),
 page:(ds:number,id:number)=>get<PageView>(root(ds)+`/pages/${id}`),
 save:(ds:number,id:number,body:unknown)=>put<PageView>(root(ds)+`/pages/${id}`,body),
 create:(ds:number,body:unknown)=>post<PageView>(root(ds)+'/pages',body),
 publish:(ds:number,id:number,expectedVersion:number)=>post<PageView>(root(ds)+`/pages/${id}/publish`,{expectedVersion}),
 disable:(ds:number,id:number,disabled:boolean)=>post<PageView>(root(ds)+`/pages/${id}/disable`,{disabled}),
 revisions:(ds:number,id:number)=>get<Revision[]>(root(ds)+`/pages/${id}/revisions`),
 restore:(ds:number,id:number,revision:number,expectedVersion:number)=>post<PageView>(root(ds)+`/pages/${id}/restore`,{revision,expectedVersion}),
 sync:(ds:number,namespace:string,table?:string)=>post<{added:number;changed:number;missing:number;total:number}>(root(ds)+'/sync',{namespace,table}),
 runs:(ds:number)=>get<SyncRun[]>(root(ds)+'/sync-runs'),
 search:(ds:number,query:string,pattern='/**',cursor=0)=>get<SearchResult>(root(ds)+`/search?query=${encodeURIComponent(query)}&pattern=${encodeURIComponent(pattern)}&cursor=${cursor}`),
 batches:(ds:number)=>get<{batchId:string;occurredAt:string;detail:string}[]>(root(ds)+'/publish-batches'),
 batch:(ds:number,items:{pageId:number;expectedVersion:number}[])=>post<{batchId:string;outcomes:{pageId:number;success:boolean;message:string}[]}>(root(ds)+'/publish-batch',items),
});
export const wikiApi={...makeWikiApi(ds=>`/datasources/${ds}/wiki`),
 inSpace:(space:number)=>makeWikiApi(()=>`/wiki-spaces/${space}`),
 inDataset:(dataset:number)=>({...makeWikiApi(()=>`/datasets/${dataset}/wiki`),
   create:(_:number,body:unknown)=>datasetApi.get(dataset).then(d=>makeWikiApi(()=>`/wiki-spaces/${d.wikiSpaceId}`).create(dataset,body)),
   runs:(_:number)=>Promise.resolve([] as SyncRun[]),
 }),
 read:(ref:WikiReference,start=ref.startLine)=>{
  const query=`path=${encodeURIComponent(ref.path)}&revision=${ref.revision}&start=${start}&count=100`;
  if(ref.scopeType&&ref.scopeId&&ref.scopeVersion)return get<ReadResult>(`/agent/chat/knowledge/reference?${query}&scopeType=${ref.scopeType}&scopeId=${ref.scopeId}&scopeVersion=${ref.scopeVersion}&pageId=${ref.pageId}`);
  return get<ReadResult>((ref.spaceId?`/wiki-spaces/${ref.spaceId}`:`/datasources/${ref.datasourceId}/wiki`)+`/read?${query}`);
 },
};
