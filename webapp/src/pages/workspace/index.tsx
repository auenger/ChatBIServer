import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Empty, Tabs, Tooltip, Typography, message } from 'antd';
import {
  CodeOutlined, DatabaseOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  PlusOutlined, TableOutlined, UnorderedListOutlined,
} from '@ant-design/icons';
import { createStyles } from 'antd-style';
import { useLocation } from 'umi';
import DatabaseTree, { type OpenTarget } from '@/components/DatabaseTree';
import ObjectListView from '@/components/ObjectListView';
import TableDataView from '@/components/TableDataView';
import TableStructureView from '@/components/TableStructureView';
import WorkbenchPanel from '@/components/WorkbenchPanel';
import { useWorkspaceModel, type WorkspaceView } from '@/models/workspace';
import type { DataSourceProfile } from '@/service/api';
import { datasourceApi } from '@/service/api';

const DEFAULT_SIDER_WIDTH = 280;
const MIN_SIDER_WIDTH = 220;
const MAX_SIDER_WIDTH = 520;
const SIDER_STORAGE_KEY = 'meper.workspace.siderWidth';

const useStyles = createStyles(({ token }) => ({
  layout: {
    position: 'relative',
    display: 'flex',
    width: '100%',
    height: '100vh',
    minWidth: 0,
    overflow: 'hidden',
    background: token.colorBgContainer,
  },
  sider: {
    position: 'relative',
    display: 'flex',
    minWidth: 0,
    flexShrink: 0,
    flexDirection: 'column',
    borderRight: `1px solid ${token.colorBorderSecondary}`,
    background: token.colorBgContainer,
  },
  siderHeader: {
    display: 'flex',
    flexShrink: 0,
    height: 37,
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 6px 0 10px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  siderActions: {
    display: 'flex',
    alignItems: 'center',
    gap: 2,
  },
  tree: {
    minHeight: 0,
    flex: 1,
    padding: '8px 6px',
    overflow: 'auto',
  },
  resizer: {
    position: 'absolute',
    zIndex: 2,
    top: 0,
    right: -3,
    width: 6,
    height: '100%',
    cursor: 'col-resize',
    '&:hover': { background: token.colorPrimaryBorder },
  },
  collapsed: {
    display: 'flex',
    width: 36,
    flexShrink: 0,
    justifyContent: 'center',
    paddingTop: 5,
    borderRight: `1px solid ${token.colorBorderSecondary}`,
    background: token.colorBgContainer,
  },
  content: {
    display: 'flex',
    minWidth: 0,
    minHeight: 0,
    flex: 1,
    flexDirection: 'column',
    background: token.colorBgContainer,
  },
  tabs: {
    height: '100%',
    '& > .ant-tabs-nav': {
      flexShrink: 0,
      height: 37,
      margin: 0,
      paddingInline: 6,
      borderBottom: `1px solid ${token.colorBorderSecondary}`,
    },
    '& > .ant-tabs-content-holder, & > .ant-tabs-content-holder > .ant-tabs-content, & .ant-tabs-tabpane': {
      height: '100%',
      minHeight: 0,
    },
    '& .ant-tabs-tab': { paddingBlock: '7px !important' },
  },
  tabPane: {
    width: '100%',
    height: '100%',
    minHeight: 0,
    overflow: 'hidden',
  },
  empty: {
    display: 'flex',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    background: token.colorFillQuaternary,
  },
}));

function quoteFor(type: DataSourceProfile['type']): (value: string) => string {
  if (type === 'MYSQL') return (value) => '`' + value + '`';
  if (type === 'SQLSERVER') return (value) => '[' + value + ']';
  return (value) => '"' + value + '"';
}

function initialTableSql(profile: DataSourceProfile, namespace: string, table?: string) {
  const quote = quoteFor(profile.type);
  const target = table ? `${quote(namespace)}.${quote(table)}` : quote(namespace);
  if (profile.type === 'SQLSERVER') return `SELECT TOP 100 * FROM ${target};`;
  if (profile.type === 'ORACLE') return `SELECT * FROM ${target} FETCH FIRST 100 ROWS ONLY;`;
  return `SELECT * FROM ${target} LIMIT 100;`;
}

function renderView(view: WorkspaceView) {
  if (view.type === 'objects') return null;
  if (view.type === 'data') {
    return <TableDataView profile={view.profile} namespace={view.namespace} table={view.table} />;
  }
  if (view.type === 'structure') {
    return <TableStructureView profile={view.profile} namespace={view.namespace} table={view.table} />;
  }
  return (
    <WorkbenchPanel
      sessionKey={view.sessionKey}
      initialDatasourceId={view.profile.id}
      initialNamespace={view.namespace}
      initialSql={view.initialSql}
    />
  );
}

export default function WorkspacePage() {
  const { styles } = useStyles();
  const location = useLocation();
  const initialRouteHandled = useRef(false);
  const [profiles, setProfiles] = useState<DataSourceProfile[]>([]);
  const [siderCollapsed, setSiderCollapsed] = useState(false);
  const [siderWidth, setSiderWidth] = useState(() => {
    const saved = Number(localStorage.getItem(SIDER_STORAGE_KEY));
    return Number.isFinite(saved) && saved >= MIN_SIDER_WIDTH && saved <= MAX_SIDER_WIDTH ? saved : DEFAULT_SIDER_WIDTH;
  });
  const tabs = useWorkspaceModel((state) => state.tabs);
  const activeKey = useWorkspaceModel((state) => state.activeKey);
  const addTab = useWorkspaceModel((state) => state.addTab);
  const closeTab = useWorkspaceModel((state) => state.closeTab);
  const setActiveKey = useWorkspaceModel((state) => state.setActiveKey);
  const createQueryTab = useWorkspaceModel((state) => state.createQueryTab);

  const loadProfiles = useCallback(() => {
    datasourceApi.list().then((list) => {
      setProfiles(list);
      const fromQuery = new URLSearchParams(location.search).get('ds');
      if (!initialRouteHandled.current && fromQuery) {
        initialRouteHandled.current = true;
        const profile = list.find((item) => item.id === Number(fromQuery));
        if (profile) {
          createQueryTab(profile, { fixedKey: `query:${profile.id}:route` });
        }
      }
    }).catch((error) => message.error((error as Error).message));
  }, [createQueryTab, location.search]);

  useEffect(() => {
    loadProfiles();
  }, [loadProfiles]);

  const openTable = (target: OpenTarget) => addTab({
    key: `data:${target.profile.id}:${target.namespace}:${target.table}`,
    title: target.table,
    view: { type: 'data', profile: target.profile, namespace: target.namespace, table: target.table },
  });

  const openStructure = (target: OpenTarget) => addTab({
    key: `structure:${target.profile.id}:${target.namespace}:${target.table}`,
    title: `${target.table} 结构`,
    view: { type: 'structure', profile: target.profile, namespace: target.namespace, table: target.table },
  });

  const openObjectList = (profile: DataSourceProfile, namespace: string, objectType: 'TABLE' | 'VIEW') => addTab({
    key: `objects:${profile.id}:${namespace}:${objectType}`,
    title: `${namespace} - ${objectType === 'TABLE' ? '表' : '视图'}`,
    view: { type: 'objects', profile, namespace, objectType },
  });

  const newQuery = (profile: DataSourceProfile, namespace?: string, table?: string) => {
    const initialSql = namespace && table ? initialTableSql(profile, namespace, table) : undefined;
    createQueryTab(profile, {
      namespace,
      table,
      initialSql,
    });
  };

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = siderWidth;
    const move = (moveEvent: PointerEvent) => {
      setSiderWidth(Math.min(MAX_SIDER_WIDTH, Math.max(MIN_SIDER_WIDTH, startWidth + moveEvent.clientX - startX)));
    };
    const stop = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', stop);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', stop);
  };

  useEffect(() => {
    localStorage.setItem(SIDER_STORAGE_KEY, String(siderWidth));
  }, [siderWidth]);

  return (
    <div className={styles.layout}>
      {siderCollapsed ? (
        <div className={styles.collapsed}>
          <Tooltip title="展开数据源">
            <Button type="text" size="small" icon={<MenuUnfoldOutlined />} onClick={() => setSiderCollapsed(false)} />
          </Tooltip>
        </div>
      ) : (
        <aside className={styles.sider} style={{ width: siderWidth }}>
          <div className={styles.siderHeader}>
            <Typography.Text strong><DatabaseOutlined /> 数据源</Typography.Text>
            <div className={styles.siderActions}>
              <Tooltip title={profiles.length ? '新建 SQL 查询' : '请先创建数据源'}>
                <Button
                  type="text"
                  size="small"
                  aria-label="新建 SQL 查询"
                  icon={<PlusOutlined />}
                  disabled={!profiles.length}
                  onClick={() => profiles[0] && newQuery(profiles[0])}
                />
              </Tooltip>
              <Tooltip title="收起数据源">
                <Button type="text" size="small" icon={<MenuFoldOutlined />} onClick={() => setSiderCollapsed(true)} />
              </Tooltip>
            </div>
          </div>
          <div className={styles.tree}>
            <DatabaseTree
              profiles={profiles}
              onOpenTable={openTable}
              onOpenStructure={openStructure}
              onOpenObjectList={openObjectList}
              onNewQuery={newQuery}
              onRefresh={loadProfiles}
            />
          </div>
          <div className={styles.resizer} onPointerDown={startResize} />
        </aside>
      )}

      <main className={styles.content}>
        {tabs.length ? (
          <Tabs
            className={styles.tabs}
            type="editable-card"
            hideAdd
            activeKey={activeKey}
            onChange={setActiveKey}
            onEdit={(key, action) => action === 'remove' && closeTab(String(key))}
            items={tabs.map((tab) => ({
              key: tab.key,
              label: (
                <span>
                  {tab.view.type === 'query' ? <CodeOutlined /> : tab.view.type === 'structure' ? <UnorderedListOutlined /> : <TableOutlined />}
                  {' '}{tab.title}
                </span>
              ),
              children: (
                <div className={styles.tabPane}>
                  {tab.view.type === 'objects' ? (
                    <ObjectListView
                      profile={tab.view.profile}
                      namespace={tab.view.namespace}
                      objectType={tab.view.objectType}
                      onOpenTable={openTable}
                      onOpenStructure={openStructure}
                      onNewQuery={newQuery}
                    />
                  ) : renderView(tab.view)}
                </div>
              ),
            }))}
          />
        ) : (
          <div className={styles.empty}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={(
                <span>
                  从左侧数据源打开表或新建查询
                  <br />
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    双击表查看数据，使用节点操作查看结构
                  </Typography.Text>
                </span>
              )}
            >
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                disabled={!profiles.length}
                onClick={() => profiles[0] && newQuery(profiles[0])}
              >
                新建 SQL 查询
              </Button>
            </Empty>
          </div>
        )}
      </main>
    </div>
  );
}
