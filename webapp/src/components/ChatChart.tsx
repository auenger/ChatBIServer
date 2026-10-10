import { useLayoutEffect, useRef, useState } from 'react';
import { Alert, Spin, Typography, theme } from 'antd';
import type { GPTVis } from '@antv/gpt-vis';
import { validateChartConfig } from './chartConfig';

/** 图表按需加载；流式增量与历史回放复用，取消和卸载时销毁绘图实例。 */
export default function ChatChart({ source, streaming }: { source: string; streaming: boolean }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState('');
  const { token } = theme.useToken();

  // 在 React 移除容器前销毁 Canvas，避免自动 resize/动画访问已移除的图形。
  useLayoutEffect(() => {
    let disposed = false;
    let chart: GPTVis | undefined;
    setState('loading');
    const timer = setTimeout(async () => {
      try {
        if (source.length > 100_000) throw new Error('图表内容过长');
        const { GPTVis: Renderer, parse } = await import('@antv/gpt-vis');
        if (disposed || !containerRef.current) return;
        const text = source.trim();
        const raw = text.startsWith('{') ? JSON.parse(text) : parse(text);
        const config = validateChartConfig(raw);
        chart = new Renderer({ container: containerRef.current, height: 320, theme: 'default' });
        chart.render({ ...config, style: { palette: [token.colorPrimary, '#1677ff', '#52c41a', '#faad14', '#722ed1', '#13c2c2'] } });
        setState('ready');
      } catch (e) {
        if (!disposed) {
          setError((e as Error).message || '图表渲染失败');
          setState('error');
        }
      }
    }, streaming ? 300 : 0);
    return () => {
      disposed = true;
      clearTimeout(timer);
      chart?.destroy();
    };
  }, [source, streaming, token.colorPrimary]);

  const waiting = state !== 'ready' && (streaming || state === 'loading');
  return (
    <div style={{ width: 640, maxWidth: '100%', border: `1px solid ${token.colorBorderSecondary}`, borderRadius: token.borderRadiusLG, margin: '12px 0', overflow: 'hidden', background: token.colorBgContainer }}>
      <div style={{ position: 'relative', padding: '8px 12px' }}>
        {waiting && <div style={{ position: 'absolute', inset: 0, display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'center', color: token.colorTextSecondary }}><Spin size="small" /> 图表生成中</div>}
        {!streaming && state === 'error' && <Alert type="warning" message="暂时无法展示图表" description={error} showIcon />}
        <div ref={containerRef} role="img" aria-label="AI 数据图表" style={{ height: !streaming && state === 'error' ? 0 : 320, visibility: state === 'ready' ? 'visible' : 'hidden' }} />
      </div>
      <details style={{ borderTop: `1px solid ${token.colorBorderSecondary}`, padding: '6px 12px', color: token.colorTextSecondary, fontSize: 12 }}>
        <summary style={{ cursor: 'pointer' }}>查看图表数据</summary>
        <Typography.Paragraph copyable={{ text: source }} style={{ marginBottom: 0 }}>
          <pre style={{ maxHeight: 240, overflow: 'auto' }}>{source}</pre>
        </Typography.Paragraph>
      </details>
    </div>
  );
}
