import { useMemo, useState } from 'react';
import { Button, Empty, Input, message, Tooltip, Tree } from 'antd';
import {
  AppstoreOutlined, CodeOutlined, DatabaseOutlined, EyeOutlined, ReloadOutlined, TableOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import type { DataSourceProfile } from '@/service/api';
import { metadataApi } from '@/service/api';

/**
 * 左侧数据源树（对齐 Chat2DB：数据源 → 库/Schema → 表/视图，懒加载展开）。
 * 双击表节点 → 打开表数据；表节点悬浮「结构」→ 表结构；库节点悬浮「查询」→ 新建查询。
 */
export interface OpenTarget {
  profile: DataSourceProfile;
  namespace: string;
  table: string;
  type: 'TABLE' | 'VIEW';
}

interface Props {
  profiles: DataSourceProfile[];
  onOpenTable: (target: OpenTarget) => void;
  onOpenStructure: (target: OpenTarget) => void;
  onNewQuery: (profile: DataSourceProfile, namespace: string) => void;
  onRefresh: () => void;
}

const KEY_DS = 'ds:';
const KEY_NS = 'ns:';
const KEY_TB = 'tb:';

export default function DatabaseTree({
  profiles, onOpenTable, onOpenStructure, onNewQuery, onRefresh,
}: Props) {
  const [keyword, setKeyword] = useState('');
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [treeVersion, setTreeVersion] = useState(0);

  const filtered = useMemo(
    () => (keyword ? profiles.filter((p) => p.name.includes(keyword)) : profiles),
    [profiles, keyword],
  );

  const treeData: DataNode[] = useMemo(
    () =>
      filtered.map((p) => ({
        key: `${KEY_DS}${p.id}`,
        title: (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <DatabaseOutlined style={{ color: '#1677ff' }} />
            <span style={{ fontWeight: 500 }}>{p.name}</span>
            <span style={{ fontSize: 11, color: '#999' }}>{p.type}</span>
          </span>
        ),
        isLeaf: false,
      })),
    [filtered],
  );

  const loadNamespaces = async (profile: DataSourceProfile): Promise<DataNode[]> => {
    const names = await metadataApi.namespaces(profile.id);
    return names.map((ns) => ({
      key: `${KEY_NS}${profile.id}:${ns}`,
      title: (
        <span className="tree-node-title" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <AppstoreOutlined style={{ color: '#722ed1' }} />
          <span>{ns}</span>
          <Tooltip title="新建查询">
            <CodeOutlined
              className="tree-action"
              onClick={(e) => {
                e.stopPropagation();
                onNewQuery(profile, ns);
              }}
              style={{ visibility: 'hidden', marginLeft: 'auto', cursor: 'pointer' }}
            />
          </Tooltip>
        </span>
      ),
      isLeaf: false,
    }));
  };

  const loadTables = async (profile: DataSourceProfile, ns: string): Promise<DataNode[]> => {
    const tables = await metadataApi.tables(profile.id, ns);
    return tables.map((t) => {
      const target: OpenTarget = { profile, namespace: ns, table: t.name, type: t.type };
      const isView = t.type === 'VIEW';
      return {
        key: `${KEY_TB}${profile.id}:${ns}:${t.name}:${t.type}`,
        title: (
          <span
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
            onDoubleClick={() => onOpenTable(target)}
          >
            {isView ? <EyeOutlined style={{ color: '#13c2c2' }} /> : <TableOutlined style={{ color: '#52c41a' }} />}
            <span>{t.name}</span>
            <span
              className="tree-action"
              style={{ visibility: 'hidden', marginLeft: 'auto', cursor: 'pointer', fontSize: 11, color: '#1677ff' }}
              onClick={(e) => {
                e.stopPropagation();
                onOpenStructure(target);
              }}
            >
              结构
            </span>
          </span>
        ),
        isLeaf: true,
      };
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', gap: 6, paddingBottom: 8 }}>
        <Input
          size="small"
          placeholder="搜索数据源"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          prefix={<DatabaseOutlined style={{ color: '#bbb' }} />}
        />
        <Tooltip title="重置树（收起并重新懒加载）">
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => {
              setExpanded([]);
              setTreeVersion((v) => v + 1);
              onRefresh();
            }}
          />
        </Tooltip>
      </div>
      {profiles.length === 0 ? (
        <Empty description="暂无数据源" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 40 }} />
      ) : (
        <Tree
          key={treeVersion}
          blockNode
          treeData={treeData}
          expandedKeys={expanded}
          onExpand={(keys) => setExpanded(keys)}
          loadData={async (node) => {
            const key = String(node.key);
            try {
              if (key.startsWith(KEY_DS)) {
                const profile = profiles.find((p) => p.id === Number(key.slice(KEY_DS.length)));
                if (profile) {
                  return await loadNamespaces(profile);
                }
              } else if (key.startsWith(KEY_NS)) {
                const rest = key.slice(KEY_NS.length);
                const dsId = Number(rest.slice(0, rest.indexOf(':')));
                const ns = rest.slice(rest.indexOf(':') + 1);
                const profile = profiles.find((p) => p.id === dsId);
                if (profile) {
                  return await loadTables(profile, ns);
                }
              }
            } catch (e) {
              message.error((e as Error).message);
            }
          }}
        />
      )}
      <style>{`
        .tree-node-title:hover .tree-action { visibility: visible !important; }
      `}</style>
    </div>
  );
}
