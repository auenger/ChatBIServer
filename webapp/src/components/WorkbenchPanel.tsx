import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Drawer, InputNumber, message, Select, Space, Spin, Tabs, Tag, Timeline, Tooltip, Typography,
} from 'antd';
import {
  CaretRightOutlined, CheckCircleOutlined, CodeOutlined, FormatPainterOutlined, HistoryOutlined,
} from '@ant-design/icons';
import { createStyles } from 'antd-style';
import Editor, { type Monaco, type OnMount } from '@monaco-editor/react';
import ResultSet from '@/components/ResultSet';
import {
  inspectSql, registerSqlCompletionProvider, setSqlMarkers,
  type SqlEditorIssue, type SqlMetadataContext,
} from '@/components/sqlEditorIntelligence';
import { useWorkspaceModel } from '@/models/workspace';
import type { DataSourceProfile, SqlCategory, TableDetail, TableInfo } from '@/service/api';
import { datasourceApi, metadataApi, workbenchApi } from '@/service/api';

// SQL 工作台：执行走「自动 preview → 确认 → execute」两步（防并发：确认前不可重复触发）
const useStyles = createStyles(({ token }) => ({
  root: {
    display: 'flex',
    minHeight: 0,
    height: '100%',
    flexDirection: 'column',
    background: token.colorBgContainer,
  },
  toolbar: {
    display: 'flex',
    flexShrink: 0,
    minHeight: 40,
    gap: 8,
    alignItems: 'center',
    padding: '4px 8px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  editorBox: {
    flexShrink: 0,
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
    overflow: 'hidden',
  },
  actionBar: {
    display: 'flex',
    flexShrink: 0,
    minHeight: 38,
    alignItems: 'center',
    padding: '3px 8px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  output: {
    display: 'flex',
    minHeight: 0,
    flex: 1,
    flexDirection: 'column',
    overflow: 'hidden',
    '& .ant-tabs': { height: '100%' },
    '& .ant-tabs-nav': { flexShrink: 0, margin: 0, minHeight: 32, paddingInline: 8 },
    '& .ant-tabs-content-holder, & .ant-tabs-content, & .ant-tabs-tabpane': { height: '100%', minHeight: 0 },
  },
}));

const CATEGORY_COLORS: Record<SqlCategory, string> = {
  SELECT: 'green',
  DML: 'orange',
  DDL: 'red',
  TCL: 'purple',
  OTHER: 'default',
};

/**
 * SQL 工作台面板（可嵌入 workspace 内容区，也可由路由页薄壳承载）。
 * initialDatasourceId / initialSql 由「库树上新建查询」等入口传入。
 */
interface WorkbenchPanelProps {
  sessionKey?: string;
  initialDatasourceId?: number;
  initialNamespace?: string;
  initialSql?: string;
}

const STANDALONE_SESSION_KEY = 'standalone-workbench';

export default function WorkbenchPanel({
  sessionKey, initialDatasourceId, initialNamespace, initialSql,
}: WorkbenchPanelProps = {}) {
  const { styles } = useStyles();
  const resolvedSessionKey = sessionKey ?? STANDALONE_SESSION_KEY;
  const [profiles, setProfiles] = useState<DataSourceProfile[]>([]);
  const [namespaces, setNamespaces] = useState<string[]>([]);
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [issues, setIssues] = useState<SqlEditorIssue[]>([]);
  const [checking, setChecking] = useState(false);
  const [formatting, setFormatting] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null);
  const monacoRef = useRef<Monaco | null>(null);
  const runPreviewRef = useRef<() => void>(() => undefined);
  const formatSqlRef = useRef<() => void>(() => undefined);
  const completionProviderRef = useRef<{ dispose: () => void } | null>(null);
  const changeListenerRef = useRef<{ dispose: () => void } | null>(null);
  const markerTimerRef = useRef<number>();
  const tableDetailCacheRef = useRef(new Map<string, Promise<TableDetail | null>>());
  const datasourceIdRef = useRef<number | null>(null);
  const metadataContextRef = useRef<SqlMetadataContext>({
    datasourceId: null, namespaces: [], tables: [],
  });
  const session = useWorkspaceModel((state) => state.querySessions[resolvedSessionKey]);
  const ensureQuerySession = useWorkspaceModel((state) => state.ensureQuerySession);
  const updateQuerySession = useWorkspaceModel((state) => state.updateQuerySession);
  const datasourceId = session?.datasourceId ?? initialDatasourceId ?? null;
  const namespace = session?.namespace ?? initialNamespace ?? null;
  const sql = session?.sql ?? initialSql ?? 'SELECT 1';
  const maxRows = session?.maxRows ?? 1000;
  const pendingSql = session?.pendingSql ?? null;
  const preview = session?.preview ?? null;
  const confirming = session?.confirming ?? false;
  const executing = session?.executing ?? false;
  const result = session?.result ?? null;
  datasourceIdRef.current = datasourceId;

  useEffect(() => {
    ensureQuerySession(resolvedSessionKey, initialDatasourceId, initialSql);
    const current = useWorkspaceModel.getState().querySessions[resolvedSessionKey];
    if (initialNamespace && !current?.namespace) {
      updateQuerySession(resolvedSessionKey, { namespace: initialNamespace });
    }
  }, [ensureQuerySession, initialDatasourceId, initialNamespace, initialSql, resolvedSessionKey, updateQuerySession]);

  useEffect(() => {
    datasourceApi.list().then((list) => {
      setProfiles(list);
      const current = useWorkspaceModel.getState().querySessions[resolvedSessionKey]?.datasourceId;
      const preferred = current ?? initialDatasourceId ?? list[0]?.id;
      if (preferred && list.some((profile) => profile.id === preferred)) {
        updateQuerySession(resolvedSessionKey, { datasourceId: preferred });
      } else {
        updateQuerySession(resolvedSessionKey, { datasourceId: list[0]?.id ?? null });
      }
    }).catch((e) => message.error((e as Error).message));
  }, [initialDatasourceId, resolvedSessionKey, updateQuerySession]);

  useEffect(() => {
    if (!datasourceId) {
      setNamespaces([]);
      setTables([]);
      return;
    }
    let active = true;
    setMetadataLoading(true);
    setNamespaces([]);
    setTables([]);
    tableDetailCacheRef.current.clear();
    metadataApi.namespaces(datasourceId).then((items) => {
      if (!active) return;
      setNamespaces(items);
      const preferred = namespace && items.includes(namespace)
        ? namespace
        : initialNamespace && items.includes(initialNamespace)
          ? initialNamespace
          : profiles.find((profile) => profile.id === datasourceId)?.databaseName;
      const nextNamespace = preferred && items.includes(preferred) ? preferred : items[0] ?? null;
      if (nextNamespace !== namespace) {
        updateQuerySession(resolvedSessionKey, { namespace: nextNamespace });
      }
    }).catch((error) => message.warning(`元数据加载失败: ${(error as Error).message}`))
      .finally(() => active && setMetadataLoading(false));
    return () => {
      active = false;
    };
  }, [datasourceId, initialNamespace]);

  useEffect(() => {
    if (!datasourceId || !namespace) {
      setTables([]);
      return;
    }
    let active = true;
    setMetadataLoading(true);
    metadataApi.tables(datasourceId, namespace).then((items) => {
      if (active) setTables(items);
    }).catch((error) => message.warning(`表元数据加载失败: ${(error as Error).message}`))
      .finally(() => active && setMetadataLoading(false));
    return () => {
      active = false;
    };
  }, [datasourceId, namespace]);

  const busy = confirming || executing;
  const hasOutput = Boolean(preview || result || executing);
  const selected = useMemo(() => profiles.find((p) => p.id === datasourceId), [profiles, datasourceId]);

  metadataContextRef.current = {
    datasourceId,
    databaseType: selected?.type,
    namespace: namespace ?? undefined,
    namespaces,
    tables,
  };

  const loadTableDetail = useCallback(async (targetNamespace: string, table: string) => {
    const currentDatasourceId = datasourceIdRef.current;
    if (!currentDatasourceId) return null;
    const key = `${currentDatasourceId}:${targetNamespace}:${table}`;
    let request = tableDetailCacheRef.current.get(key);
    if (!request) {
      request = metadataApi.tableDetail(currentDatasourceId, targetNamespace, table)
        .catch(() => null);
      tableDetailCacheRef.current.set(key, request);
    }
    return request;
  }, []);

  const applyInspection = useCallback((value: string) => {
    const nextIssues = inspectSql(value);
    setIssues(nextIssues);
    const model = editorRef.current?.getModel();
    if (model && monacoRef.current) setSqlMarkers(monacoRef.current, model, nextIssues);
    return nextIssues;
  }, []);

  const sqlForExecution = useCallback(() => {
    const editor = editorRef.current;
    const selection = editor?.getSelection();
    const selectedSql = editor && selection ? editor.getModel()?.getValueInRange(selection) : '';
    return selectedSql?.trim() ? selectedSql : sql;
  }, [sql]);

  const runPreview = useCallback(async () => {
    const executionSql = sqlForExecution();
    if (!datasourceId || !executionSql.trim()) return;
    const localIssues = applyInspection(sql);
    if (localIssues.some((issue) => issue.severity === 'error')) {
      message.error('请先修复编辑器中标记的 SQL 结构问题');
      editorRef.current?.focus();
      return;
    }
    updateQuerySession(resolvedSessionKey, {
      confirming: true,
      result: null,
      pendingSql: executionSql,
    });
    try {
      updateQuerySession(resolvedSessionKey, {
        preview: await workbenchApi.preview(datasourceId, executionSql),
      });
    } catch (e) {
      message.error((e as Error).message);
      updateQuerySession(resolvedSessionKey, { confirming: false, pendingSql: null });
    }
  }, [applyInspection, datasourceId, resolvedSessionKey, sql, sqlForExecution, updateQuerySession]);

  runPreviewRef.current = runPreview;

  const checkSql = useCallback(async () => {
    if (!datasourceId || !sql.trim()) return;
    const localIssues = applyInspection(sql);
    if (localIssues.some((issue) => issue.severity === 'error')) {
      message.error('SQL 检查发现结构问题，请查看编辑器标记');
      editorRef.current?.focus();
      return;
    }
    setChecking(true);
    try {
      const checked = await workbenchApi.preview(datasourceId, sql);
      updateQuerySession(resolvedSessionKey, {
        preview: checked,
        confirming: false,
        pendingSql: null,
      });
      message.success(`检查完成，识别到 ${checked.statements.length} 条 SQL`);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setChecking(false);
    }
  }, [applyInspection, datasourceId, resolvedSessionKey, sql, updateQuerySession]);

  const formatSql = useCallback(async () => {
    const editor = editorRef.current;
    const model = editor?.getModel();
    if (!datasourceId || !editor || !model) return;
    const selection = editor.getSelection();
    const hasSelection = Boolean(selection && !selection.isEmpty());
    const source = hasSelection && selection ? model.getValueInRange(selection) : model.getValue();
    if (!source.trim()) return;
    setFormatting(true);
    try {
      const formatted = await workbenchApi.format(datasourceId, source);
      const range = hasSelection && selection ? selection : model.getFullModelRange();
      editor.executeEdits('meper-sql-format', [{ range, text: formatted.sql, forceMoveMarkers: true }]);
      applyInspection(model.getValue());
      message.success(hasSelection ? '已格式化选中 SQL' : 'SQL 格式化完成');
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setFormatting(false);
    }
  }, [applyInspection, datasourceId]);

  formatSqlRef.current = formatSql;

  const runExecute = useCallback(async () => {
    if (!datasourceId) return;
    const executionSql = pendingSql ?? sql;
    updateQuerySession(resolvedSessionKey, { executing: true });
    try {
      updateQuerySession(resolvedSessionKey, {
        result: await workbenchApi.execute(datasourceId, executionSql, maxRows),
        preview: null,
        confirming: false,
        pendingSql: null,
      });
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      updateQuerySession(resolvedSessionKey, { executing: false });
    }
  }, [datasourceId, maxRows, pendingSql, resolvedSessionKey, sql, updateQuerySession]);

  useEffect(() => () => {
    window.clearTimeout(markerTimerRef.current);
    completionProviderRef.current?.dispose();
    changeListenerRef.current?.dispose();
  }, []);

  const tabs = useMemo(() => {
    if (!result) return [];
    return result.statements.map((s) => ({
      key: String(s.seq),
      label: (
        <Space size={4}>
          <span>#{s.seq}</span>
          <Tag color={CATEGORY_COLORS[s.category]} style={{ marginInlineEnd: 0 }}>
            {s.category}
          </Tag>
        </Space>
      ),
      children: s.error ? (
        <Alert type="error" message="执行失败" description={s.error} style={{ marginTop: 8 }} />
      ) : s.query && s.resultData ? (
        <ResultSet data={s.resultData} />
      ) : (
        <Alert
          style={{ marginTop: 8 }}
          type="success"
          message={`执行成功, 影响 ${s.updateCount ?? 0} 行, ${s.durationMs}ms`}
        />
      ),
    }));
  }, [result]);

  return (
    <div className={styles.root}>
      <div className={styles.toolbar}>
        <Space style={{ minWidth: 0, flex: 1 }}>
          <Typography.Text strong style={{ whiteSpace: 'nowrap' }}>
            <CodeOutlined /> SQL 查询
          </Typography.Text>
          <Select
            size="small"
            style={{ width: 280 }}
            placeholder="选择数据源"
            value={datasourceId}
            onChange={(value) => updateQuerySession(resolvedSessionKey, {
              datasourceId: value,
              namespace: null,
              preview: null,
              result: null,
              confirming: false,
              pendingSql: null,
            })}
            options={profiles.map((p) => ({
              value: p.id,
              label: `${p.name}（${p.type} · ${p.host}:${p.port}）`,
            }))}
          />
          <Select
            size="small"
            loading={metadataLoading}
            style={{ width: 160 }}
            placeholder="选择库 / Schema"
            value={namespace}
            onChange={(value) => updateQuerySession(resolvedSessionKey, { namespace: value })}
            options={namespaces.map((item) => ({ value: item, label: item }))}
            showSearch
            optionFilterProp="label"
          />
          <Tooltip title="单语句返回行数上限（硬上限 10000）">
            <Space size={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                最大行数
              </Typography.Text>
              <InputNumber
                size="small"
                min={1}
                max={10000}
                value={maxRows}
                onChange={(value) => updateQuerySession(resolvedSessionKey, { maxRows: value ?? 1000 })}
                style={{ width: 90 }}
              />
            </Space>
          </Tooltip>
          {selected && (
            <Typography.Text type="secondary" ellipsis style={{ maxWidth: 260, fontSize: 12 }}>
              {selected.type} / {selected.host}:{selected.port}
            </Typography.Text>
          )}
        </Space>
        <Space>
          <Button type="text" size="small" icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>
            执行历史
          </Button>
        </Space>
      </div>

      <div className={styles.editorBox} style={hasOutput ? undefined : { flex: 1, minHeight: 220 }}>
        <Editor
          path={`file:///meper-workbench/${encodeURIComponent(resolvedSessionKey)}.sql`}
          height={hasOutput ? '238px' : '100%'}
          language="sql"
          theme="vs"
          value={sql}
          onChange={(value) => updateQuerySession(resolvedSessionKey, {
            sql: value ?? '',
            preview: null,
            confirming: false,
            pendingSql: null,
          })}
          onMount={(editor, monaco) => {
            editorRef.current = editor;
            monacoRef.current = monaco;
            completionProviderRef.current?.dispose();
            completionProviderRef.current = registerSqlCompletionProvider({
              monaco,
              editor,
              getContext: () => metadataContextRef.current,
              loadTableDetail,
            });
            applyInspection(editor.getValue());
            changeListenerRef.current?.dispose();
            changeListenerRef.current = editor.onDidChangeModelContent(() => {
              window.clearTimeout(markerTimerRef.current);
              markerTimerRef.current = window.setTimeout(() => applyInspection(editor.getValue()), 300);
            });
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runPreviewRef.current());
            editor.addCommand(
              monaco.KeyMod.Shift | monaco.KeyMod.Alt | monaco.KeyCode.KeyF,
              () => formatSqlRef.current(),
            );
          }}
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            lineHeight: 20,
            automaticLayout: true,
            wordBasedSuggestions: 'off',
            quickSuggestions: { other: true, comments: false, strings: false },
            suggestOnTriggerCharacters: true,
            parameterHints: { enabled: true },
            snippetSuggestions: 'top',
            scrollBeyondLastLine: false,
            autoClosingQuotes: 'always',
            autoClosingBrackets: 'always',
            fixedOverflowWidgets: true,
            tabSize: 2,
          }}
          loading={<Spin style={{ margin: 80 }} />}
        />
      </div>

      <div className={styles.actionBar}>
        <Space>
          <Button
            type="primary"
            size="small"
            icon={<CaretRightOutlined />}
            onClick={() => runPreviewRef.current()}
            disabled={!datasourceId || busy || !sql.trim()}
          >
            {confirming ? '1/2 已预检' : '运行'}
          </Button>
          {confirming && preview && (
            <Button size="small" type="primary" danger loading={executing} onClick={runExecute}>
              2/2 确认执行 ({preview.statements.length} 条)
            </Button>
          )}
          <Button
            size="small"
            icon={<CheckCircleOutlined />}
            loading={checking}
            disabled={!datasourceId || busy || !sql.trim()}
            onClick={checkSql}
          >
            检查
          </Button>
          <Button
            size="small"
            icon={<FormatPainterOutlined />}
            loading={formatting}
            disabled={!datasourceId || busy || !sql.trim()}
            onClick={() => formatSqlRef.current()}
          >
            格式化
          </Button>
          {issues.some((issue) => issue.severity === 'error') ? (
            <Tag color="error">{issues.filter((issue) => issue.severity === 'error').length} 个错误</Tag>
          ) : issues.length ? (
            <Tag color="warning">{issues.length} 个提醒</Tag>
          ) : (
            <Tag color="success">结构检查通过</Tag>
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Ctrl/Cmd + Enter 运行，Shift + Alt + F 格式化，Ctrl/Cmd + Space 补全
          </Typography.Text>
        </Space>
      </div>

      {preview && (
        <Alert
          style={{ margin: 8, flexShrink: 0 }}
          type={preview.statements.some((s) => s.category === 'DDL' || s.category === 'DML') ? 'warning' : 'info'}
          showIcon={false}
          message={
            <Space wrap>
              <span>预检结果：</span>
              {preview.statements.map((s) => (
                <Tag key={s.seq} color={CATEGORY_COLORS[s.category]}>
                  #{s.seq} {s.category}
                </Tag>
              ))}
              <Tag color="volcano">{preview.enforcement}（阶段 1 无策略约束，P2 接入）</Tag>
            </Space>
          }
          description={
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              数据源「{preview.datasourceName}」, 目标执行 {preview.statements.length} 条语句。逐语句独立提交 (autocommit)，失败即停止后续语句
            </Typography.Text>
          }
        />
      )}

      {executing && <Alert type="info" showIcon={false} message="正在执行 SQL" style={{ margin: 8, flexShrink: 0 }} />}

      {result && (
        <div className={styles.output}>
          <Alert
            style={{ margin: '6px 8px', flexShrink: 0, paddingBlock: 3 }}
            type={result.status === 'SUCCESS' ? 'success' : result.status === 'PARTIAL' ? 'warning' : 'error'}
            showIcon={false}
            message={`执行完成: ${result.status}, 共 ${result.statements.length} 条, ${result.durationMs}ms, 记录 #${result.executionId}`}
          />
          {tabs.length > 0 && <Tabs items={tabs} type="line" size="small" />}
        </div>
      )}

      <HistoryDrawer open={historyOpen} onClose={() => setHistoryOpen(false)} />
    </div>
  );
}

function HistoryDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [records, setRecords] = useState<import('@/service/api').ExecutionRecord[] | null>(null);

  useEffect(() => {
    if (open) {
      workbenchApi.executions().then(setRecords).catch((e) => message.error((e as Error).message));
    }
  }, [open]);

  return (
    <Drawer title="执行历史（最近 100 条）" width={640} open={open} onClose={onClose} destroyOnClose>
      {!records ? (
        <Spin />
      ) : records.length === 0 ? (
        <Typography.Text type="secondary">暂无执行记录</Typography.Text>
      ) : (
        <Timeline
          items={records.map((r) => ({
            color: r.status === 'SUCCESS' ? 'green' : r.status === 'PARTIAL' ? 'orange' : 'red',
            children: (
              <div>
                <Space wrap>
                  <Tag>#{r.id}</Tag>
                  <Tag color={r.status === 'SUCCESS' ? 'green' : r.status === 'PARTIAL' ? 'orange' : 'red'}>
                    {r.status}
                  </Tag>
                  <span style={{ fontSize: 12 }}>
                    数据源 {r.datasourceId} · {r.statementCount} 条 · {r.subject}
                  </span>
                </Space>
                <div style={{ fontSize: 12, color: '#888', margin: '4px 0' }}>
                  {new Date(r.startedAt).toLocaleString()} · {r.correlationId.slice(0, 8)}
                </div>
                <pre
                  style={{
                    fontSize: 12, background: '#f6f6f6', padding: 8, borderRadius: 6,
                    maxHeight: 88, overflow: 'hidden', margin: 0,
                  }}
                >
                  {r.sqlText.slice(0, 300)}
                </pre>
              </div>
            ),
          }))}
        />
      )}
    </Drawer>
  );
}
