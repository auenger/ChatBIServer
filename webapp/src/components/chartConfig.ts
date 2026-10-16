export const CHART_TYPES = ['line', 'area', 'column', 'bar', 'pie', 'scatter', 'radar', 'funnel'] as const;
export type ChartType = typeof CHART_TYPES[number];
type ChartRow = Record<string, string | number>;
export interface ChatChartConfig {
  type: ChartType;
  data: ChartRow[];
  title?: string;
  axisXTitle?: string;
  axisYTitle?: string;
  stack?: boolean;
  group?: boolean;
  innerRadius?: number;
}

function record(input: unknown): input is Record<string, unknown> {
  return typeof input === 'object' && input !== null && !Array.isArray(input);
}

function label(input: unknown): input is string | number {
  return (typeof input === 'string' && input.trim().length > 0 && input.length <= 200)
    || (typeof input === 'number' && Number.isFinite(input));
}

/** 图表仅接收有限行数的内联数据与白名单配置，不将模型任意配置透传给绘图库。 */
export function validateChartConfig(input: unknown): ChatChartConfig {
  if (!record(input) || !CHART_TYPES.includes(input.type as ChartType)) {
    throw new Error('不支持此图表类型');
  }
  if (!Array.isArray(input.data) || input.data.length === 0 || input.data.length > 200) {
    throw new Error('图表需要 1–200 条数据');
  }
  const type = input.type as ChartType;
  const categoryKey = type === 'line' || type === 'area' ? 'time' : type === 'radar' ? 'name' : 'category';
  const data = input.data.map((row): ChartRow => {
    if (!record(row)) throw new Error('图表数据行格式错误');
    if (type === 'scatter') {
      if (typeof row.x !== 'number' || !Number.isFinite(row.x)
          || typeof row.y !== 'number' || !Number.isFinite(row.y)) {
        throw new Error('散点图的 x、y 必须是数值');
      }
    } else if (!label(row[categoryKey]) || typeof row.value !== 'number' || !Number.isFinite(row.value)) {
      throw new Error(`图表需要 ${categoryKey} 和数值 value`);
    }
    if ((type === 'pie' || type === 'funnel') && (row.value as number) < 0) {
      throw new Error('占比与漏斗图的数值不能为负');
    }
    const clean: ChartRow = type === 'scatter'
      ? { x: row.x as number, y: row.y as number }
      : { [categoryKey]: row[categoryKey] as string | number, value: row.value as number };
    if (row.group !== undefined) {
      if (!label(row.group)) throw new Error('分组名称格式错误');
      clean.group = row.group as string | number;
    }
    return clean;
  });
  if (type === 'pie' && !data.some((row) => (row.value as number) > 0)) {
    throw new Error('饼图至少需要一个大于零的数值');
  }
  const config: ChatChartConfig = { type, data };
  for (const key of ['title', 'axisXTitle', 'axisYTitle'] as const) {
    const value = input[key];
    if (value !== undefined) {
      if (typeof value !== 'string' || value.length > 200) throw new Error('图表标题格式错误');
      config[key] = value;
    }
  }
  for (const key of ['stack', 'group'] as const) {
    const value = input[key];
    if (typeof value === 'boolean') config[key] = value;
  }
  if (type === 'pie' && typeof input.innerRadius === 'number'
      && input.innerRadius >= 0 && input.innerRadius < 1) config.innerRadius = input.innerRadius;
  return config;
}
