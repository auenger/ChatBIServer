import { useMemo, useState } from 'react';
import { Button, Input, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { CopyOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { TablePaginationConfig } from 'antd/es/table';
import { createStyles } from 'antd-style';
import type { QueryResultData } from '@/service/api';

const useStyles = createStyles(({ token }) => ({
  root: {
    display: 'flex',
    minHeight: 0,
    height: '100%',
    flexDirection: 'column',
    border: `1px solid ${token.colorBorderSecondary}`,
    background: token.colorBgContainer,
  },
  toolbar: {
    display: 'flex',
    flexShrink: 0,
    minHeight: 34,
    alignItems: 'center',
    gap: 4,
    padding: '2px 6px',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
  },
  table: {
    flex: 1,
    minHeight: 0,
    overflow: 'hidden',
    '& .ant-table-cell': {
      padding: '5px 8px !important',
      whiteSpace: 'nowrap',
    },
    '& .ant-table-thead .ant-table-cell': {
      fontWeight: 500,
      background: token.colorFillQuaternary,
    },
    '& .ant-table-row-selected .ant-table-cell': {
      background: `${token.colorPrimaryBg} !important`,
    },
  },
  status: {
    display: 'flex',
    flexShrink: 0,
    height: 27,
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 8px',
    borderTop: `1px solid ${token.colorBorderSecondary}`,
    background: token.colorFillQuaternary,
    color: token.colorTextSecondary,
    fontSize: 12,
    whiteSpace: 'nowrap',
  },
  nullValue: {
    color: token.colorTextQuaternary,
    fontSize: 11,
    fontStyle: 'italic',
  },
}));

interface ResultSetProps {
  data: QueryResultData;
  loading?: boolean;
  pagination?: false | TablePaginationConfig;
  rowNumberOffset?: number;
  tableHeight?: number | string;
  onRefresh?: () => void;
  extraToolbar?: React.ReactNode;
  statusText?: string;
  fileName?: string;
}

function textValue(value: string | null) {
  return value === null ? '' : value;
}

function csvCell(value: string | null) {
  const normalized = value === null ? '' : String(value);
  return `"${normalized.replace(/"/g, '""')}"`;
}

/**
 * Chat2DB 风格结果集：紧凑工具栏、可搜索数据网格、行选择与底部状态栏。
 * 仅处理当前 Java API 已返回的数据，不在浏览器端扩张查询权限或重新访问数据库。
 */
export default function ResultSet({
  data,
  loading = false,
  pagination,
  rowNumberOffset = 0,
  tableHeight = 'calc(100vh - 410px)',
  onRefresh,
  extraToolbar,
  statusText,
  fileName = 'query-result',
}: ResultSetProps) {
  const { styles } = useStyles();
  const [keyword, setKeyword] = useState('');
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const allRows = useMemo(
    () => data.rows.map((row, index) => ({ key: index, values: row })),
    [data.rows],
  );
  const rows = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase();
    if (!normalized) return allRows;
    return allRows.filter((row) => row.values.some((value) => textValue(value).toLocaleLowerCase().includes(normalized)));
  }, [allRows, keyword]);

  const columns = useMemo(
    () => [
      {
        title: '#',
        width: 58,
        fixed: 'left' as const,
        render: (_: unknown, row: { key: number }) => rowNumberOffset + row.key + 1,
      },
      ...data.columns.map((name, columnIndex) => ({
        title: <Tooltip title={name}><span>{name}</span></Tooltip>,
        key: `column-${columnIndex}`,
        width: Math.min(360, Math.max(120, name.length * 12 + 44)),
        ellipsis: true,
        render: (_: unknown, row: { values: (string | null)[] }) => {
          const value = row.values[columnIndex];
          return value === null ? (
            <span className={styles.nullValue}>NULL</span>
          ) : (
            <Tooltip mouseEnterDelay={0.6} title={value.length > 80 ? value : undefined}>
              <span>{value}</span>
            </Tooltip>
          );
        },
      })),
    ],
    [data.columns, rowNumberOffset, styles.nullValue],
  );

  const selectedRows = allRows.filter((row) => selectedRowKeys.includes(row.key));

  const copyRows = async () => {
    if (!selectedRows.length) return;
    const tsv = [data.columns.join('\t'), ...selectedRows.map((row) => row.values.map(textValue).join('\t'))].join('\n');
    try {
      await navigator.clipboard.writeText(tsv);
      message.success(`已复制 ${selectedRows.length} 行`);
    } catch {
      message.error('复制失败，请检查浏览器剪贴板权限');
    }
  };

  const exportCsv = () => {
    const csv = [
      data.columns.map((value) => csvCell(value)).join(','),
      ...rows.map((row) => row.values.map(csvCell).join(',')),
    ].join('\r\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${fileName}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className={styles.root}>
      <div className={styles.toolbar}>
        {extraToolbar}
        {onRefresh && <Button type="text" size="small" onClick={onRefresh}>刷新</Button>}
        <Input
          allowClear
          size="small"
          prefix={<SearchOutlined />}
          placeholder="在当前结果中搜索"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          style={{ width: 220 }}
        />
        <div style={{ flex: 1 }} />
        {(data.rowsTruncated || data.columnsTruncated) && (
          <Space size={4}>
            {data.rowsTruncated && <Tag color="orange">行已截断</Tag>}
            {data.columnsTruncated && <Tag color="orange">列已截断</Tag>}
          </Space>
        )}
        <Tooltip title={selectedRows.length ? `复制选中的 ${selectedRows.length} 行` : '选择行后复制'}>
          <Button type="text" size="small" icon={<CopyOutlined />} disabled={!selectedRows.length} onClick={copyRows} />
        </Tooltip>
        <Tooltip title="导出当前筛选结果为 CSV">
          <Button type="text" size="small" icon={<DownloadOutlined />} onClick={exportCsv} />
        </Tooltip>
      </div>

      <div className={styles.table}>
        <Table
          rowKey="key"
          size="small"
          bordered
          loading={loading}
          columns={columns as never}
          dataSource={rows}
          rowSelection={{ fixed: true, selectedRowKeys, onChange: setSelectedRowKeys, columnWidth: 42 }}
          scroll={{ x: 'max-content', y: tableHeight }}
          pagination={pagination === undefined ? { pageSize: 50, size: 'small', showSizeChanger: false } : pagination}
          locale={{ emptyText: keyword ? '没有匹配的数据' : '无数据行' }}
        />
      </div>

      <div className={styles.status}>
        <span>{statusText ?? `${data.rows.length} 行, ${data.columns.length} 列`}</span>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {selectedRows.length ? `已选择 ${selectedRows.length} 行` : keyword ? `筛选后 ${rows.length} 行` : '未选择行'}
        </Typography.Text>
      </div>
    </div>
  );
}
