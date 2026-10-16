import { get, post, put } from './base';
export interface Resource { namespace:string; table:string }
export interface Member { id:number; datasourceId:number; alias:string; resources:Resource[]; wikiSpaceId:number|null }
export interface Dataset { id:number; name:string; description:string; revision:number; enabled:boolean; updatedBy:string; updatedAt:string }
export interface DatasetView { dataset:Dataset; members:Member[]; wikiSpaceId:number }
export interface WikiSpace { id:number; name:string; datasourceId:number|null; datasetId:number|null; base:boolean }
export interface SourceSpaces { spaces:WikiSpace[]; defaultSpaceId:number; baseSpaceId:number }
export interface ChatScope { scopeType:'DATABASE'|'DATASET'; scopeId:number; scopeVersion:string; providerId:number; namespace:string|null; mode:string; spaceId:number|null }
export const datasetApi={
 list:()=>get<DatasetView[]>('/datasets'),
 get:(id:number)=>get<DatasetView>(`/datasets/${id}`),
 create:(body:unknown)=>post<DatasetView>('/datasets',body),
 update:(id:number,body:unknown)=>put<DatasetView>(`/datasets/${id}`,body),
 scope:(id:number)=>get<{version:string}>(`/datasets/${id}/scope`),
};
export const wikiSpaceApi={
 source:(ds:number)=>get<SourceSpaces>(`/datasources/${ds}/wiki/spaces`),
 create:(ds:number,name:string)=>post<WikiSpace>(`/datasources/${ds}/wiki/spaces`,{name}),
 setDefault:(ds:number,spaceId:number)=>post(`/datasources/${ds}/wiki/default-space`,{spaceId}),
};
export const modelDefaultApi={set:(id:number)=>post(`/model-providers/${id}/default`)};
export const chatScopeApi={get:(id:string)=>get<ChatScope>(`/agent/chat/sessions/${encodeURIComponent(id)}/context`)};
