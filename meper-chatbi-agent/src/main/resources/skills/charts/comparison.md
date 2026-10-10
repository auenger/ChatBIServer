# 分类对比

- 分类数量比较选择 `column`；标签长或展示排名选择 `bar`。
- 先获取真实分组聚合结果，每行字段为 `category`（类别）、`value`（数值），可带 `group`（分组）。
- 排名使用明确的排序与 Top N，说明筛选范围及单位；不可把未查询的类别当成零。
- 多系列并列可设置 `group:true`，堆叠可设置 `stack:true`，不要同时启用。
- 返回 Markdown 表格、图表与简要中文结论。图表使用 `GPT-Vis` 代码块，内容是 JSON：

```GPT-Vis
{"type":"bar","title":"区域销售额对比","axisXTitle":"区域","axisYTitle":"销售额（元）","data":[{"category":"华东","value":120},{"category":"华南","value":80}]}
```

示例数据仅用于说明格式，实际回答必须替换为查询结果。空结果或查询失败时不生成图表。
