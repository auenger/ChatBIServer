import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Drawer, InputNumber, message, Select, Space, Spin, Table, Tabs, Tag, Timeline, Tooltip, Typography,
} from 'antd';
import { CaretRightOutlined, HistoryOutlined, ProfileOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import Editor, { type Monaco } from '@monaco-editor/react';
import { useLocation } from 'umi';
import ResultSet from '@/components/ResultSet';
import type { DataSourceProfile, ExecuteResult, PreviewResult, SqlCategory } from '@/service/api';
import { datasourceApi, workbenchApi } from '@/service/api';

// SQL 工作台：执行走「自动 preview → 确认 → execute」两步（防并发：确认前不可重复触发）
const useStyles = createStyles(({ token }) => ({
  toolbar: {
    display: 'flex',
    gap: 12,
    alignItems: 'center',
    marginBottom: 12,
  },
  editorBox: {
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadius,
    overflow: 'hidden',
  },
}));

const CATEGORY_COLORS: Record<SqlCategory, string> = {
  SELECT: 'green',
  DML: 'orange',
  DDL: 'red',
  TCL: 'purple',
  OTHER: 'default',
};

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE', 'CREATE',
  'TABLE', 'DROP', 'ALTER', 'TRUNCATE', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON',
  'GROUP', 'BY', 'ORDER', 'HAVING', 'LIMIT', 'OFFSET', 'AND', 'OR', 'NOT', 'NULL', 'IS',
  'IN', 'LIKE', 'BETWEEN', 'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'COMMIT',
  'ROLLBACK', 'BEGIN', 'UNION', 'ALL', 'AS', 'ASC', 'DESC',
];

function setupCompletion(monaco: Monaco) {
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      return {
        suggestions: SQL_KEYWORDS.map((k) => ({
          label: k,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: k,
          range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn,
          },
        })),
      };
    },
  });
}

export default function WorkbenchPage() {
  const { styles } = useStyles();
  const location = useLocation();
  const [profiles, setProfiles] = useState<DataSourceProfile[]>([]);
  const [datasourceId, setDatasourceId] = useState<number | null>(null);
  const [sql, setSql] = useState('SELECT 1');
  const [maxRows, setMaxRows] = useState<number>(1000);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [result, setResult] = useState<ExecuteResult | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  useEffect(() => {
    datasourceApi.list().then((list) => {
      setProfiles(list);
      const fromQuery = new URLSearchParams(location.search).get('ds');
      const initial = fromQuery ? Number(fromQuery) : list[0]?.id;
      if (initial && list.some((p) => p.id === initial)) {
        setDatasourceId(initial);
      } else if (list.length > 0) {
        setDatasourceId(list[0].id);
      }
    }).catch((e) => message.error((e as Error).message));
  }, []);

  const busy = confirming || executing;
  const selected = useMemo(() => profiles.find((p) => p.id === datasourceId), [profiles, datasourceId]);

  const runPreview = useCallback(async () => {
    if (!datasourceId || !sql.trim()) return;
    setConfirming(true);
    setResult(null);
    try {
      setPreview(await workbenchApi.preview(datasourceId, sql));
    } catch (e) {
      message.error((e as Error).message);
      setConfirming(false);
    }
  }, [datasourceId, sql]);

  const runExecute = useCallback(async () => {
    if (!datasourceId) return;
    setExecuting(true);
    try {
      setResult(await workbenchApi.execute(datasourceId, sql, maxRows));
      setPreview(null);
      setConfirming(false);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setExecuting(false);
    }
  }, [datasourceId, sql, maxRows]);

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
          message={`执行成功 · 影响 ${s.updateCount ?? 0} 行 · ${s.durationMs}ms`}
        />
      ),
    }));
  }, [result]);

  return (
    <div>
      <div className={styles.toolbar}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          SQL 工作台
        </Typography.Title>
        <Space style={{ flex: 1, justifyContent: 'flex-end' }}>
          <Select
            style={{ width: 280 }}
            placeholder="选择数据源"
            value={datasourceId}
            onChange={setDatasourceId}
            options={profiles.map((p) => ({
              value: p.id,
              label: `${p.name}（${p.type} · ${p.host}:${p.port}）`,
            }))}
          />
          <Tooltip title="单语句返回行数上限（硬上限 10000）">
            <InputNumber min={1} max={10000} value={maxRows} onChange={(v) => setMaxRows(v ?? 1000)} addonBefore="maxRows" style={{ width: 180 }} />
          </Tooltip>
          <Button icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>
            执行历史
          </Button>
        </Space>
      </div>

      <div className={styles.editorBox}>
        <Editor
          height="220px"
          language="sql"
          theme="vs"
          value={sql}
          onChange={(v) => setSql(v ?? '')}
          beforeMount={setupCompletion}
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            automaticLayout: true,
            wordBasedSuggestions: 'off',
            tabSize: 2,
          }}
          loading={<Spin style={{ margin: 80 }} />}
        />
      </div>

      <Space style={{ margin: '12px 0' }}>
        <Button
          type="primary"
          icon={<CaretRightOutlined />}
          onClick={runPreview}
          disabled={!datasourceId || busy || !sql.trim()}
        >
          {confirming ? '1/2 已预检' : '运行'}
        </Button>
        {confirming && preview && (
          <Button type="primary" danger loading={executing} onClick={runExecute}>
            2/2 确认执行（{preview.statements.length} 条语句）
          </Button>
        )}
      </Space>

      {preview && (
        <Alert
          style={{ marginBottom: 12 }}
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
              数据源「{preview.datasourceName}」· 目标执行 {preview.statements.length} 条语句，逐语句独立提交（autocommit），失败即停止后续语句
            </Typography.Text>
          }
        />
      )}

      {executing && <Alert type="info" showIcon={false} message="执行中…" style={{ marginBottom: 12 }} />}

      {result && (
        <div>
          <Alert
            style={{ marginBottom: 12 }}
            type={result.status === 'SUCCESS' ? 'success' : result.status === 'PARTIAL' ? 'warning' : 'error'}
            showIcon={false}
            message={`执行完成：${result.status} · 共 ${result.statements.length} 条 · ${result.durationMs}ms · 记录 #${result.executionId}`}
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
