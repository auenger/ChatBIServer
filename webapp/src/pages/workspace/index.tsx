import { useCallback, useEffect, useState } from 'react';
import { Button, Empty, Typography, message } from 'antd';
import { ConsoleSqlOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import { useLocation } from 'umi';
import DatabaseTree, { type OpenTarget } from '@/components/DatabaseTree';
import TableDataView from '@/components/TableDataView';
import TableStructureView from '@/components/TableStructureView';
import WorkbenchPanel from '@/components/WorkbenchPanel';
import type { DataSourceProfile } from '@/service/api';
import { datasourceApi } from '@/service/api';

type ContentMode =
  | { type: 'empty' }
  | { type: 'data'; profile: DataSourceProfile; namespace: string; table: string }
  | { type: 'structure'; profile: DataSourceProfile; namespace: string; table: string }
  | { type: 'query'; profile: DataSourceProfile; namespace?: string; table?: string };

// Workspace（对齐 Chat2DB 工作区）：左侧库表树 + 右侧内容区（单视图切换：表数据/表结构/查询）
const useStyles = createStyles(({ token }) => ({
  layout: {
    display: 'flex',
    gap: 12,
    height: 'calc(100vh - 40px)',
  },
  sider: {
    width: 280,
    flexShrink: 0,
    background: token.colorBgContainer,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    padding: 12,
    overflow: 'auto',
  },
  content: {
    flex: 1,
    background: token.colorBgContainer,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    padding: 16,
    overflow: 'auto',
    minWidth: 0,
  },
  collapse: {
    alignSelf: 'flex-start',
    border: `1px solid ${token.colorBorderSecondary}`,
    background: token.colorBgContainer,
    borderRadius: token.borderRadiusLG,
    padding: '10px 4px',
  },
}));

/** 生成打开表的初始 SQL（按方言使用对应标识符引用符）。 */
function quoteFor(type: DataSourceProfile['type']): (s: string) => string {
  if (type === 'MYSQL') return (s) => '`' + s + '`';
  if (type === 'SQLSERVER') return (s) => '[' + s + ']';
  return (s) => '"' + s + '"';
}

export default function WorkspacePage() {
  const { styles } = useStyles();
  const location = useLocation();
  const [profiles, setProfiles] = useState<DataSourceProfile[]>([]);
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const [mode, setMode] = useState<ContentMode>({ type: 'empty' });

  const loadProfiles = useCallback(() => {
    datasourceApi
      .list()
      .then((list) => {
        setProfiles(list);
        // 支持 /workspace?ds=N 直达：自动展开该数据源第一个库并打开查询
        const fromQuery = new URLSearchParams(location.search).get('ds');
        if (fromQuery && mode.type === 'empty') {
          const p = list.find((x) => x.id === Number(fromQuery));
          if (p) {
            setMode({ type: 'query', profile: p });
          }
        }
      })
      .catch((e) => message.error((e as Error).message));
  }, [location.search]);

  useEffect(() => {
    loadProfiles();
  }, []);

  const [lastSql, setLastSql] = useState<string | undefined>(undefined);

  const openTable = (t: OpenTarget) => {
    setMode({ type: 'data', profile: t.profile, namespace: t.namespace, table: t.table });
  };

  const openStructure = (t: OpenTarget) => {
    setMode({ type: 'structure', profile: t.profile, namespace: t.namespace, table: t.table });
  };

  const newQuery = (profile: DataSourceProfile, namespace: string, table?: string) => {
    const q = quoteFor(profile.type);
    const qualified = table ? `${q(namespace)}.${q(table)}` : q(namespace);
    setMode({
      type: 'query',
      profile,
      namespace,
      table,
    });
    setLastSql(`SELECT * FROM ${qualified} LIMIT 100;`);
  };

  return (
    <div className={styles.layout}>
      <div className={styles.sider} style={{ display: siderCollapsed ? 'none' : undefined }}>
        <Typography.Title level={5} style={{ marginTop: 0 }}>
          数据源
        </Typography.Title>
        <DatabaseTree
          profiles={profiles}
          onOpenTable={openTable}
          onOpenStructure={openStructure}
          onNewQuery={(p, ns) => newQuery(p, ns)}
          onRefresh={loadProfiles}
        />
      </div>

      {siderCollapsed && (
        <div className={styles.collapse}>
          <Button
            type="text"
            size="small"
            icon={<MenuUnfoldOutlined />}
            onClick={() => setSiderCollapsed(false)}
          />
        </div>
      )}

      <div className={styles.content}>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 4 }}>
          <Button
            type="text"
            size="small"
            icon={<MenuFoldOutlined />}
            onClick={() => setSiderCollapsed(true)}
          >
            收起树
          </Button>
        </div>

        {mode.type === 'empty' && (
          <Empty
            style={{ marginTop: 120 }}
            description={
              <span>
                在左侧展开数据源，双击表查看数据
                <br />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  库节点悬浮「查询」可新建查询；表节点悬浮「结构」可查看表结构
                </Typography.Text>
              </span>
            }
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}

        {mode.type === 'data' && (
          <TableDataView profile={mode.profile} namespace={mode.namespace} table={mode.table} />
        )}

        {mode.type === 'structure' && (
          <TableStructureView profile={mode.profile} namespace={mode.namespace} table={mode.table} />
        )}

        {mode.type === 'query' && (
          <>
            <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
              <ConsoleSqlOutlined style={{ color: '#1677ff' }} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                查询 · {mode.profile.name}
                {mode.namespace ? ` · ${mode.namespace}` : ''}
                {mode.table ? ` · ${mode.table}` : ''}
              </Typography.Text>
            </div>
            <WorkbenchPanel
              key={`${mode.profile.id}:${mode.namespace ?? ''}:${mode.table ?? ''}`}
              initialDatasourceId={mode.profile.id}
              initialSql={lastSql}
            />
          </>
        )}
      </div>
    </div>
  );
}
