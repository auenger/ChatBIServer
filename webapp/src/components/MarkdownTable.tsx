import { useRef } from 'react';
import type { ComponentPropsWithoutRef } from 'react';
import { Button, Tooltip, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';

/** 从已渲染的单元格复制可见文本，保留表头与列顺序；Excel 识别 TSV 的引号转义。 */
function tableText(table: HTMLTableElement) {
  return Array.from(table.rows, (row) => Array.from(row.cells, (cell) => {
    const text = cell.innerText ?? cell.textContent ?? '';
    return /[\t\r\n"]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  }).join('\t')).join('\n');
}

/** AI 回复与历史回放共用：每个 Markdown 表格独立复制，可直接粘贴到电子表格。 */
export default function MarkdownTable({ children, ...props }: ComponentPropsWithoutRef<'table'>) {
  const tableRef = useRef<HTMLTableElement>(null);
  const copy = async () => {
    const table = tableRef.current;
    if (!table) return;
    const text = tableText(table);
    try {
      // HTML 保留单元格（含换行），纯文本 TSV 供文本编辑器与不支持富文本的环境使用。
      if (navigator.clipboard?.write && typeof ClipboardItem !== 'undefined') {
        try {
          await navigator.clipboard.write([new ClipboardItem({
            'text/html': new Blob([table.outerHTML], { type: 'text/html' }),
            'text/plain': new Blob([text], { type: 'text/plain' }),
          })]);
        } catch {
          await navigator.clipboard.writeText(text);
        }
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        throw new Error('当前浏览器不支持剪贴板');
      }
      message.success('表格已复制，可粘贴到 Excel');
    } catch {
      message.error('复制失败，请检查浏览器剪贴板权限或手动选中表格复制');
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6 }}>
        <Tooltip title="复制表头和全部数据，可直接粘贴到 Excel">
          <Button size="small" aria-label="复制表格" icon={<CopyOutlined aria-hidden />} onClick={copy}>复制表格</Button>
        </Tooltip>
      </div>
      <table {...props} ref={tableRef}>{children}</table>
    </div>
  );
}
