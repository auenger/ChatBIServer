import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, message, Tooltip, Tree } from 'antd';
import {
  AppstoreOutlined, CodeOutlined, DatabaseOutlined, EyeOutlined, FolderOpenOutlined,
  ReloadOutlined, TableOutlined, UnorderedListOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import type { DataSourceProfile, TableInfo } from '@/service/api';
import { metadataApi } from '@/service/api';

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
  onOpenObjectList: (profile: DataSourceProfile, namespace: string, type: 'TABLE' | 'VIEW') => void;
  onNewQuery: (profile: DataSourceProfile, namespace?: string, table?: string) => void;
  onRefresh: () => void;
}

interface WorkspaceDataNode extends DataNode {
  searchText?: string;
  children?: WorkspaceDataNode[];
}

const KEY_DS = 'ds:';
const KEY_NS = 'ns:';
const KEY_GROUP = 'group:';

function encode(value: string) {
  return encodeURIComponent(value);
}

function nodeLabelText(node: WorkspaceDataNode): string {
  return String(node.searchText ?? '').toLocaleLowerCase();
}

function updateChildren(nodes: WorkspaceDataNode[], key: React.Key, children: WorkspaceDataNode[]): WorkspaceDataNode[] {
  return nodes.map((node) => {
    if (node.key === key) return { ...node, children };
    if (node.children) return { ...node, children: updateChildren(node.children, key, children) };
    return node;
  });
}

function filterNodes(nodes: WorkspaceDataNode[], keyword: string): WorkspaceDataNode[] {
  if (!keyword) return nodes;
  return nodes.flatMap((node) => {
    const children = node.children ? filterNodes(node.children, keyword) : [];
    if (nodeLabelText(node).includes(keyword) || children.length) {
      return [{ ...node, children: children.length ? children : node.children }];
    }
    return [];
  });
}

export default function DatabaseTree({
  profiles,
  onOpenTable,
  onOpenStructure,
  onOpenObjectList,
  onNewQuery,
  onRefresh,
}: Props) {
  const [keyword, setKeyword] = useState('');
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [treeData, setTreeData] = useState<WorkspaceDataNode[]>([]);

  const profileTitle = (profile: DataSourceProfile) => (
    <span className="tree-node-title">
      <DatabaseOutlined className="tree-node-icon tree-node-icon-datasource" />
      <span className="tree-node-label tree-node-label-strong">{profile.name}</span>
      <span className="tree-node-meta">{profile.type}</span>
      <Tooltip title="新建查询">
        <CodeOutlined
          className="tree-action"
          onClick={(event) => {
            event.stopPropagation();
            onNewQuery(profile);
          }}
        />
      </Tooltip>
    </span>
  );

  useEffect(() => {
    setTreeData(profiles.map((profile) => ({
      key: `${KEY_DS}${profile.id}`,
      searchText: `${profile.name} ${profile.type}`,
      title: profileTitle(profile),
      isLeaf: false,
    })));
  }, [profiles]);

  const makeTableNode = (profile: DataSourceProfile, namespace: string, table: TableInfo): WorkspaceDataNode => {
    const target: OpenTarget = { profile, namespace, table: table.name, type: table.type };
    return {
      key: `object:${profile.id}:${encode(namespace)}:${table.type}:${encode(table.name)}`,
      searchText: `${table.name} ${table.remarks ?? ''}`,
      title: (
        <span className="tree-node-title" onDoubleClick={() => onOpenTable(target)}>
          {table.type === 'VIEW'
            ? <EyeOutlined className="tree-node-icon tree-node-icon-view" />
            : <TableOutlined className="tree-node-icon tree-node-icon-table" />}
          <span className="tree-node-label">{table.name}</span>
          <Tooltip title="新建查询">
            <CodeOutlined
              className="tree-action"
              onClick={(event) => {
                event.stopPropagation();
                onNewQuery(profile, namespace, table.name);
              }}
            />
          </Tooltip>
          <Tooltip title="查看结构">
            <UnorderedListOutlined
              className="tree-action"
              onClick={(event) => {
                event.stopPropagation();
                onOpenStructure(target);
              }}
            />
          </Tooltip>
        </span>
      ),
      isLeaf: true,
    };
  };

  const loadNode = async (node: WorkspaceDataNode) => {
    const key = String(node.key);
    try {
      if (key.startsWith(KEY_DS)) {
        const profile = profiles.find((item) => item.id === Number(key.slice(KEY_DS.length)));
        if (!profile) return;
        const namespaces = await metadataApi.namespaces(profile.id);
        const children = namespaces.map((namespace): WorkspaceDataNode => ({
          key: `${KEY_NS}${profile.id}:${encode(namespace)}`,
          searchText: namespace,
          title: (
            <span className="tree-node-title">
              <AppstoreOutlined className="tree-node-icon tree-node-icon-namespace" />
              <span className="tree-node-label tree-node-label-strong">{namespace}</span>
              <Tooltip title="新建查询">
                <CodeOutlined
                  className="tree-action"
                  onClick={(event) => {
                    event.stopPropagation();
                    onNewQuery(profile, namespace);
                  }}
                />
              </Tooltip>
            </span>
          ),
          isLeaf: false,
        }));
        setTreeData((current) => updateChildren(current, node.key, children));
        return;
      }

      if (key.startsWith(KEY_NS)) {
        const [, datasourceIdText, encodedNamespace] = key.split(':');
        const profile = profiles.find((item) => item.id === Number(datasourceIdText));
        if (!profile) return;
        const namespace = decodeURIComponent(encodedNamespace);
        const tables = await metadataApi.tables(profile.id, namespace);
        const makeGroup = (type: 'TABLE' | 'VIEW', label: string, icon: React.ReactNode): WorkspaceDataNode => {
          const objects = tables.filter((table) => table.type === type);
          return {
            key: `${KEY_GROUP}${profile.id}:${encode(namespace)}:${type}`,
            searchText: label,
            title: (
              <span className="tree-node-title">
                {icon}
                <span className="tree-node-label">{label}</span>
                <span className="tree-node-count">{objects.length}</span>
              </span>
            ),
            children: objects.map((table) => makeTableNode(profile, namespace, table)),
            isLeaf: objects.length === 0,
          };
        };
        setTreeData((current) => updateChildren(current, node.key, [
          makeGroup('TABLE', '表', <FolderOpenOutlined className="tree-node-icon tree-node-icon-folder" />),
          makeGroup('VIEW', '视图', <FolderOpenOutlined className="tree-node-icon tree-node-icon-folder" />),
        ]));
      }
    } catch (error) {
      message.error((error as Error).message);
      throw error;
    }
  };

  const handleSelect = (_keys: React.Key[], info: { node: DataNode }) => {
    const key = String(info.node.key);
    if (!key.startsWith(KEY_GROUP)) return;
    const [, datasourceIdText, encodedNamespace, type] = key.split(':');
    const profile = profiles.find((item) => item.id === Number(datasourceIdText));
    if (profile && (type === 'TABLE' || type === 'VIEW')) {
      onOpenObjectList(profile, decodeURIComponent(encodedNamespace), type);
    }
  };

  const handleDoubleClick = (event: React.MouseEvent, node: DataNode) => {
    if ((event.target as HTMLElement).closest('.tree-action') || node.isLeaf) return;
    const willExpand = !expanded.includes(node.key);
    setExpanded((current) => (
      current.includes(node.key)
        ? current.filter((key) => key !== node.key)
        : [...current, node.key]
    ));
    if (willExpand && !node.children) {
      void loadNode(node as WorkspaceDataNode);
    }
  };

  const displayedTree = useMemo(
    () => filterNodes(treeData, keyword.trim().toLocaleLowerCase()),
    [treeData, keyword],
  );

  return (
    <div className="database-tree-root">
      <div className="database-tree-toolbar">
        <Input
          size="small"
          placeholder="搜索已加载的数据库对象"
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          prefix={<DatabaseOutlined className="database-tree-search-icon" />}
        />
        <Tooltip title="刷新数据源树">
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => {
              setExpanded([]);
              setTreeData([]);
              onRefresh();
            }}
          />
        </Tooltip>
      </div>
      {profiles.length === 0 ? (
        <Empty description="暂无数据源" image={Empty.PRESENTED_IMAGE_SIMPLE} className="database-tree-empty" />
      ) : (
        <Tree
          blockNode
          showLine={{ showLeafIcon: false }}
          treeData={displayedTree}
          expandedKeys={expanded}
          onExpand={setExpanded}
          onDoubleClick={handleDoubleClick}
          onSelect={handleSelect}
          loadData={loadNode}
        />
      )}
      <style>{`
        .database-tree-root { display: flex; flex-direction: column; height: 100%; min-height: 0; }
        .database-tree-toolbar { display: flex; gap: 6px; padding-bottom: 8px; }
        .database-tree-search-icon { color: #bfbfbf; }
        .database-tree-empty { margin-top: 40px; }
        .database-tree-root .ant-tree { min-width: 0; overflow: auto; }
        .database-tree-root .ant-tree-node-content-wrapper { min-width: 0; flex: 1; overflow: hidden; }
        .database-tree-root .ant-tree-title { display: block; width: 100%; min-width: 0; }
        .tree-node-title { display: inline-flex; width: 100%; min-width: 0; align-items: center; gap: 6px; }
        .tree-node-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .tree-node-label-strong { font-weight: 500; }
        .tree-node-meta { color: #999; font-size: 11px; }
        .tree-node-count { margin-left: auto; padding: 0 5px; border-radius: 8px; color: #999; background: #f2f3f5; font-size: 11px; line-height: 16px; }
        .tree-node-icon { flex: none; }
        .tree-node-icon-datasource { color: #1677ff; }
        .tree-node-icon-namespace { color: #6f52c4; }
        .tree-node-icon-folder { color: #e6a23c; }
        .tree-node-icon-table { color: #52c41a; }
        .tree-node-icon-view { color: #13c2c2; }
        .tree-action { flex: none; visibility: hidden; margin-left: auto; color: #666; cursor: pointer; }
        .tree-action + .tree-action { margin-left: 0; }
        .tree-node-title:hover .tree-action { visibility: visible; }
      `}</style>
    </div>
  );
}
