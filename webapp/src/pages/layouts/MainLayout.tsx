import { useEffect } from 'react';
import { Avatar, Layout, Menu, Popconfirm, Space, Tooltip, Typography } from 'antd';
import { CodeOutlined, DatabaseOutlined, LogoutOutlined } from '@ant-design/icons';
import { createStyles } from 'antd-style';
import { Outlet, history, useLocation } from 'umi';
import { authApi } from '@/service/api';
import { clearSession, getToken, savedUsername } from '@/service/base';
import useAuthModel from '@/models/auth';

// 左侧 icon 竖导航布局（借鉴 Chat2DB GlobalLayout 的导航模式，简化为两页）
const useStyles = createStyles(({ token }) => ({
  sider: {
    background: token.colorBgContainer,
    borderRight: `1px solid ${token.colorBorderSecondary}`,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: '12px 0',
    gap: 8,
  },
  logo: {
    width: 40,
    height: 40,
    borderRadius: token.borderRadiusLG,
    background: token.colorPrimary,
    color: '#fff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 700,
    marginBottom: 12,
  },
  content: {
    padding: 20,
    overflow: 'auto',
    height: '100vh',
  },
}));

export default function MainLayout() {
  const { styles } = useStyles();
  const location = useLocation();
  const auth = useAuthModel();

  useEffect(() => {
    if (!getToken()) {
      history.push('/login');
    } else if (!auth.username) {
      auth.bootstrap(savedUsername());
    }
  }, []);

  const selectedKey = location.pathname.startsWith('/workbench') ? '/workbench' : '/datasources';

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 忽略登出接口错误，本地清理即可
    }
    auth.clear();
    history.push('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider width={64} className={styles.sider} trigger={null} collapsible={false}>
        <div className={styles.logo}>M</div>
        <Menu
          mode="inline"
          inlineCollapsed
          selectedKeys={[selectedKey]}
          style={{ borderInlineEnd: 'none' }}
          items={[
            { key: '/datasources', icon: <DatabaseOutlined />, label: '数据源' },
            { key: '/workbench', icon: <CodeOutlined />, label: '工作台' },
          ]}
          onClick={({ key }) => history.push(key)}
        />
        <div style={{ flex: 1 }} />
        <Tooltip title={auth.username ?? ''}>
          <Avatar style={{ background: '#1677ff' }}>{(auth.username ?? '?').slice(0, 1).toUpperCase()}</Avatar>
        </Tooltip>
        <Popconfirm title="退出登录？" onConfirm={logout}>
          <LogoutOutlined style={{ marginTop: 8, cursor: 'pointer' }} />
        </Popconfirm>
      </Layout.Sider>
      <Layout.Content className={styles.content}>
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
