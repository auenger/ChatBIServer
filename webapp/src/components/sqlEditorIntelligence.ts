import type { Monaco, OnMount } from '@monaco-editor/react';
import type { DatabaseType, TableDetail, TableInfo } from '@/service/api';

type MonacoEditor = Parameters<OnMount>[0];
type MonacoModel = NonNullable<ReturnType<MonacoEditor['getModel']>>;

export interface SqlMetadataContext {
  datasourceId: number | null;
  databaseType?: DatabaseType;
  namespace?: string;
  namespaces: string[];
  tables: TableInfo[];
}

export interface SqlEditorIssue {
  severity: 'error' | 'warning';
  message: string;
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
}

const COMMON_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'CREATE', 'TABLE', 'VIEW', 'INDEX', 'DROP', 'ALTER', 'TRUNCATE', 'JOIN', 'LEFT',
  'RIGHT', 'FULL', 'INNER', 'OUTER', 'CROSS', 'ON', 'GROUP', 'BY', 'ORDER', 'HAVING',
  'LIMIT', 'OFFSET', 'FETCH', 'AND', 'OR', 'NOT', 'NULL', 'IS', 'IN', 'LIKE', 'BETWEEN',
  'DISTINCT', 'UNION', 'ALL', 'AS', 'ASC', 'DESC', 'CASE', 'WHEN', 'THEN', 'ELSE',
  'END', 'WITH', 'EXISTS', 'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'DEFAULT',
  'COMMIT', 'ROLLBACK', 'BEGIN', 'EXPLAIN', 'GRANT', 'REVOKE', 'MERGE', 'USING',
];

const DIALECT_KEYWORDS: Partial<Record<DatabaseType, string[]>> = {
  MYSQL: ['SHOW', 'DESCRIBE', 'REPLACE', 'USE', 'AUTO_INCREMENT', 'ENGINE'],
  SQLSERVER: ['TOP', 'GO', 'IDENTITY', 'NVARCHAR', 'TRY', 'CATCH'],
  POSTGRESQL: ['RETURNING', 'ILIKE', 'SERIAL', 'MATERIALIZED', 'DO', 'LANGUAGE'],
  ORACLE: ['ROWNUM', 'CONNECT', 'START', 'PRIOR', 'MINUS', 'SEQUENCE', 'SYNONYM'],
};

const COMMON_FUNCTIONS = [
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'COALESCE', 'NULLIF', 'UPPER', 'LOWER',
  'TRIM', 'LENGTH', 'SUBSTRING', 'CONCAT', 'ROUND', 'CURRENT_DATE', 'CURRENT_TIMESTAMP',
];

const DIALECT_FUNCTIONS: Partial<Record<DatabaseType, string[]>> = {
  MYSQL: ['IFNULL', 'DATE_FORMAT', 'GROUP_CONCAT', 'NOW'],
  SQLSERVER: ['ISNULL', 'GETDATE', 'DATEADD', 'DATEDIFF', 'STRING_AGG'],
  POSTGRESQL: ['NOW', 'DATE_TRUNC', 'STRING_AGG', 'JSONB_BUILD_OBJECT'],
  ORACLE: ['NVL', 'SYSDATE', 'TO_CHAR', 'TO_DATE', 'LISTAGG'],
};

const SQL_SNIPPETS = [
  { label: 'select', detail: '查询表数据', insertText: 'SELECT ${1:*}\nFROM ${2:table_name}\nWHERE ${3:condition};' },
  { label: 'select-count', detail: '统计行数', insertText: 'SELECT COUNT(${1:*}) AS ${2:total}\nFROM ${3:table_name};' },
  { label: 'insert', detail: '插入数据', insertText: 'INSERT INTO ${1:table_name} (${2:columns})\nVALUES (${3:values});' },
  { label: 'update', detail: '更新数据', insertText: 'UPDATE ${1:table_name}\nSET ${2:column} = ${3:value}\nWHERE ${4:condition};' },
  { label: 'delete', detail: '删除数据', insertText: 'DELETE FROM ${1:table_name}\nWHERE ${2:condition};' },
  { label: 'cte', detail: '公共表表达式', insertText: 'WITH ${1:cte_name} AS (\n  ${2:SELECT * FROM table_name}\n)\nSELECT *\nFROM ${1:cte_name};' },
];

const ALIAS_STOP_WORDS = new Set([
  'where', 'join', 'left', 'right', 'full', 'inner', 'outer', 'cross', 'on', 'group',
  'order', 'having', 'limit', 'offset', 'fetch', 'set', 'values', 'returning', 'union',
]);

function unquoteIdentifier(value: string) {
  return value.replace(/^[`"\[]|[`"\]]$/g, '');
}

function quoteIdentifierIfNeeded(value: string, type?: DatabaseType) {
  if (/^[A-Za-z_][\w$]*$/.test(value)) return value;
  if (type === 'MYSQL') return `\`${value.split('`').join('``')}\``;
  if (type === 'SQLSERVER') return `[${value.split(']').join(']]')}]`;
  return `"${value.split('"').join('""')}"`;
}

function currentStatement(model: MonacoModel, position: { lineNumber: number; column: number }) {
  const before = model.getValueInRange({
    startLineNumber: 1,
    startColumn: 1,
    endLineNumber: position.lineNumber,
    endColumn: position.column,
  });
  const after = model.getValueInRange({
    startLineNumber: position.lineNumber,
    startColumn: position.column,
    endLineNumber: model.getLineCount(),
    endColumn: model.getLineMaxColumn(model.getLineCount()),
  });
  return `${before.split(';').pop() ?? ''}${after.split(';')[0] ?? ''}`;
}

function extractRelations(sql: string) {
  const relationPattern = /\b(?:from|join|update|into)\s+((?:(?:`[^`]+`|"[^"]+"|\[[^\]]+\]|[\w$]+)\.)?(?:`[^`]+`|"[^"]+"|\[[^\]]+\]|[\w$]+))(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?/gi;
  const relations: Array<{ namespace?: string; table: string; alias?: string }> = [];
  for (const match of sql.matchAll(relationPattern)) {
    const parts = match[1].split('.').map(unquoteIdentifier);
    const possibleAlias = match[2]?.toLowerCase();
    relations.push({
      namespace: parts.length > 1 ? parts[parts.length - 2] : undefined,
      table: parts[parts.length - 1] ?? '',
      alias: possibleAlias && !ALIAS_STOP_WORDS.has(possibleAlias) ? match[2] : undefined,
    });
  }
  return relations;
}

function uniqueSuggestions<T extends { label: string | { label: string } }>(items: T[]) {
  const seen = new Set<string>();
  return items.filter((item) => {
    const label = typeof item.label === 'string' ? item.label : item.label.label;
    if (seen.has(label)) return false;
    seen.add(label);
    return true;
  });
}

export function registerSqlCompletionProvider(options: {
  monaco: Monaco;
  editor: MonacoEditor;
  getContext: () => SqlMetadataContext;
  loadTableDetail: (namespace: string, table: string) => Promise<TableDetail | null>;
}) {
  const { monaco, editor, getContext, loadTableDetail } = options;
  return monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.', ' ', '(', ','],
    provideCompletionItems: async (model, position) => {
      if (model !== editor.getModel()) return { suggestions: [] };
      const context = getContext();
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endLineNumber: position.lineNumber,
        endColumn: word.endColumn,
      };
      const statement = currentStatement(model, position);
      const linePrefix = model.getLineContent(position.lineNumber).slice(0, position.column - 1);
      const qualifier = linePrefix.match(/([A-Za-z_][\w$]*)\.[\w$]*$/)?.[1];
      const relations = extractRelations(statement);
      const namespace = context.namespace ?? context.namespaces[0];
      const suggestions: Array<{
        label: string | { label: string; detail?: string; description?: string };
        kind: number;
        insertText: string;
        insertTextRules?: number;
        sortText: string;
        range: typeof range;
        detail?: string;
        documentation?: string;
      }> = [];

      const pushColumns = async (relation: { namespace?: string; table: string }) => {
        const relationNamespace = relation.namespace ?? namespace;
        if (!relationNamespace || !relation.table) return;
        const detail = await loadTableDetail(relationNamespace, relation.table);
        detail?.columns.forEach((column) => suggestions.push({
          label: { label: column.name, detail: column.typeName, description: relation.table },
          kind: monaco.languages.CompletionItemKind.Field,
          insertText: quoteIdentifierIfNeeded(column.name, context.databaseType),
          sortText: `01_${column.name}`,
          range,
          detail: `${relation.table}.${column.name}`,
          documentation: column.remarks ?? undefined,
        }));
      };

      if (qualifier) {
        if (context.namespaces.some((item) => item.toLowerCase() === qualifier.toLowerCase())) {
          context.tables.forEach((table) => suggestions.push({
            label: { label: table.name, description: table.type === 'VIEW' ? '视图' : '表' },
            kind: table.type === 'VIEW'
              ? monaco.languages.CompletionItemKind.Interface
              : monaco.languages.CompletionItemKind.Class,
            insertText: quoteIdentifierIfNeeded(table.name, context.databaseType),
            sortText: `00_${table.name}`,
            range,
            documentation: table.remarks ?? undefined,
          }));
        } else {
          const relation = relations.find((item) =>
            item.alias?.toLowerCase() === qualifier.toLowerCase()
            || item.table.toLowerCase() === qualifier.toLowerCase());
          if (relation) await pushColumns(relation);
        }
        return { suggestions: uniqueSuggestions(suggestions), incomplete: false };
      }

      context.namespaces.forEach((item) => suggestions.push({
        label: { label: item, description: '命名空间' },
        kind: monaco.languages.CompletionItemKind.Module,
        insertText: `${quoteIdentifierIfNeeded(item, context.databaseType)}.`,
        sortText: `02_${item}`,
        range,
      }));
      context.tables.forEach((table) => suggestions.push({
        label: { label: table.name, description: table.type === 'VIEW' ? '视图' : '表' },
        kind: table.type === 'VIEW'
          ? monaco.languages.CompletionItemKind.Interface
          : monaco.languages.CompletionItemKind.Class,
        insertText: quoteIdentifierIfNeeded(table.name, context.databaseType),
        sortText: `03_${table.name}`,
        range,
        documentation: table.remarks ?? undefined,
      }));

      await Promise.all(relations.slice(0, 3).map(pushColumns));

      const functions = [...COMMON_FUNCTIONS, ...(context.databaseType ? DIALECT_FUNCTIONS[context.databaseType] ?? [] : [])];
      functions.forEach((name) => suggestions.push({
        label: { label: name, description: '函数' },
        kind: monaco.languages.CompletionItemKind.Function,
        insertText: `${name}(${name === 'COUNT' ? '${1:*}' : '${1:}'})`,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        sortText: `05_${name}`,
        range,
      }));
      [...COMMON_KEYWORDS, ...(context.databaseType ? DIALECT_KEYWORDS[context.databaseType] ?? [] : [])]
        .forEach((keyword) => suggestions.push({
          label: { label: keyword, description: '关键字' },
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          sortText: `06_${keyword}`,
          range,
        }));
      SQL_SNIPPETS.forEach((snippet) => suggestions.push({
        label: { label: snippet.label, description: 'SQL 模板' },
        kind: monaco.languages.CompletionItemKind.Snippet,
        insertText: snippet.insertText,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        sortText: `04_${snippet.label}`,
        range,
        detail: snippet.detail,
      }));
      return { suggestions: uniqueSuggestions(suggestions), incomplete: false };
    },
  });
}

function offsetPosition(sql: string, offset: number) {
  const before = sql.slice(0, offset);
  const lines = before.split('\n');
  return { line: lines.length, column: (lines[lines.length - 1]?.length ?? 0) + 1 };
}

export function inspectSql(sql: string): SqlEditorIssue[] {
  const issues: SqlEditorIssue[] = [];
  const parentheses: number[] = [];
  const cleaned = sql.split('');
  let state: 'normal' | 'single' | 'double' | 'backtick' | 'lineComment' | 'blockComment' = 'normal';
  let stateStart = 0;

  for (let index = 0; index < sql.length; index += 1) {
    const char = sql[index];
    const next = sql[index + 1];
    if (state === 'lineComment') {
      if (char === '\n') state = 'normal'; else cleaned[index] = ' ';
      continue;
    }
    if (state === 'blockComment') {
      if (char === '*' && next === '/') {
        cleaned[index] = ' ';
        cleaned[index + 1] = ' ';
        index += 1;
        state = 'normal';
      } else if (char !== '\n') cleaned[index] = ' ';
      continue;
    }
    if (state !== 'normal') {
      if (char !== '\n') cleaned[index] = ' ';
      const quote = state === 'single' ? '\'' : state === 'double' ? '"' : '`';
      if (char === quote) {
        if (next === quote) {
          cleaned[index + 1] = ' ';
          index += 1;
        } else if (sql[index - 1] !== '\\') {
          state = 'normal';
        }
      }
      continue;
    }
    if (char === '-' && next === '-') {
      cleaned[index] = ' ';
      cleaned[index + 1] = ' ';
      index += 1;
      state = 'lineComment';
    } else if (char === '/' && next === '*') {
      stateStart = index;
      cleaned[index] = ' ';
      cleaned[index + 1] = ' ';
      index += 1;
      state = 'blockComment';
    } else if (char === '\'' || char === '"' || char === '`') {
      stateStart = index;
      state = char === '\'' ? 'single' : char === '"' ? 'double' : 'backtick';
      cleaned[index] = ' ';
    } else if (char === '(') {
      parentheses.push(index);
    } else if (char === ')') {
      const opening = parentheses.pop();
      if (opening === undefined) {
        const pos = offsetPosition(sql, index);
        issues.push({
          severity: 'error', message: '缺少与此处匹配的左括号',
          startLineNumber: pos.line, startColumn: pos.column,
          endLineNumber: pos.line, endColumn: pos.column + 1,
        });
      }
    }
  }

  if (state === 'single' || state === 'double' || state === 'backtick' || state === 'blockComment') {
    const pos = offsetPosition(sql, stateStart);
    issues.push({
      severity: 'error',
      message: state === 'blockComment' ? '块注释未结束' : '字符串或标识符引用未结束',
      startLineNumber: pos.line, startColumn: pos.column,
      endLineNumber: pos.line, endColumn: pos.column + 1,
    });
  }
  parentheses.forEach((offset) => {
    const pos = offsetPosition(sql, offset);
    issues.push({
      severity: 'error', message: '左括号未闭合',
      startLineNumber: pos.line, startColumn: pos.column,
      endLineNumber: pos.line, endColumn: pos.column + 1,
    });
  });

  const safeSql = cleaned.join('');
  for (const match of safeSql.matchAll(/(?:^|;)\s*(UPDATE|DELETE\s+FROM)\b[^;]*/gi)) {
    if (/\bWHERE\b/i.test(match[0])) continue;
    const keywordOffset = (match.index ?? 0) + match[0].search(/\b(?:UPDATE|DELETE)\b/i);
    const pos = offsetPosition(sql, keywordOffset);
    issues.push({
      severity: 'warning', message: '该语句没有 WHERE 条件，请确认是否需要影响全部数据',
      startLineNumber: pos.line, startColumn: pos.column,
      endLineNumber: pos.line, endColumn: pos.column + (match[1].toUpperCase().startsWith('DELETE') ? 6 : 6),
    });
  }
  return issues;
}

export function setSqlMarkers(monaco: Monaco, model: MonacoModel, issues: SqlEditorIssue[]) {
  monaco.editor.setModelMarkers(model, 'meper-sql-check', issues.map((issue) => ({
    ...issue,
    severity: issue.severity === 'error'
      ? monaco.MarkerSeverity.Error
      : monaco.MarkerSeverity.Warning,
  })));
}
