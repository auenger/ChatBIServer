import { get, post, put, del } from './base';

// ---------- 类型（与后端 API 契约一一对应） ----------

export type DatabaseType = 'MYSQL' | 'SQLSERVER' | 'POSTGRESQL' | 'ORACLE';
export type SslMode = 'PREFERRED' | 'REQUIRED' | 'DISABLED';
export type SqlCategory = 'SELECT' | 'DML' | 'DDL' | 'TCL' | 'OTHER';

export interface DataSourceProfile {
  id: number;
  name: string;
  type: DatabaseType;
  host: string;
  port: number;
  databaseName: string | null;
  username: string;
  sslMode: SslMode;
  extendInfo: Record<string, string>;
  credentialVersion: number;
}

export interface CapabilityDescriptor {
  supportsDatabase: boolean;
  supportsSchema: boolean;
  identifierQuote: string;
  paginationStyle: string;
  probeSql: string;
}

export interface TestResult {
  success: boolean;
  message: string;
  latencyMs: number;
}

export interface AnalyzedStatement {
  seq: number;
  sql: string;
  category: SqlCategory;
}

export interface PreviewResult {
  datasourceId: number;
  datasourceName: string;
  enforcement: string;
  statements: AnalyzedStatement[];
}

export interface QueryResultData {
  columns: string[];
  rows: (string | null)[][];
  columnsTruncated: boolean;
  rowsTruncated: boolean;
}

export interface StatementResult {
  seq: number;
  sql: string;
  category: SqlCategory;
  query: boolean;
  resultData: QueryResultData | null;
  updateCount: number | null;
  durationMs: number;
  error: string | null;
}

export interface ExecuteResult {
  executionId: number;
  enforcement: string;
  status: 'SUCCESS' | 'FAILED' | 'PARTIAL';
  statements: StatementResult[];
  durationMs: number;
}

export interface ExecutionRecord {
  id: number;
  datasourceId: number;
  subject: string;
  purpose: string;
  correlationId: string;
  sqlText: string;
  statementCount: number;
  status: 'SUCCESS' | 'FAILED' | 'PARTIAL';
  error: string | null;
  enforcementState: string;
  startedAt: string;
  finishedAt: string;
}

// ---------- API ----------

export interface DatasourcePayload {
  name: string;
  type: DatabaseType;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  sslMode: SslMode;
  extendInfo?: Record<string, string>;
}

export const authApi = {
  login: (username: string, password: string) =>
    post<{ token: string; username: string; role: string }>('/auth/login', { username, password }),
  logout: () => post('/auth/logout'),
  me: () => get<{ username: string; role: string; enforcementState: string }>('/auth/me'),
};

export const datasourceApi = {
  list: () => get<DataSourceProfile[]>('/datasources'),
  get: (id: number) => get<DataSourceProfile>(`/datasources/${id}`),
  create: (payload: DatasourcePayload) => post<DataSourceProfile>('/datasources', payload),
  testTemporary: (payload: DatasourcePayload) => post<TestResult>('/datasources/test', payload),
  testStored: (id: number) => post<TestResult>(`/datasources/${id}/test`),
  capabilities: (id: number) => get<CapabilityDescriptor>(`/datasources/${id}/capabilities`),
  rotate: (id: number, password: string) =>
    post<DataSourceProfile>(`/datasources/${id}/rotate-credential`, { password }),
  remove: (id: number) => del<{ deleted: boolean }>(`/datasources/${id}`),
};

export const workbenchApi = {
  preview: (datasourceId: number, sql: string) =>
    post<PreviewResult>('/workbench/preview', { datasourceId, sql }),
  format: (datasourceId: number, sql: string) =>
    post<{ sql: string }>('/workbench/format', { datasourceId, sql }),
  execute: (datasourceId: number, sql: string, maxRows?: number) =>
    post<ExecuteResult>('/workbench/execute', { datasourceId, sql, maxRows }),
  execution: (id: number) => get<ExecutionRecord>(`/workbench/executions/${id}`),
  executions: (datasourceId?: number) =>
    get<ExecutionRecord[]>(`/workbench/executions${datasourceId ? `?datasourceId=${datasourceId}` : ''}`),
};

// ---------- 元数据（库表树 / 表结构 / 表数据） ----------

export interface TableInfo {
  name: string;
  type: 'TABLE' | 'VIEW';
  remarks: string | null;
}

export interface ColumnInfo {
  name: string;
  typeName: string;
  nullable: boolean;
  defaultValue: string | null;
  remarks: string | null;
  primaryKey: boolean;
  autoIncrement: boolean;
}

export interface IndexInfo {
  name: string;
  unique: boolean;
  columns: string[];
}

export interface TableDetail {
  table: string;
  type: 'TABLE' | 'VIEW';
  remarks: string | null;
  columns: ColumnInfo[];
  indexes: IndexInfo[];
}

export interface TableDataPage {
  data: QueryResultData;
  total: number;
  page: number;
  size: number;
}

export const metadataApi = {
  namespaces: (id: number) => get<string[]>(`/datasources/${id}/metadata/namespaces`),
  tables: (id: number, namespace: string, pattern?: string) =>
    get<TableInfo[]>(
      `/datasources/${id}/metadata/tables?namespace=${encodeURIComponent(namespace)}` +
      (pattern ? `&pattern=${encodeURIComponent(pattern)}` : ''),
    ),
  tableDetail: (id: number, namespace: string, table: string) =>
    get<TableDetail>(
      `/datasources/${id}/metadata/table?namespace=${encodeURIComponent(namespace)}&table=${encodeURIComponent(table)}`,
    ),
  tableData: (id: number, namespace: string, table: string, page: number, size: number) =>
    get<TableDataPage>(
      `/datasources/${id}/metadata/data?namespace=${encodeURIComponent(namespace)}` +
      `&table=${encodeURIComponent(table)}&page=${page}&size=${size}`,
    ),
};

// ---------- 模型 provider 配置（0.1） ----------

export type ModelProtocol = 'OPENAI_COMPATIBLE' | 'ANTHROPIC';

export interface ModelProviderView {
  isDefault: boolean;
  id: number;
  name: string;
  protocol: ModelProtocol;
  baseUrl: string;
  model: string;
  params: Record<string, unknown>;
  enabled: boolean;
}

export interface ModelProviderSave {
  name: string;
  protocol: ModelProtocol;
  baseUrl: string;
  model: string;
  apiKey?: string;
  params?: Record<string, unknown>;
  enabled?: boolean;
}

export const modelProviderApi = {
  list: () => get<ModelProviderView[]>('/model-providers'),
  create: (body: ModelProviderSave) => post<{ id: number }>('/model-providers', body),
  update: (id: number, body: ModelProviderSave) => put<void>(`/model-providers/${id}`, body),
  remove: (id: number) => del<void>(`/model-providers/${id}`),
  test: (id: number) => post<TestResult>(`/model-providers/${id}/test`),
};

export const modelProviderApiExtra = {
  previewModels: (body: { protocol: ModelProtocol; baseUrl: string; apiKey?: string; providerId?: number }) =>
    post<{ models: string[] }>('/model-providers/preview-models', body),
};

// ---------- Agent chat 会话历史（0.1 持久化） ----------

export interface ChatSessionSummary {
  sessionId: string;
  title: string;
  messageCount: number;
  lastAt: string;
}

export interface ChatMessageView {
  seq: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string | null;
}

export const chatApi = {
  sessions: () => get<ChatSessionSummary[]>('/agent/chat/sessions'),
  messages: (sessionId: string, beforeSeq?: number, limit = 20) =>
    get<ChatMessageView[]>(
      `/agent/chat/sessions/${sessionId}/messages?limit=${limit}` +
      (beforeSeq !== undefined ? `&beforeSeq=${beforeSeq}` : ''),
    ),
};

export interface ChatMessageView {
  seq: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
}

// ChatMessageView 追加思维链与工具调用字段（覆盖上方旧定义）
export interface ToolCallView {
  request: string;
  result: string | null;
}

export interface ChatMessageView {
  seq: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string | null;
  reasoning: string | null;
  tools: ToolCallView[] | null;
  promptTokens: number | null;
  completionTokens: number | null;
}

// chatApi 补充删除
export const chatApiExtra = {
  deleteSession: (sessionId: string) => del<void>(`/agent/chat/sessions/${sessionId}`),
};
