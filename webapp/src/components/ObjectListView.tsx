import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Input, Pagination, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import {
  CodeOutlined, DatabaseOutlined, EyeOutlined, ReloadOutlined, SearchOutlined,
  TableOutlined, UnorderedListOutlined,
} from '@ant-design/icons';
import { createStyles } from 'antd-style';
import type { DataSourceProfile, TableInfo } from '@/service/api';
import { metadataApi } from '@/service/api';
import type { OpenTarget } from '@/components/DatabaseTree';

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
    minHeight: 38,
    alignItems: 'center',
    gap: 6,
    padding: '3px 8px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  search: {
    display: 'flex',
    flexShrink: 0,
    minHeight: 38,
    alignItems: 'center',
    padding: '4px 8px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  table: {
    minHeight: 0,
    flex: 1,
    overflow: 'hidden',
    '& .ant-table-cell': { padding: '7px 9px !important' },
    '& .ant-table-thead .ant-table-cell': {
      fontWeight: 500,
      background: token.colorFillQuaternary,
    },
    '& .ant-table-row': { cursor: 'default' },
  },
  objectName: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 7,
    fontWeight: 500,
  },
}));

interface Props {
  profile: DataSourceProfile;
  namespace: string;
  objectType: 'TABLE' | 'VIEW';
  onOpenTable: (target: OpenTarget) => void;
  onOpenStructure: (target: OpenTarget) => void;
  onNewQuery: (profile: DataSourceProfile, namespace: string, table: string) => void;
}

const unavailable = (
  <Tooltip title="当前 JDBC 元数据接口未提供该属性">
    <Typography.Text type="secondary">-</Typography.Text>
  </Tooltip>
);

/** 数据库对象列表，对齐 Chat2DB 点击「表 / 视图」后的右侧主视图。 */
export default function ObjectListView({
  profile, namespace, objectType, onOpenTable, onOpenStructure, onNewQuery,
}: Props) {
  const { styles } = useStyles();
  const [objects, setObjects] = useState<TableInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedName, setSelectedName] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(100);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await metadataApi.tables(profile.id, namespace);
      setObjects(data.filter((item) => item.type === objectType));
      setSelectedName(undefined);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [profile.id, namespace, objectType]);

  useEffect(() => {
    setPage(1);
    setKeyword('');
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase();
    if (!normalized) return objects;
    return objects.filter((item) =>
      item.name.toLocaleLowerCase().includes(normalized)
      || (item.remarks ?? '').toLocaleLowerCase().includes(normalized));
  }, [objects, keyword]);
  const pageRows = filtered.slice((page - 1) * pageSize, page * pageSize);
  const selected = objects.find((item) => item.name === selectedName);
  const selectedTarget = selected
    ? { profile, namespace, table: selected.name, type: selected.type } satisfies OpenTarget
    : undefined;

  return (
    <div className={styles.root}>
      <div className={styles.toolbar}>
        <Space size={4}>
          <DatabaseOutlined />
          <Typography.Text strong>{profile.name}</Typography.Text>
          <Typography.Text type="secondary">/</Typography.Text>
          <Typography.Text>{namespace}</Typography.Text>
          <Tag color={objectType === 'TABLE' ? 'blue' : 'cyan'}>{objectType === 'TABLE' ? '表' : '视图'}</Tag>
        </Space>
        <Pagination
          size="small"
          current={page}
          pageSize={pageSize}
          total={filtered.length}
          showSizeChanger
          pageSizeOptions={[50, 100, 200, 500]}
          showTotal={(total) => `总数: ${total}`}
          onChange={(nextPage, nextSize) => {
            setPage(nextSize !== pageSize ? 1 : nextPage);
            setPageSize(nextSize);
          }}
        />
        <Tooltip title="刷新对象列表">
          <Button type="text" size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}>刷新</Button>
        </Tooltip>
        <div style={{ flex: 1 }} />
        <Button
          type="text"
          size="small"
          icon={<TableOutlined />}
          disabled={!selectedTarget}
          onClick={() => selectedTarget && onOpenTable(selectedTarget)}
        >
          打开数据
        </Button>
        <Button
          type="text"
          size="small"
          icon={<UnorderedListOutlined />}
          disabled={!selectedTarget}
          onClick={() => selectedTarget && onOpenStructure(selectedTarget)}
        >
          查看结构
        </Button>
        <Button
          type="text"
          size="small"
          icon={<CodeOutlined />}
          disabled={!selectedTarget}
          onClick={() => selectedTarget && onNewQuery(profile, namespace, selectedTarget.table)}
        >
          查询
        </Button>
      </div>

      <div className={styles.search}>
        <Input
          variant="borderless"
          prefix={<SearchOutlined />}
          placeholder={`搜索${objectType === 'TABLE' ? '表' : '视图'}名称或备注`}
          value={keyword}
          allowClear
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(1);
          }}
        />
      </div>

      <div className={styles.table}>
        <Table
          rowKey="name"
          size="small"
          loading={loading}
          pagination={false}
          dataSource={pageRows}
          scroll={{ x: 980, y: 'calc(100vh - 165px)' }}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedName ? [selectedName] : [],
            onChange: (keys) => setSelectedName(String(keys[0] ?? '')),
            columnWidth: 38,
          }}
          onRow={(record) => ({
            onClick: () => setSelectedName(record.name),
            onDoubleClick: () => onOpenTable({ profile, namespace, table: record.name, type: record.type }),
          })}
          columns={[
            {
              title: objectType === 'TABLE' ? '表名' : '视图名',
              dataIndex: 'name',
              width: 260,
              fixed: 'left',
              render: (name: string) => (
                <span className={styles.objectName}>
                  {objectType === 'TABLE' ? <TableOutlined /> : <EyeOutlined />}
                  {name}
                </span>
              ),
            },
            { title: '行数', width: 120, render: () => unavailable },
            { title: '类型', dataIndex: 'type', width: 120 },
            { title: '引擎', width: 140, render: () => unavailable },
            { title: '数据长度', width: 140, render: () => unavailable },
            { title: '创建时间', width: 180, render: () => unavailable },
            { title: '备注', dataIndex: 'remarks', ellipsis: true, render: (value: string | null) => value || '-' },
          ] as never}
          locale={{ emptyText: keyword ? '没有匹配的数据库对象' : `当前命名空间没有${objectType === 'TABLE' ? '表' : '视图'}` }}
        />
      </div>
    </div>
  );
}
