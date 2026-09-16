import { useMemo, useState } from 'react';
import { Alert, Button, Col, Form, Input, InputNumber, message, Row, Select, Typography } from 'antd';
import { ApiOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import type { DatasourcePayload, DatabaseType, SslMode } from '@/service/api';
import { datasourceApi } from '@/service/api';
import { buildJdbcUrlPreview, DATABASE_TYPES, SSL_MODES, typeInfo } from './config';

// 新建数据源表单：配置驱动 + 独立测试连接 loading（借鉴 Chat2DB ConnectionEdit 三态 loading 模式）
const useStyles = createStyles(({ token }) => ({
  typeCard: {
    flex: 1,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadius,
    padding: '10px 4px',
    textAlign: 'center',
    cursor: 'pointer',
    transition: `all ${token.motionDurationMid}`,
    fontWeight: 600,
    '&:hover': { borderColor: token.colorPrimary },
  },
  typeCardActive: {
    borderColor: token.colorPrimary,
    color: token.colorPrimary,
    boxShadow: `0 0 0 1px ${token.colorPrimary}`,
  },
  footer: {
    display: 'flex',
    justifyContent: 'space-between',
    marginTop: 8,
  },
}));

interface Values {
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  sslMode: SslMode;
}

export default function ConnectionForm({ onSaved }: { onSaved: () => void }) {
  const { styles } = useStyles();
  const [form] = Form.useForm<Values>();
  const [type, setType] = useState<DatabaseType>('MYSQL');
  const [testLoading, setTestLoading] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);

  const info = useMemo(() => typeInfo(type), [type]);
  const values = Form.useWatch([], form);
  const urlPreview = useMemo(
    () =>
      buildJdbcUrlPreview(
        type,
        values?.host ?? '',
        values?.port ?? info.defaultPort,
        values?.databaseName ?? '',
        values?.sslMode ?? 'DISABLED',
      ),
    [type, values],
  );

  const pickType = (t: DatabaseType) => {
    setType(t);
    form.setFieldValue('port', typeInfo(t).defaultPort);
  };

  const toPayload = (): DatasourcePayload => {
    const v = form.getFieldsValue();
    return {
      name: v.name,
      type,
      host: v.host,
      port: v.port,
      databaseName: v.databaseName ?? '',
      username: v.username,
      password: v.password,
      sslMode: v.sslMode ?? 'DISABLED',
    };
  };

  const doTest = async () => {
    setTestLoading(true);
    try {
      const result = await datasourceApi.testTemporary(toPayload());
      if (result.success) {
        message.success(`连接成功（${result.latencyMs}ms）`);
      } else {
        message.error(result.message);
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setTestLoading(false);
    }
  };

  const doSave = async () => {
    setSaveLoading(true);
    try {
      await form.validateFields();
      await datasourceApi.create(toPayload());
      message.success('数据源已登记');
      onSaved();
    } catch (e) {
      if ((e as Error).name !== 'ValidationError') {
        message.error((e as Error).message);
      }
    } finally {
      setSaveLoading(false);
    }
  };

  return (
    <Form form={form} layout="vertical" initialValues={{ port: info.defaultPort, sslMode: 'DISABLED' }}>
      <Form.Item label="数据库类型" style={{ marginBottom: 16 }}>
        <Row gutter={8}>
          {DATABASE_TYPES.map((t) => (
            <Col key={t.type} span={6}>
              <div
                className={`${styles.typeCard} ${type === t.type ? styles.typeCardActive : ''}`}
                onClick={() => pickType(t.type)}
              >
                {t.label}
              </div>
            </Col>
          ))}
        </Row>
      </Form.Item>

      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="name" label="数据源名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="唯一名称，如 生产订单库" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="host" label="主机" rules={[{ required: true, message: '请输入主机' }]}>
            <Input placeholder="127.0.0.1" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="port" label="端口" rules={[{ required: true, message: '请输入端口' }]}>
            <InputNumber style={{ width: '100%' }} min={1} max={65535} />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={12}>
        <Col span={12}>
          <Form.Item
            name="databaseName"
            label={info.databaseLabel}
            rules={[{ required: type !== 'ORACLE', message: `请输入${info.databaseLabel}` }]}
          >
            <Input placeholder={info.databasePlaceholder} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="sslMode" label="TLS 策略" tooltip="禁止连接失败后自动降级；由该策略显式决定">
            <Select options={SSL_MODES} />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="username" label="数据库用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="password" label="数据库密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Col>
      </Row>

      <Typography.Paragraph type="secondary" copyable style={{ fontSize: 12, marginBottom: 16 }}>
        JDBC URL 预览：{urlPreview}
      </Typography.Paragraph>

      <div className={styles.footer}>
        <Button icon={<ApiOutlined />} loading={testLoading} onClick={doTest}>
          测试连接
        </Button>
        <Button type="primary" icon={<ThunderboltOutlined />} loading={saveLoading} onClick={doSave}>
          保存
        </Button>
      </div>
      <Alert
        style={{ marginTop: 12 }}
        type="info"
        showIcon={false}
        message="密码经 AES-256-GCM 加密后入库，接口不回显；登记后可在列表中轮换凭据。"
      />
    </Form>
  );
}
