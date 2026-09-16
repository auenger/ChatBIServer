import { useCallback, useEffect, useState } from 'react';
import {
  Button, Descriptions, Drawer, Input, message, Modal, Popconfirm, Space, Table, Tag, Tooltip, Typography,
} from 'antd';
import { ApiOutlined, CodeOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import { history } from 'umi';
import type { CapabilityDescriptor, DataSourceProfile } from '@/service/api';
import { datasourceApi } from '@/service/api';
import ConnectionForm from './ConnectionForm';

const useStyles = createStyles({
  toolbar: { display: 'flex', justifyContent: 'space-between', marginBottom: 16 },
});

const TYPE_COLORS: Record<string, string> = {
  MYSQL: 'blue',
  SQLSERVER: 'red',
  POSTGRESQL: 'cyan',
  ORACLE: 'orange',
};

export default function DatasourcesPage() {
  const { styles } = useStyles();
  const [profiles, setProfiles] = useState<DataSourceProfile[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [rotateTarget, setRotateTarget] = useState<DataSourceProfile | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [capabilities, setCapabilities] = useState<{ profile: DataSourceProfile; caps: CapabilityDescriptor } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setProfiles(await datasourceApi.list());
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const doTest = async (id: number) => {
    setTestingId(id);
    try {
      const result = await datasourceApi.testStored(id);
      if (result.success) {
        message.success(`连接成功（${result.latencyMs}ms）`);
      } else {
        message.error(result.message);
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setTestingId(null);
    }
  };

  const doRotate = async () => {
    if (!rotateTarget) return;
    try {
      await datasourceApi.rotate(rotateTarget.id, newPassword);
      message.success('凭据已轮换，旧连接池已逐出');
      setRotateTarget(null);
      setNewPassword('');
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const doDelete = async (id: number) => {
    try {
      await datasourceApi.remove(id);
      message.success('已删除');
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const showCapabilities = async (profile: DataSourceProfile) => {
    try {
      const caps = await datasourceApi.capabilities(profile.id);
      setCapabilities({ profile, caps });
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string) => <Typography.Text strong>{name}</Typography.Text>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (type: string) => <Tag color={TYPE_COLORS[type]}>{type}</Tag>,
    },
    { title: '主机', dataIndex: 'host' },
    { title: '端口', dataIndex: 'port', width: 80 },
    { title: '数据库', dataIndex: 'databaseName' },
    { title: '用户名', dataIndex: 'username' },
    {
      title: 'TLS',
      dataIndex: 'sslMode',
      width: 100,
      render: (mode: string) => (
        <Tag color={mode === 'DISABLED' ? 'default' : 'green'}>{mode}</Tag>
      ),
    },
    {
      title: '凭据版本',
      dataIndex: 'credentialVersion',
      width: 90,
      render: (v: number) => <Tag>v{v}</Tag>,
    },
    {
      title: '操作',
      width: 300,
      render: (_: unknown, record: DataSourceProfile) => (
        <Space>
          <Button
            size="small"
            icon={<ApiOutlined />}
            loading={testingId === record.id}
            onClick={() => doTest(record.id)}
          >
            测试
          </Button>
          <Button
            size="small"
            icon={<CodeOutlined />}
            onClick={() => history.push(`/workbench?ds=${record.id}`)}
          >
            工作台
          </Button>
          <Button size="small" icon={<KeyOutlined />} onClick={() => setRotateTarget(record)}>
            轮换
          </Button>
          <Button size="small" type="text" onClick={() => showCapabilities(record)}>
            能力
          </Button>
          <Popconfirm title={`确认删除「${record.name}」？`} onConfirm={() => doDelete(record.id)}>
            <Button size="small" type="text" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className={styles.toolbar}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          数据源
        </Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建数据源
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns as never}
        dataSource={profiles}
        pagination={false}
        size="middle"
      />

      <Drawer
        title="新建数据源"
        width={560}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        destroyOnClose
      >
        <ConnectionForm
          onSaved={() => {
            setCreateOpen(false);
            load();
          }}
        />
      </Drawer>

      <Modal
        title={`轮换凭据 — ${rotateTarget?.name ?? ''}`}
        open={!!rotateTarget}
        onOk={doRotate}
        onCancel={() => setRotateTarget(null)}
        okButtonProps={{ disabled: !newPassword }}
      >
        <Typography.Paragraph type="secondary">
          生成新凭据版本并立即逐出旧连接池；请确保与数据库侧的实际口令一致。
        </Typography.Paragraph>
        <Input.Password
          placeholder="新数据库密码"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
      </Modal>

      <Modal
        title={`能力声明 — ${capabilities?.profile.name ?? ''}`}
        open={!!capabilities}
        footer={null}
        onCancel={() => setCapabilities(null)}
      >
        {capabilities && (
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="数据库层级">
              {capabilities.caps.supportsDatabase ? '支持 database' : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Schema 层级">
              {capabilities.caps.supportsSchema ? '支持 schema' : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="标识符引用符">
              <Tooltip title="用于 SQL 生成时转义标识符">{capabilities.caps.identifierQuote}</Tooltip>
            </Descriptions.Item>
            <Descriptions.Item label="分页风格">{capabilities.caps.paginationStyle}</Descriptions.Item>
            <Descriptions.Item label="探活 SQL">
              <code>{capabilities.caps.probeSql}</code>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
}
