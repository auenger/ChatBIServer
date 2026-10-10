const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');

const source = fs.readFileSync(require.resolve('../src/components/chartConfig.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const context = { exports: {} };
vm.runInNewContext(compiled, context);
const { validateChartConfig } = context.exports;
const plain = value => JSON.parse(JSON.stringify(value));

test('supports the eight chart data contracts and keeps numbers and groups', () => {
  for (const type of ['line', 'area', 'column', 'bar', 'pie', 'radar', 'funnel', 'scatter']) {
    const key = ['line', 'area'].includes(type) ? 'time' : type === 'radar' ? 'name' : 'category';
    const row = type === 'scatter' ? { x: 12, y: 30, group: '甲' } : { [key]: '甲', value: 30, group: '乙' };
    const result = validateChartConfig({ type, title: '测试', data: [row] });
    assert.deepEqual(plain(result), { type, title: '测试', data: [row] });
  }
});

test('does not pass external data, style or arbitrary chart configuration to renderer', () => {
  const result = validateChartConfig({ type:'bar', data:[{category:'甲',value:2,url:'https://external'}],
    url:'https://external', tooltip:{formatter:'script'}, style:{backgroundColor:'url(external)'}, onClick:'script' });
  assert.deepEqual(plain(result), { type:'bar',data:[{category:'甲',value:2}] });
});

test('rejects unsupported charts, empty or excessive data, and invalid numeric data', () => {
  for (const input of [null, {type:'html',data:[]}, {type:'bar',data:[]},
    {type:'bar',data:Array(201).fill({category:'甲',value:1})},
    {type:'bar',data:[{category:'甲',value:'1元'}]},
    {type:'line',data:[{time:'2026',value:Infinity}]},
    {type:'scatter',data:[{x:1,y:NaN}]},
    {type:'pie',data:[{category:'甲',value:-1}]},
    {type:'pie',data:[{category:'甲',value:0}]}]) {
    assert.throws(() => validateChartConfig(input));
  }
});

test('keeps optional titles and chart settings but limits labels and pie radius', () => {
  const input = {type:'pie', data:[{category:'甲',value:10}], title:'份额',innerRadius:0.6};
  assert.equal(validateChartConfig(input).innerRadius, 0.6);
  assert.equal(validateChartConfig({...input,innerRadius:2}).innerRadius, undefined);
  assert.throws(() => validateChartConfig({...input,title:'字'.repeat(201)}));
});
