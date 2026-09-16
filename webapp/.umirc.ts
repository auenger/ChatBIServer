import { defineConfig } from 'umi';

export default defineConfig({
  // 基础 umi 4：无默认布局，页面结构完全自定义（借鉴 Chat2DB 的左侧 icon 竖导航模式）
  hash: true,
  history: { type: 'browser' },
  routes: [
    { path: '/login', component: 'login' },
    {
      path: '/',
      component: 'layouts/MainLayout',
      routes: [
        { path: '/', redirect: '/datasources' },
        { path: '/datasources', component: 'datasources' },
        { path: '/workbench', component: 'workbench' },
      ],
    },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
