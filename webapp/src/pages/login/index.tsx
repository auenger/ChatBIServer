import { useState } from 'react';
import { Button, Card, Form, Input, message, Typography } from 'antd';
import { createStyles } from 'antd-style';
import { history } from 'umi';
import { authApi, } from '@/service/api';
import { clearSession, savedUsername, saveSession } from '@/service/base';

const useStyles = createStyles(({ token }) => ({
  wrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: `linear-gradient(160deg, ${token.colorPrimaryBg} 0%, #f5f6f8 55%)`,
  },
  card: {
    width: 380,
    boxShadow: token.boxShadowSecondary,
    borderRadius: token.borderRadiusLG,
  },
  title: {
    textAlign: 'center',
    marginBottom: 4,
  },
  subtitle: {
    textAlign: 'center',
    color: token.colorTextSecondary,
    marginBottom: 24,
  },
}));

export default function LoginPage() {
  const { styles } = useStyles();
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const result = await authApi.login(values.username, values.password);
      saveSession(result.token, result.username);
      message.success('登录成功');
      history.push('/datasources');
    } catch (e) {
      message.error((e as Error).message || '登录失败');
      clearSession();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.wrapper}>
      <Card className={styles.card}>
        <Typography.Title level={3} className={styles.title}>
          MEPER ChatBI
        </Typography.Title>
        <Typography.Paragraph className={styles.subtitle}>
          以服务端为数据库能力主体的 ChatBI
        </Typography.Paragraph>
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ username: savedUsername() ?? '' }}
        >
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="用户名" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登 录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
