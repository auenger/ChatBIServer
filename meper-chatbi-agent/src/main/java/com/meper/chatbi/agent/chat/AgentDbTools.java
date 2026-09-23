package com.meper.chatbi.agent.chat;

import com.meper.chatbi.domain.DataSourceService;
import com.meper.chatbi.domain.MetadataService;
import com.meper.chatbi.domain.WorkbenchService;
import com.meper.chatbi.query.SqlClassifier;
import com.meper.chatbi.spi.SqlCategory;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.QueryResultData;
import com.meper.chatbi.spi.model.TableInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Agent 数据工具（0.1）：注册给模型的三个只读能力。
 *
 * <p>执法边界：{@code run_query} 仅放行单条 SELECT（SqlClassifier 白名单），
 * 且必经 {@link WorkbenchService}（审计/执行记录/限额全保留）——本类零 JDBC。
 * Spring AI 的 @Tool 桥接集中在 agent 模块内；AgentTool SPI 抽象在 P5 批次 2 落。
 */
public class AgentDbTools {

    /** 给模型的单次结果行数上限（完整数据仍受 ctx.maxRows 执行限额约束）。 */
    static final int MODEL_SAMPLE_ROWS = 30;

    private final ChatToolContext ctx;
    private final DataSourceService dataSources;
    private final MetadataService metadata;
    private final WorkbenchService workbench;
    private final SqlClassifier classifier;

    public AgentDbTools(ChatToolContext ctx, DataSourceService dataSources,
                        MetadataService metadata, WorkbenchService workbench, SqlClassifier classifier) {
        this.ctx = ctx;
        this.dataSources = dataSources;
        this.metadata = metadata;
        this.workbench = workbench;
        this.classifier = classifier;
    }

    @Tool(description = "列出指定库/Schema 下的所有表和视图（名称、类型、备注）。不确定有哪些库时传空字符串可获取顶层命名空间列表")
    public String list_tables(
            @ToolParam(description = "库名（MySQL）或 Schema 名（PostgreSQL/SQL Server/Oracle）；空字符串=列出顶层命名空间", required = false)
            String namespace) {
        String ns = namespace == null || namespace.isBlank() ? ctx.namespace() : namespace.trim();
        ctx.notifyTool("list_tables(" + ns + ")");
        try {
            if (namespace == null || namespace.isBlank()) {
                List<String> namespaces = metadata.namespaces(ctx.datasourceId());
                ctx.notifyToolResult("返回 " + namespaces.size() + " 个命名空间");
                return "可用命名空间: " + String.join(", ", namespaces) + "；请用 list_tables(命名空间) 查看其中的表";
            }
            List<TableInfo> tables = metadata.tables(ctx.datasourceId(), ns, null);
            if (tables.isEmpty()) {
                ctx.notifyToolResult("命名空间为空");
                return "命名空间 " + ns + " 下没有表或视图";
            }
            ctx.notifyToolResult("返回 " + tables.size() + " 个对象");
            StringBuilder sb = new StringBuilder("命名空间 ").append(ns).append(" 共 ").append(tables.size()).append(" 个对象:\n");
            for (TableInfo t : tables) {
                sb.append("- ").append(t.name()).append("（").append(t.type());
                if (t.remarks() != null && !t.remarks().isBlank()) {
                    sb.append("；").append(t.remarks());
                }
                sb.append("）\n");
            }
            return sb.toString();
        } catch (Exception e) {
            ctx.notifyToolResult("失败: " + summarize(e));
            return "列出表失败: " + summarize(e);
        }
    }

    @Tool(description = "查看某张表的完整结构：列名、类型、可空、默认值、备注、主键索引。写 SQL 前应先查看表结构")
    public String get_table_schema(
            @ToolParam(description = "库名（MySQL）或 Schema 名", required = false) String namespace,
            @ToolParam(description = "表名") String table) {
        String ns = namespace == null || namespace.isBlank() ? ctx.namespace() : namespace.trim();
        ctx.notifyTool("get_table_schema(" + ns + "." + table + ")");
        try {
            var detail = metadata.tableDetail(ctx.datasourceId(), ns, table);
            ctx.notifyToolResult("返回 " + detail.columns().size() + " 列");
            StringBuilder sb = new StringBuilder("表 ").append(ns).append(".").append(detail.table())
                    .append("（").append(detail.type()).append("）列清单:\n");
            for (var c : detail.columns()) {
                sb.append("- ").append(c.name()).append(" ").append(c.typeName())
                        .append(Boolean.TRUE.equals(c.nullable()) || "YES".equalsIgnoreCase(String.valueOf(c.nullable())) ? "" : " NOT NULL");
                if (c.defaultValue() != null) {
                    sb.append(" DEFAULT ").append(c.defaultValue());
                }
                if (c.remarks() != null && !c.remarks().isBlank()) {
                    sb.append(" -- ").append(c.remarks());
                }
                sb.append("\n");
            }
            detail.indexes().stream().filter(i -> "PRIMARY".equalsIgnoreCase(i.name()) || i.name().equalsIgnoreCase("PRIMARY KEY"))
                    .findFirst()
                    .ifPresent(pk -> sb.append("主键: ").append(String.join(", ", pk.columns())).append("\n"));
            return sb.toString();
        } catch (NoSuchElementException e) {
            ctx.notifyToolResult("表不存在");
            return "表不存在: " + ns + "." + table;
        } catch (Exception e) {
            ctx.notifyToolResult("失败: " + summarize(e));
            return "查看表结构失败: " + summarize(e);
        }
    }

    @Tool(description = "执行只读 SELECT 查询并返回数据（单条语句，最多 200 行）。INSERT/UPDATE/DELETE/DDL 等非 SELECT 语句会被拒绝")
    public String run_query(
            @ToolParam(description = "完整的单条 SELECT 语句（不要带分号）") String sql) {
        ctx.notifyTool("run_query: " + (sql.length() > 80 ? sql.substring(0, 80) + "…" : sql));
        try {
            var profile = dataSources.get(ctx.datasourceId());
            List<AnalyzedStatement> statements = classifier.analyze(profile.type(), sql);
            if (statements.size() != 1 || statements.get(0).category() != SqlCategory.SELECT) {
                return "拒绝执行: 仅允许单条 SELECT 语句（检测到 " + statements.size() + " 条语句，类别 "
                        + (statements.isEmpty() ? "空" : statements.get(0).category()) + "）。"
                        + "写操作请告知用户到 SQL 工作台手动执行。";
            }
            var result = workbench.execute(ctx.principal(), ctx.datasourceId(), sql, ctx.maxRows());
            var st = result.statements().get(0);
            if (st.error() != null) {
                ctx.notifyToolResult("失败: " + st.error());
                return "查询执行失败: " + st.error();
            }
            int rows = st.resultData() == null ? 0 : st.resultData().rows().size();
            ctx.notifyToolResult("返回 " + rows + " 行");
            return formatResult(st.resultData());
        } catch (Exception e) {
            ctx.notifyToolResult("失败: " + summarize(e));
            return "查询执行失败: " + summarize(e);
        }
    }

    private static String formatResult(QueryResultData data) {
        if (data == null) {
            return "查询无结果集";
        }
        StringBuilder sb = new StringBuilder("查询结果 ").append(data.rows().size()).append(" 行 × ")
                .append(data.columns().size()).append(" 列:\n");
        sb.append(String.join(" | ", data.columns())).append("\n");
        int shown = Math.min(data.rows().size(), MODEL_SAMPLE_ROWS);
        for (int i = 0; i < shown; i++) {
            sb.append(String.join(" | ", data.rows().get(i))).append("\n");
        }
        if (data.rows().size() > shown) {
            sb.append("（仅显示前 ").append(shown).append(" 行，其余 ").append(data.rows().size() - shown)
                    .append(" 行已截断；如需更多可加 LIMIT 或聚合）\n");
        }
        return sb.toString();
    }

    private static String summarize(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
