package com.meper.chatbi.domain;

import com.meper.chatbi.jdbc.JdbcSqlExecutor;
import com.meper.chatbi.query.SqlClassifier;
import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.ConnectionSpec;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.ExecutionLimits;
import com.meper.chatbi.spi.model.SqlExecutionResult;
import com.meper.chatbi.spi.model.StatementResult;
import com.meper.chatbi.storage.model.ExecutionRecord;
import com.meper.chatbi.storage.repository.AuditRepository;
import com.meper.chatbi.storage.repository.ExecutionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * SQL 工作台领域服务：preview（分类预检）→ execute（受控执行）→ 执行历史。
 *
 * <p>阶段 1 执法状态为 BOOTSTRAP（无策略约束），响应中显式携带；
 * P2 接入策略后此处插入「策略决策」节点，调用方无感知。
 */
@Service
public class WorkbenchService {

    /** preview 结果。 */
    public record PreviewResult(long datasourceId, String datasourceName,
                                EnforcementState enforcement, List<AnalyzedStatement> statements) {
    }

    /** 执行结果（语句明细沿用 SPI StatementResult；结果集数据直接返回）。 */
    public record ExecuteResult(long executionId, EnforcementState enforcement,
                                ExecutionRecord.Status status, List<StatementResult> statements,
                                long durationMs) {
    }

    private final DataSourceService dataSources;
    private final SqlClassifier classifier;
    private final JdbcSqlExecutor executor;
    private final ExecutionRepository executions;
    private final AuditRepository audits;
    private final ExecutionContextFactory contextFactory;

    public WorkbenchService(DataSourceService dataSources,
                            SqlClassifier classifier,
                            JdbcSqlExecutor executor,
                            ExecutionRepository executions,
                            AuditRepository audits,
                            ExecutionContextFactory contextFactory) {
        this.dataSources = dataSources;
        this.classifier = classifier;
        this.executor = executor;
        this.executions = executions;
        this.audits = audits;
        this.contextFactory = contextFactory;
    }

    public PreviewResult preview(String principal, long datasourceId, String sql) {
        var profile = dataSources.get(datasourceId);
        List<AnalyzedStatement> statements = classifier.analyze(profile.type(), sql);
        ExecutionContext context = contextFactory.workbench(principal, datasourceId);
        audits.insert("default", principal, "WORKBENCH_PREVIEW", "DATASOURCE", String.valueOf(datasourceId),
                Map.of("statementCount", statements.size()));
        return new PreviewResult(profile.id(), profile.name(),
                context.enforcementState(), statements);
    }

    public ExecuteResult execute(String principal, long datasourceId, String sql, Integer maxRows) {
        Instant start = Instant.now();
        var profile = dataSources.get(datasourceId);
        List<AnalyzedStatement> statements = classifier.analyze(profile.type(), sql);
        ExecutionContext context = contextFactory.workbench(principal, datasourceId);

        char[] password = dataSources.activePassword(profile);
        var spec = ConnectionSpec.of(profile, password);
        var dataSource = dataSources.poolFor(profile);

        ExecutionLimits limits = maxRows == null ? ExecutionLimits.DEFAULTS
                : new ExecutionLimits(maxRows, ExecutionLimits.DEFAULTS.queryTimeoutSeconds(),
                ExecutionLimits.DEFAULTS.cellCharLimit(), ExecutionLimits.DEFAULTS.maxColumns());
        SqlExecutionResult result = executor.execute(context, dataSource, statements, limits, null);
        Instant finish = Instant.now();

        long executionId = executions.insert(context, datasourceId, sql, result, start, finish);
        long failed = result.statements().stream().filter(s -> s.error() != null).count();
        audits.insert("default", principal, "WORKBENCH_EXECUTE", "DATASOURCE", String.valueOf(datasourceId),
                Map.of("executionId", executionId, "statements", result.statements().size(),
                        "failed", failed));
        return new ExecuteResult(executionId, context.enforcementState(),
                failed == 0 ? ExecutionRecord.Status.SUCCESS
                        : failed == result.statements().size() ? ExecutionRecord.Status.FAILED
                        : ExecutionRecord.Status.PARTIAL,
                result.statements(), finish.toEpochMilli() - start.toEpochMilli());
    }

    public ExecutionRecord getExecution(String principal, long executionId) {
        return executions.findById(executionId)
                .orElseThrow(() -> new NoSuchElementException("执行记录不存在: " + executionId));
    }

    public List<ExecutionRecord> listExecutions(String principal, Long datasourceId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return executions.list(datasourceId, safeSize, (long) (safePage - 1) * safeSize);
    }
}
