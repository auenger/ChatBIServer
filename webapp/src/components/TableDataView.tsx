import { useCallback, useEffect, useState } from 'react';
import { Button, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { DataSourceProfile, TableDataPage } from '@/service/api';
import { metadataApi } from '@/service/api';

/**
 * 表数据网格（对齐 Chat2DB「打开表」视图）：服务端分页 + 精确总行数 + 状态条。
 */
interface Props {
  profile: DataSourceProfile;
  namespace: string;
  table: string;
}

export default function TableDataView({ profile, namespace, table }: Props) {
  const [pageData, setPageData] = useState<TableDataPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPageData(await metadataApi.tableData(profile.id, namespace, table, page, size));
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [profile.id, namespace, table, page, size]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = pageData
    ? [
        {
          title: '#',
          width: 64,
          fixed: 'left' as const,
          render: (_: unknown, __: Record<string, unknown>, i: number) => (pageData.page - 1) * pageData.size + i + 1,
        },
        ...pageData.data.columns.map((c) => ({
          title: c,
          dataIndex: `c${c}`,
          ellipsis: true,
        })),
      ]
    : [];

  const rows = (pageData?.data.rows ?? []).map((row, i) => {
    const item: Record<string, unknown> = { key: i };
    row.forEach((cell, j) => {
      item[`c${pageData!.data.columns[j]}`] = cell;
    });
    return item;
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Space size={8}>
          <Typography.Text strong>
            {namespace}.{table}
          </Typography.Text>
          {pageData && <Tag>{pageData.total} 行</Tag>}
        </Space>
        <Space>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            每页
          </Typography.Text>
          <select
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value));
              setPage(1);
            }}
            style={{ padding: '2px 6px', borderRadius: 6, border: '1px solid #ddd' }}
          >
            {[25, 50, 100, 200].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          <Button size="small" icon={<ReloadOutlined />} onClick={load} loading={loading}>
            刷新
          </Button>
        </Space>
      </div>

      {loading && !pageData ? (
        <Spin style={{ margin: '80px auto', display: 'block' }} />
      ) : (
        <Table
          rowKey="key"
          size="small"
          loading={loading}
          columns={columns as never}
          dataSource={rows}
          scroll={{ x: 'max-content', y: 'calc(100vh - 300px)' }}
          pagination={{
            current: page,
            pageSize: size,
            total: pageData?.total ?? 0,
            showSizeChanger: false,
            showTotal: (t) => `共 ${t} 行（精确计数）`,
            onChange: (p) => setPage(p),
          }}
        />
      )}
    </div>
  );
}
