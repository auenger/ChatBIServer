import { Children, isValidElement, useMemo } from 'react';
import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import MarkdownTable from './MarkdownTable';
import ChatChart from './ChatChart';

/** Markdown 正文保留原渲染；显式 GPT-Vis / vis 代码块使用图表渲染器。 */
export default function ChatMarkdown({ content, streaming = false }: { content: string; streaming?: boolean }) {
  const components = useMemo(() => ({
    table: ({ node: _node, ...props }: ComponentPropsWithoutRef<'table'> & { node?: unknown }) => <MarkdownTable {...props} />,
    pre: ({ node: _node, children, ...props }: ComponentPropsWithoutRef<'pre'> & { node?: unknown }) => {
      const child = Children.toArray(children)[0];
      if (isValidElement<{ className?: string; children?: ReactNode }>(child)) {
        const language = child.props.className?.replace(/^language-/, '').toLowerCase();
        if (language === 'gpt-vis' || language === 'vis') {
          return <ChatChart source={String(child.props.children ?? '')} streaming={streaming} />;
        }
      }
      return <pre {...props}>{children}</pre>;
    },
  }), [streaming]);
  return <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>{content}</ReactMarkdown>;
}
