import type { DatabaseType, SslMode } from '@/service/api';

/**
 * 数据库类型的字段配置声明（借鉴 Chat2DB ConnectionEdit「配置驱动表单」模式：
 * 每库一份声明，表单按声明渲染；阶段 1 简化为统一基础字段 + 类型默认值 + URL 预览）。
 */

export interface DbTypeInfo {
  type: DatabaseType;
  label: string;
  defaultPort: number;
  /** databaseName 字段的展示名 */
  databaseLabel: string;
  databasePlaceholder: string;
}

export const DATABASE_TYPES: DbTypeInfo[] = [
  { type: 'MYSQL', label: 'MySQL', defaultPort: 3306, databaseLabel: '数据库名', databasePlaceholder: '如 meperdb' },
  { type: 'SQLSERVER', label: 'SQL Server', defaultPort: 1433, databaseLabel: '数据库名', databasePlaceholder: '如 meper' },
  { type: 'POSTGRESQL', label: 'PostgreSQL', defaultPort: 5432, databaseLabel: '数据库名', databasePlaceholder: '如 meperdb' },
  { type: 'ORACLE', label: 'Oracle', defaultPort: 1521, databaseLabel: '服务名 (service)', databasePlaceholder: '如 FREEPDB1' },
];

export function typeInfo(type: DatabaseType): DbTypeInfo {
  return DATABASE_TYPES.find((t) => t.type === type) ?? DATABASE_TYPES[0];
}

export const SSL_MODES: { value: SslMode; label: string }[] = [
  { value: 'DISABLED', label: '禁用 TLS' },
  { value: 'PREFERRED', label: '优先 TLS（尽力）' },
  { value: 'REQUIRED', label: '强制 TLS' },
];

/** JDBC URL 预览（与后端方言层同样的拼装规则；阶段 1 单向展示，反解解析后续迭代）。 */
export function buildJdbcUrlPreview(
  type: DatabaseType,
  host: string,
  port: number,
  databaseName: string,
  sslMode: SslMode,
): string {
  const db = databaseName || '<库名>';
  switch (type) {
    case 'MYSQL':
      return `jdbc:mysql://${host || '<host>'}:${port}/${db}?sslMode=${sslMode}${sslMode !== 'REQUIRED' ? '&allowPublicKeyRetrieval=true' : ''}&characterEncoding=utf8`;
    case 'SQLSERVER':
      return sslMode === 'DISABLED'
        ? `jdbc:sqlserver://${host || '<host>'}:${port};encrypt=false;databaseName=${db}`
        : `jdbc:sqlserver://${host || '<host>'}:${port};encrypt=true;trustServerCertificate=true;databaseName=${db}`;
    case 'POSTGRESQL':
      return `jdbc:postgresql://${host || '<host>'}:${port}/${db}?sslmode=${sslMode === 'DISABLED' ? 'disable' : sslMode === 'REQUIRED' ? 'require' : 'prefer'}`;
    case 'ORACLE':
      return `jdbc:oracle:thin:@//${host || '<host>'}:${port}/${db}`;
  }
}
