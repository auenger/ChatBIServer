import { defineConfig } from 'umi';

export default defineConfig({
  // 基础 umi 4：无默认布局，页面结构完全自定义（借鉴 Chat2DB 的左侧 icon 竖导航模式）
  hash: true,
  history: { type: 'browser' },
  // 端口用环境变量指定：PORT=8001 pnpm dev（umi 4.7 不接受顶层 port 配置键）
  routes: [
    { path: '/login', component: 'login' },
    {
      path: '/',
      component: 'layouts/MainLayout',
      routes: [
        { path: '/', redirect: '/workspace' },
        { path: '/datasources', component: 'datasources' },
        { path: '/workspace', component: 'workspace' },
        { path: '/workbench', component: 'workbench' },
        { path: '/agentchat', component: 'agentchat' },
        { path: '/modelproviders', component: 'modelproviders' },
      ],
    },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      // SSE（AI 对话流式）：剥掉 Accept-Encoding，防止压缩把流式事件攒成一次输出
      // （浏览器 fetch 默认携带 gzip，压缩器缓冲导致前端「一次性加载」）
      on: {
        proxyReq(proxyReq: { removeHeader: (name: string) => void }) {
          proxyReq.removeHeader('accept-encoding');
        },
      },
    },
  },
});
