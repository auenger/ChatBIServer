import { Alert, Space, Table, Tag, Typography } from 'antd';
import type { QueryResultData } from '@/service/api';

/** 查询结果集表格（阶段 1 用 antd Table；VTable 虚拟化随大数据量需求后续接入）。 */
export default function ResultSet({ data }: { data: QueryResultData }) {
  const columns = [
    { title: '#', width: 56, render: (_: unknown, __: Record<string, unknown>, i: number) => i + 1 },
    ...data.columns.map((c) => ({ title: c, dataIndex: `c${c}`, ellipsis: true })),
  ];
  const rows = data.rows.map((row, i) => {
    const item: Record<string, unknown> = { key: i };
    row.forEach((cell, j) => {
      item[`c${data.columns[j]}`] = cell;
    });
    return item;
  });

  return (
    <div>
      {(data.rowsTruncated || data.columnsTruncated) && (
        <Alert
          style={{ marginBottom: 8 }}
          type="warning"
          showIcon={false}
          message={
            <Space>
              {data.rowsTruncated && <Tag color="orange">行数已达上限，结果被截断</Tag>}
              {data.columnsTruncated && <Tag color="orange">列数超限，部分列未显示</Tag>}
            </Space>
          }
        />
      )}
      <Table
        rowKey="key"
        size="small"
        columns={columns as never}
        dataSource={rows}
        scroll={{ x: 'max-content' }}
        pagination={{ pageSize: 50, size: 'small', showSizeChanger: false }}
        locale={{ emptyText: '无数据行' }}
        footer={() => (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {data.rows.length} 行 × {data.columns.length} 列
          </Typography.Text>
        )}
      />
    </div>
  );
}
