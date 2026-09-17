import { useEffect, useState } from 'react';
import { Descriptions, Spin, Table, Tabs, Tag, Typography, message } from 'antd';
import { KeyOutlined } from '@ant-design/icons';
import type { DataSourceProfile, TableDetail } from '@/service/api';
import { metadataApi } from '@/service/api';

/**
 * 表结构视图（对齐 Chat2DB「表信息」）：基本信息 + 列清单 + 索引清单。
 */
interface Props {
  profile: DataSourceProfile;
  namespace: string;
  table: string;
}

export default function TableStructureView({ profile, namespace, table }: Props) {
  const [detail, setDetail] = useState<TableDetail | null>(null);

  useEffect(() => {
    let alive = true;
    metadataApi
      .tableDetail(profile.id, namespace, table)
      .then((d) => alive && setDetail(d))
      .catch((e) => alive && message.error((e as Error).message));
    return () => {
      alive = false;
    };
  }, [profile.id, namespace, table]);

  if (!detail) {
    return <Spin style={{ margin: '80px auto', display: 'block' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', minHeight: 38, padding: '0 10px', borderBottom: '1px solid #f0f0f0' }}>
        <Typography.Text strong>{namespace}.{table}</Typography.Text>
        <Tag style={{ marginLeft: 8 }} color={detail.type === 'VIEW' ? 'cyan' : 'green'}>{detail.type}</Tag>
      </div>

      <Descriptions size="small" column={4} bordered style={{ margin: 10 }}>
        <Descriptions.Item label="列数">{detail.columns.length}</Descriptions.Item>
        <Descriptions.Item label="索引数">{detail.indexes.length}</Descriptions.Item>
        <Descriptions.Item label="备注" span={2}>
          {detail.remarks || '-'}
        </Descriptions.Item>
      </Descriptions>

      <Tabs
        size="small"
        style={{ flex: 1, minHeight: 0, padding: '0 10px' }}
        items={[
          {
            key: 'columns',
            label: `列 (${detail.columns.length})`,
            children: <Table
              rowKey="name"
              size="small"
              bordered
              pagination={false}
              scroll={{ x: 'max-content', y: 'calc(100vh - 245px)' }}
              dataSource={detail.columns}
              columns={[
          {
            title: '#',
            width: 48,
            render: (_: unknown, __: unknown, i: number) => i + 1,
          },
          {
            title: '列名',
            dataIndex: 'name',
            render: (name: string, row) => (
              <span>
                {row.primaryKey && (
                  <KeyOutlined style={{ color: '#faad14', marginRight: 6 }} title="主键" />
                )}
                <Typography.Text strong={row.primaryKey}>{name}</Typography.Text>
              </span>
            ),
          },
          { title: '类型', dataIndex: 'typeName', width: 140 },
          {
            title: '可空',
            dataIndex: 'nullable',
            width: 80,
            render: (v: boolean) => (v ? <Tag>NULL</Tag> : <Tag color="blue">NOT NULL</Tag>),
          },
          {
            title: '默认值',
            dataIndex: 'defaultValue',
            width: 140,
            render: (v: string | null) => v ?? '-',
          },
          {
            title: '自增',
            dataIndex: 'autoIncrement',
            width: 72,
            render: (v: boolean) => (v ? <Tag color="purple">AI</Tag> : '-'),
          },
          { title: '备注', dataIndex: 'remarks', ellipsis: true, render: (v: string | null) => v || '-' },
              ] as never}
            />,
          },
          {
            key: 'indexes',
            label: `索引 (${detail.indexes.length})`,
            children: detail.indexes.length === 0 ? (
              <Typography.Text type="secondary">无索引</Typography.Text>
            ) : <Table
              rowKey="name"
              size="small"
              bordered
              pagination={false}
              dataSource={detail.indexes}
              columns={[
            { title: '索引名', dataIndex: 'name' },
            {
              title: '唯一',
              dataIndex: 'unique',
              width: 90,
              render: (v: boolean) => (v ? <Tag color="gold">UNIQUE</Tag> : <Tag>普通</Tag>),
            },
            {
              title: '列',
              dataIndex: 'columns',
              render: (cols: string[]) => cols.join(', '),
            },
              ] as never}
            />,
          },
        ]}
      />
    </div>
  );
}
