import 'react';
import type { PointerEventHandler } from 'react';

// 现有 icons 5 的生成声明仍引用 React 18 新类型移除的两个 capture 属性。
// 仅补回可选声明，保持图标类型检查；不改变运行时事件或升级依赖。
declare module 'react' {
  interface DOMAttributes<T> {
    onPointerEnterCapture?: PointerEventHandler<T>;
    onPointerLeaveCapture?: PointerEventHandler<T>;
  }
}
