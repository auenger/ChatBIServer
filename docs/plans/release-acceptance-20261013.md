# 当前版本验收收口（2026-10-13）

> 范围：当前引导管理员版本的 DataWiki、对话草稿、数据集跨库分析与前后端 Docker 交付。P2 本轮完成适配设计，未实现权限执法。

## 1. 基线与结论

本次使用已打包的 `0.1.0-20261013` 前后端镜像，在全新命名控制库上实际启动、登录和验收。源代码与上午全量构建相同，本轮只修改文档和验收产物忽略规则。

| 项目 | 证据/结果 |
| --- | --- |
| 后端 | `mvn -B -ntp package`：79 测试，0 失败/错误/跳过；日志 `/tmp/meper-package-20261013.log` |
| 数据库矩阵 | MySQL 5.7/8.4.9、SQL Server 2019/2022、PostgreSQL 16、Oracle 23ai；Oracle 19c 未验 |
| 前端 | TypeScript、生产构建通过，7 项流式/图表回归通过 |
| Docker 后端 | `sha256:70455e04136ab284ea5fdc716f4bad06109ff2ba1bf044a39a6fc26fdd3cdc04` |
| Docker 前端 | `sha256:caf22a152aaf1a93bbb86c9df6c50b7b70c6dc19c4066a236d2039051bafb7d7` |
| 运行环境 | 新建 `meper_release_control_20261013`，Flyway V1–V8 启动成功；前端 `127.0.0.1:18801` 同源代理后端 |
| 真实模型 | 用户指定智谱 `glm-5.3-flash`，ANTHROPIC，`maxTokens=8192`；原控制库配置保持原样 |

本次通过结论限于本记录列出的引导管理员功能路径；产品权限切换仍按 P2 单独验收。

当前 Git 基线仍为 `ec8325e` 加工作区改动；未将已有大量未提交改动自动提交。交付时另附带文件哈希的源码快照，避免误将旧 commit 当作本包完整源码。快照不包含 node_modules、编译产物、控制库或本次凭据。

## 2. 实际浏览器验证

使用 Chromium/Playwright 访问运行中的前后端镜像，API 未 mock。证据在仓库本地 `dist/acceptance/20261013/`，生成产物不进 Git。

- 实际管理员登录成功。
- DataWiki 1440 / 1280 / 1024 / 768 宽度：操作按钮无重叠、页面无横向溢出；AI 入口独立样式。
- 浮窗：鼠标拖动、键盘缩放、8 个缩放手柄存在、固定、贴边收起/展开、位置写入 localStorage。未将此冒充八方向及四边所有组合的穷举测试。
- 新建文档、编辑正文和字段绑定；未保存时 AI 禁用，离开触发确认并可继续编辑。
- 保存草稿后正式搜索不可见；查看差异、确认发布后可检索；版本历史可回看 v1。
- 浏览器 `pageerror` 为 0。脚本初次使用精确按钮名和虚拟 option 定位失败，修正测试定位后全链路通过；未修改产品代码。

另通过真实 AI 浮窗输入补充 channel 字段说明，收到草稿差异回执后人工点击「统一确认并发布」，发布成功后变更清单清空；实际 channel 语义与正式版本同步核对。证据为 `live-ui-results.json`、`ai-draft-changes.png`、`ai-published.png`。

主证据：`browser-results.json`、`toolbar-*.png`、`publish-diff.png`、`history-v1.png`。

## 3. 真实模型与数据库核验

### 3.1 知识补充与多轮修改

- 模型实际调用草稿工具，保存 orders 粒度“一行一个订单”、paid_amount 单位“分”、status 枚举及 PAID 收入口径。
- 新建术语文档，绑定 `orders.paid_amount`，未自动发布。
- 固定页面范围的同一会话第二轮，成功将会话新建文档标题改为“验收实付金额口径（已核对）”；正文与绑定保持不变，draftVersion 从 1 到 2。
- 模型尝试改动 TABLE 的结构绑定被拒绝；根据工具回执改为只改语义后成功，未开放额外写入能力。
- 证据：`knowledge-8192.json`、`knowledge-multiturn.json`，并通过 API 核对真实草稿字段。

### 3.2 跨 MySQL / PostgreSQL 计算

测试库：MySQL `meper_release_sales_20261013.orders`，PostgreSQL `release_20261013.refunds`。两笔 PAID 订单 12000/8000 分；一笔 UNPAID 9900 分排除；退款 2000 分。

模型先确认范围/结构，分别查询，调用服务端 `join_results` → `calculate_column` → `aggregate_result`。最终 **AGGREGATE 回执的 sample 为 `["200", "20", "180"]`**，均为元；不只检查自然语言答案。查询和最终结果均未截断，来源含实际执行 ID 与查询时间。

本轮模型曾传错计算参数产生 0，随后重算并使用正确结果汇总；此记录保留，说明实际调用过程并非每一步均正确。验收以已核实的最终来源和服务端结果为准，不代表任意问题都能自动得出正确结论。

证据：`analysis-live.json`。实际耗时 288.6 秒，接近当前 SSE 300 秒上限；这是功能验收，不是时延或并发 SLA 验收。

两套业务库经实际 SQL 再查，原订单和退款记录均保持不变，见 `business-data-check.json`。Wiki 修改只落控制库。

发布后另开真实分析会话，模型通过 Wiki 搜索和固定版本读取，正确回答单位、支付口径和渠道枚举，返回 revision 1 引用；没有调用业务查询。证据：`reference-live.json`。

### 3.3 配置问题与处理

原复用配置 params 为空，当前 Anthropic 网关默认 maxTokens=1024。首轮只读未写，续轮出现工具参数 JSON 截断。独立验收 provider 调整为 8192 后，知识补充、多轮改名和跨库汇总完成；原环境 provider 未修改。

部署使用该模型时，在「模型设置 → 高级参数 → Max Tokens」填写 8192。文字承诺不等同于草稿落库，必须看实际变更回执；中断/错误后查看已落库草稿，避免重复创建。早期失败证据保留在 `knowledge-live.json` / `knowledge-followup.json`。

## 4. P2 适配修订交付

[P2 适配修订](p2-permission-adaptation-20261013.md)是当前实施入口，九月计划作为历史基线保留。模块文档、两份全局设计、AGENTS 和文档索引已补同一优先级说明。

- contractVersion 2：明确 STAR、无输出列与解析失败；字段引用和结果血缘。
- 逐策略版本 + tenantEpoch + identityVersion，覆盖新增最严策略、角色变更与撤权。
- 纳入元数据/计数、Wiki 正式版本与草稿、数据集成员、结果工作区、模型输入、SSE、聊天和执行历史。
- 细分知识读/编辑/发布与数据集使用操作，不将 admin 管理权当作数据读取权。
- V9/V10 追加迁移规划与升级回退边界；全部入口接线后一次切 ENFORCED。
- 新批次 0–8 与多主体验收矩阵；下一步从框架隔离、元数据 SPI 和 v2 合同开始。

## 5. 保留边界

- 当前仍为 `BOOTSTRAP_ADMIN_UNRESTRICTED`，不能宣称多主体/字段/行权限已通过验收。
- Spring AI 类型尚未全部收回 gateway、MetadataService 的连接生命周期尚未迁出 domain；列为 P2 批次 0/4 明确修订项。
- Oracle 19c、负载/并发/长时间稳定性、任意自然语言准确率不在本次通过结论内。
- 旧 10 月 12 日记录中的“无浏览器/无真实模型”只描述当时状态；本记录补充新证据，不抹除旧记录。

## 6. 交付文件

收口包：`dist/docker/meper-chatbi-0.1.0-20261013-acceptance-linux-amd64.tar.gz`，对应 `.sha256`。保留原包；收口包复用本次实测的同一前后端镜像，新增验收证据、本文、P2 修订稿及 `source-snapshot.tar.gz` / `source-SHA256SUMS`。manifest 保留原构建基线和工作区事实，附当前源码快照哈希，不伪称已经有新的 Git 提交。

包内 `SHA256SUMS` 校验全部交付文件；源码快照不含 Git 元数据，解开后以常规 Maven/pnpm 命令构建。源码依赖下载与 Docker 基础镜像仍需要相应环境，源码快照并非依赖全离线包。
