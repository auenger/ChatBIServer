import { useCallback, useEffect, useState } from 'react';
import {
  Button, Collapse, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography,
} from 'antd';
import { ApiOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import type { ModelProtocol, ModelProviderSave, ModelProviderView } from '@/service/api';
import { modelProviderApi, modelProviderApiExtra } from '@/service/api';

const useStyles = createStyles({
  toolbar: { display: 'flex', justifyContent: 'space-between', marginBottom: 16 },
});

const PROTOCOL_LABEL: Record<ModelProtocol, string> = {
  OPENAI_COMPATIBLE: 'OpenAI 兼容',
  ANTHROPIC: 'Anthropic',
};

/** 预设模板（借鉴 dsh ProviderEditor 的 baseUrl 占位/协议选择思想）：一键填接入点。 */
const PRESETS: Array<{ key: string; label: string; protocol: ModelProtocol; baseUrl: string; model: string }> = [
  { key: 'openai', label: 'OpenAI 官方', protocol: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.openai.com', model: 'gpt-4o-mini' },
  { key: 'deepseek', label: 'DeepSeek', protocol: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { key: 'anthropic', label: 'Anthropic 官方', protocol: 'ANTHROPIC', baseUrl: 'https://api.anthropic.com', model: 'claude-sonnet-5' },
  { key: 'custom', label: '自定义网关', protocol: 'OPENAI_COMPATIBLE', baseUrl: '', model: '' },
];

interface FormValues {
  name: string;
  protocol: ModelProtocol;
  baseUrl: string;
  model: string;
  apiKey?: string;
  temperature?: number;
  maxTokens?: number;
  enabled: boolean;
}

/** 模型设置页（0.1，借鉴 dsh ui-settings-models）：CRUD / 启停 / 连通测试 / 预设模板 / 模型列表拉取。 */
export default function ModelProvidersPage() {
  const { styles } = useStyles();
  const [providers, setProviders] = useState<ModelProviderView[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ModelProviderView | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [fetchingModels, setFetchingModels] = useState(false);
  const [modelOptions, setModelOptions] = useState<string[]>([]);
  const [form] = Form.useForm<FormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setProviders(await modelProviderApi.list());
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const applyPreset = (key: string) => {
    const preset = PRESETS.find((p) => p.key === key);
    if (!preset) return;
    form.setFieldsValue({ protocol: preset.protocol, baseUrl: preset.baseUrl, model: preset.model || '' });
    setModelOptions([]);
  };

  const doFetchModels = async () => {
    const { protocol, baseUrl, apiKey } = form.getFieldsValue();
    if (!protocol || !baseUrl) {
      message.warning('请先选择协议并填写接入点');
      return;
    }
    setFetchingModels(true);
    try {
      const r = await modelProviderApiExtra.previewModels({
        protocol, baseUrl, apiKey, providerId: editing?.id,
      });
      if (r.models.length === 0) {
        message.warning('该接入点未返回模型列表，请手动填写模型名');
      } else {
        setModelOptions(r.models);
        message.success(`获取到 ${r.models.length} 个模型`);
      }
    } catch (e) {
      message.error(`获取模型列表失败: ${(e as Error).message}`);
    } finally {
      setFetchingModels(false);
    }
  };

  const openCreate = () => {
    setEditing(null);
    setModelOptions([]);
    form.resetFields();
    form.setFieldsValue({ protocol: 'OPENAI_COMPATIBLE', enabled: true });
    setCreateOpen(true);
  };

  const openEdit = (p: ModelProviderView) => {
    setEditing(p);
    setModelOptions([]);
    form.resetFields();
    form.setFieldsValue({
      name: p.name,
      protocol: p.protocol,
      baseUrl: p.baseUrl,
      model: p.model,
      temperature: typeof p.params.temperature === 'number' ? p.params.temperature : undefined,
      maxTokens: typeof p.params.maxTokens === 'number' ? p.params.maxTokens : undefined,
      enabled: p.enabled,
    });
    setCreateOpen(true);
  };

  const doSave = async () => {
    const v = await form.validateFields();
    const params: Record<string, unknown> = {};
    if (v.temperature !== undefined) params.temperature = v.temperature;
    if (v.maxTokens !== undefined) params.maxTokens = v.maxTokens;
    const body: ModelProviderSave = {
      name: v.name,
      protocol: v.protocol,
      baseUrl: v.baseUrl,
      model: v.model,
      enabled: v.enabled,
      params,
    };
    if (v.apiKey) body.apiKey = v.apiKey;
    try {
      if (editing) {
        await modelProviderApi.update(editing.id, body);
        message.success('已保存');
      } else {
        await modelProviderApi.create(body);
        message.success('已登记');
      }
      setCreateOpen(false);
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const doTest = async (id: number) => {
    setTestingId(id);
    try {
      const r = await modelProviderApi.test(id);
      if (r.success) {
        message.success(`连通正常（${r.latencyMs}ms）`);
      } else {
        message.error(`连通失败: ${r.message}`);
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setTestingId(null);
    }
  };

  const doToggle = async (p: ModelProviderView, enabled: boolean) => {
    try {
      await modelProviderApi.update(p.id, {
        name: p.name, protocol: p.protocol, baseUrl: p.baseUrl, model: p.model,
        params: p.params, enabled,
      });
      load();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const protocol = Form.useWatch('protocol', form);

  return (
    <div style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <div className={styles.toolbar}>
        <Typography.Title level={4} style={{ margin: 0 }}>模型设置</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>登记模型</Button>
        </Space>
      </div>
      <Table<ModelProviderView>
        rowKey="id"
        loading={loading}
        dataSource={providers}
        pagination={false}
        columns={[
          { title: '名称', dataIndex: 'name' },
          {
            title: '协议', dataIndex: 'protocol', width: 130,
            render: (p: ModelProtocol) => <Tag color={p === 'ANTHROPIC' ? 'orange' : 'green'}>{PROTOCOL_LABEL[p]}</Tag>,
          },
          { title: '接入点', dataIndex: 'baseUrl', ellipsis: true },
          { title: '模型', dataIndex: 'model', width: 180 },
          {
            title: '密钥', width: 110,
            render: () => <Typography.Text type="secondary">已加密存储</Typography.Text>,
          },
          {
            title: '启用', dataIndex: 'enabled', width: 80,
            render: (_: boolean, p) => <Switch size="small" checked={p.enabled} onChange={(v) => doToggle(p, v)} />,
          },
          {
            title: '操作', width: 200,
            render: (_: unknown, p) => (
              <Space>
                <Button size="small" icon={<ApiOutlined />} loading={testingId === p.id} onClick={() => doTest(p.id)}>
                  测试
                </Button>
                <Button size="small" onClick={() => openEdit(p)}>编辑</Button>
                <Popconfirm title="删除该模型配置？" onConfirm={async () => { await modelProviderApi.remove(p.id); load(); }}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing ? `编辑：${editing.name}` : '登记模型'}
        open={createOpen}
        onOk={doSave}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
      >
        <div style={{ marginBottom: 12 }}>
          <Typography.Text type="secondary" style={{ marginRight: 8 }}>快速预设：</Typography.Text>
          {PRESETS.map((p) => (
            <Tag.CheckableTag key={p.key} checked={false} onChange={() => applyPreset(p.key)}>
              {p.label}
            </Tag.CheckableTag>
          ))}
        </div>
        <Form<FormValues> form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="如 openai-main / deepseek / claude" />
          </Form.Item>
          <Form.Item name="protocol" label="协议" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容（含各兼容网关）' },
                { value: 'ANTHROPIC', label: 'Anthropic 原生' },
              ]}
              onChange={() => setModelOptions([])}
            />
          </Form.Item>
          <Form.Item name="baseUrl" label="接入点 Base URL" rules={[{ required: true }]}>
            <Input placeholder={protocol === 'ANTHROPIC' ? 'https://api.anthropic.com' : 'https://api.openai.com 或自定义网关'} />
          </Form.Item>
          <Form.Item name="model" label="模型名" rules={[{ required: true }]}>
            {modelOptions.length > 0 ? (
              <Select
                showSearch
                placeholder="从该接入点获取到的模型列表"
                options={modelOptions.map((m) => ({ value: m, label: m }))}
              />
            ) : (
              <Input placeholder="gpt-4o-mini / deepseek-chat / claude-sonnet-5 …" />
            )}
          </Form.Item>
          <Button size="small" loading={fetchingModels} onClick={doFetchModels} style={{ marginBottom: 16 }}>
            从接入点获取模型列表
          </Button>
          <Form.Item
            name="apiKey"
            label={editing ? 'API Key（留空 = 保留原密钥）' : 'API Key'}
            rules={editing ? [] : [{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder={editing ? '••••••••' : 'sk-…'} autoComplete="new-password" />
          </Form.Item>
          <Collapse
            ghost
            items={[{
              key: 'advanced',
              label: '高级参数',
              children: (
                <Space size="large">
                  <Form.Item name="temperature" label="Temperature">
                    <InputNumber min={0} max={2} step={0.1} style={{ width: 120 }} />
                  </Form.Item>
                  <Form.Item name="maxTokens" label="Max Tokens">
                    <InputNumber min={64} max={32768} style={{ width: 120 }} />
                  </Form.Item>
                  <Form.Item name="enabled" label="启用" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space>
              ),
            }]}
          />
        </Form>
      </Modal>
    </div>
  );
}
