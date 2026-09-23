import { useEffect } from 'react';
import { Button, ConfigProvider, Popconfirm, Tooltip } from 'antd';
import {
  CodeOutlined, CommentOutlined, DatabaseOutlined, LogoutOutlined, RobotOutlined,
} from '@ant-design/icons';
import { createStyles } from 'antd-style';
import { Outlet, history, useLocation } from 'umi';
import { authApi } from '@/service/api';
import { clearSession, getToken, savedUsername } from '@/service/base';
import useAuthModel from '@/models/auth';

// 布局与配色复刻 AILiteWMS（frontend/src/styles.css 的 JA 主题）：
// 深色顶栏 + 白色侧栏（红色选中指示）+ 浅灰内容区，品牌主色 #e82127
const useStyles = createStyles(({ token }) => ({
  shell: { minHeight: '100vh', display: 'flex', flexDirection: 'column' },
  topbar: {
    position: 'sticky', top: 0, zIndex: 40, height: 58, flexShrink: 0,
    display: 'flex', alignItems: 'center', gap: 16, padding: '0 20px',
    color: '#f6f7f8', background: '#111318', borderBottom: '1px solid #2b2e34',
  },
  brandBlock: { display: 'flex', alignItems: 'center', gap: 10, minWidth: 190 },
  brandMark: {
    width: 32, height: 32, display: 'grid', placeItems: 'center',
    color: '#fff', background: '#e82127', borderRadius: '50%', fontWeight: 700,
  },
  brandCopy: {
    '& strong': { display: 'block', fontSize: 15, letterSpacing: '.4px' },
    '& small': { display: 'block', color: '#90949d', fontSize: 10, marginTop: 1 },
  },
  topbarSpacer: { flex: 1 },
  userChip: {
    display: 'flex', alignItems: 'center', gap: 9, padding: '3px 6px',
    color: 'inherit', background: 'transparent', border: 0, borderRadius: 6, cursor: 'default',
    '&:hover': { background: 'rgba(255, 255, 255, .06)' },
    '& strong': { display: 'block', fontSize: 12, lineHeight: 1.2 },
    '& small': { display: 'block', color: '#8f939b', fontSize: 10, marginTop: 3 },
  },
  avatar: {
    width: 30, height: 30, display: 'grid', placeItems: 'center', borderRadius: '50%',
    color: '#fff', background: '#e82127', fontSize: 12, fontWeight: 700,
  },
  logout: {
    border: '1px solid rgba(255, 255, 255, .12)', color: '#eef0f3', background: 'rgba(255, 255, 255, .05)',
    borderRadius: 6, height: 34, display: 'inline-flex', alignItems: 'center', gap: 6, padding: '0 12px',
    '&:hover': { background: 'rgba(255, 255, 255, .1) !important', color: '#eef0f3 !important' },
  },
  body: { display: 'flex', flex: 1, minHeight: 0 },
  sidebar: {
    width: 222, flexShrink: 0, background: '#fff', borderRight: '1px solid #e3e5e8',
    paddingTop: 12, paddingBottom: 20,
  },
  navItem: {
    width: '100%', height: 40, display: 'flex', alignItems: 'center', gap: 12, padding: '0 18px',
    color: '#555a63', background: 'transparent', border: 0, borderLeft: '3px solid transparent',
    cursor: 'pointer', fontSize: 14, textAlign: 'left', whiteSpace: 'nowrap',
    '&:hover': { color: '#181b21', background: '#f5f5f6' },
  },
  navItemIcon: { fontSize: 16, flex: '0 0 18px' },
  navItemActive: {
    color: '#e82127', background: '#f9f1f2', borderLeftColor: '#e82127', fontWeight: 600,
  },
  content: {
    flex: 1, minWidth: 0, padding: '26px 32px 48px', background: '#f4f5f6', overflow: 'auto',
    height: 'calc(100vh - 58px)',
  },
  contentFullHeight: { padding: 0, overflow: 'hidden' },
}));

const NAV_ITEMS = [
  { key: '/datasources', icon: <DatabaseOutlined />, label: '数据源' },
  { key: '/workspace', icon: <CodeOutlined />, label: '工作台' },
  { key: '/agentchat', icon: <CommentOutlined />, label: 'AI 对话' },
  { key: '/modelproviders', icon: <RobotOutlined />, label: '模型设置' },
];

export default function MainLayout() {
  const { styles, cx } = useStyles();
  const location = useLocation();
  const auth = useAuthModel();

  useEffect(() => {
    if (!getToken()) {
      history.push('/login');
    } else if (!auth.username) {
      auth.bootstrap(savedUsername());
    }
  }, []);

  const isFullHeight = location.pathname.startsWith('/workspace')
    || location.pathname.startsWith('/workbench')
    || location.pathname.startsWith('/agentchat');
  const activeKey = NAV_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ?? '/datasources';

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
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#e82127',
          colorInfo: '#e82127',
          colorSuccess: '#168254',
          colorWarning: '#a7680c',
          colorError: '#cf2935',
          colorText: '#2e3033',
          colorTextSecondary: '#73777f',
          colorBgLayout: '#f4f5f6',
          colorBorderSecondary: '#e3e5e8',
          borderRadius: 6,
          fontFamily: '"Helvetica Neue", "PingFang SC", "Microsoft YaHei", Arial, sans-serif',
          boxShadowTertiary: '0 2px 8px rgba(19, 23, 31, 0.045), 0 1px 2px rgba(19, 23, 31, 0.035)',
        },
      }}
    >
      <div className={styles.shell}>
        <header className={styles.topbar}>
          <div className={styles.brandBlock}>
            <div className={styles.brandMark}>M</div>
            <div className={styles.brandCopy}>
              <strong>MEPER ChatBI</strong>
              <small>数据库能力主体 · 会话式分析</small>
            </div>
          </div>
          <div className={styles.topbarSpacer} />
          <div className={styles.userChip}>
            <div className={styles.avatar}>{(auth.username ?? '?').slice(0, 1).toUpperCase()}</div>
            <div>
              <strong>{auth.username ?? ''}</strong>
              <small>管理员</small>
            </div>
          </div>
          <Popconfirm title="退出登录？" onConfirm={logout}>
            <Button className={styles.logout} icon={<LogoutOutlined />}>退出</Button>
          </Popconfirm>
        </header>
        <div className={styles.body}>
          <aside className={styles.sidebar}>
            {NAV_ITEMS.map((item) => (
              <button
                key={item.key}
                type="button"
                className={cx(styles.navItem, activeKey === item.key && styles.navItemActive)}
                onClick={() => history.push(item.key)}
              >
                <span className={styles.navItemIcon}>{item.icon}</span>
                {item.label}
              </button>
            ))}
          </aside>
          <main
            className={cx(styles.content, isFullHeight && styles.contentFullHeight)}
          >
            <Outlet />
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
}
