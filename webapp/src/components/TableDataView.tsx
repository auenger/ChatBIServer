import { useCallback, useEffect, useState } from 'react';
import { Select, Space, Typography, message } from 'antd';
import ResultSet from '@/components/ResultSet';
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', minHeight: 38, padding: '0 10px', borderBottom: '1px solid #f0f0f0' }}>
        <Space size={8}>
          <Typography.Text strong>
            {namespace}.{table}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            表数据
          </Typography.Text>
        </Space>
      </div>
      <div style={{ flex: 1, minHeight: 0 }}>
        <ResultSet
          data={pageData?.data ?? { columns: [], rows: [], rowsTruncated: false, columnsTruncated: false }}
          loading={loading}
          onRefresh={load}
          rowNumberOffset={pageData ? (pageData.page - 1) * pageData.size : 0}
          fileName={`${namespace}.${table}`}
          tableHeight="calc(100vh - 245px)"
          statusText={pageData ? `第 ${pageData.page} 页, 共 ${pageData.total} 行` : '正在读取表数据'}
          extraToolbar={(
            <Space size={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>每页</Typography.Text>
              <Select
                size="small"
                value={size}
                style={{ width: 76 }}
                options={[25, 50, 100, 200].map((value) => ({ value, label: value }))}
                onChange={(value) => {
                  setSize(value);
                  setPage(1);
                }}
              />
            </Space>
          )}
          pagination={{
            current: page,
            pageSize: size,
            total: pageData?.total ?? 0,
            showSizeChanger: false,
            onChange: (p) => setPage(p),
          }}
        />
      </div>
    </div>
  );
}
