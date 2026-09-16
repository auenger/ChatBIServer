package com.meper.chatbi.query;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLAlterStatement;
import com.alibaba.druid.sql.ast.statement.SQLCommitStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLMergeStatement;
import com.alibaba.druid.sql.ast.statement.SQLRollbackStatement;
import com.alibaba.druid.sql.ast.statement.SQLSavePointStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLStartTransactionStatement;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SqlCategory;
import com.meper.chatbi.spi.model.AnalyzedStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 语句拆分与分类。
 *
 * <p>主路径用 Druid 解析（ DbType 按方言；借鉴 Chat2DB 在 4 个目标库上的成熟用法），
 * 语句文本为 Druid 规范化输出（语义等价、格式可能规范化，历史记录与执行文本一致）；
 * 解析失败时退化为「尊重引号的分号切分 + 首关键字分类」。
 *
 * <p>本类只做结构与类别判定，<b>不做</b>授权判断；策略执法属 P2（QueryEnforcement 管线）。
 */
public class SqlClassifier {

    public List<AnalyzedStatement> analyze(DatabaseType type, String script) {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("SQL 脚本为空");
        }
        List<AnalyzedStatement> statements = new ArrayList<>();
        List<SQLStatement> parsed = parse(type, script);
        if (parsed != null) {
            int seq = 0;
            for (SQLStatement stmt : parsed) {
                statements.add(new AnalyzedStatement(seq++, stmt.toString(), classify(stmt)));
            }
            return statements;
        }
        int seq = 0;
        for (String fragment : splitBySemicolon(script)) {
            statements.add(new AnalyzedStatement(seq++, fragment, classifyByText(fragment)));
        }
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("SQL 脚本中未解析出任何语句");
        }
        return statements;
    }

    /** Druid 解析成功返回语句列表；整体解析失败返回 null（走 fallback）。 */
    private List<SQLStatement> parse(DatabaseType type, String script) {
        try {
            List<SQLStatement> statements = SQLUtils.parseStatements(script, druidType(type));
            return statements == null || statements.isEmpty() ? null : statements;
        } catch (Exception e) {
            return null;
        }
    }

    private static DbType druidType(DatabaseType type) {
        return switch (type) {
            case MYSQL -> DbType.mysql;
            case SQLSERVER -> DbType.sqlserver;
            case POSTGRESQL -> DbType.postgresql;
            case ORACLE -> DbType.oracle;
        };
    }

    private SqlCategory classify(SQLStatement stmt) {
        if (stmt instanceof SQLSelectStatement) {
            return SqlCategory.SELECT;
        }
        if (stmt instanceof SQLInsertStatement || stmt instanceof SQLUpdateStatement
                || stmt instanceof SQLDeleteStatement || stmt instanceof SQLMergeStatement) {
            return SqlCategory.DML;
        }
        if (stmt instanceof SQLCreateStatement || stmt instanceof SQLAlterStatement
                || stmt instanceof SQLDropStatement || stmt instanceof SQLTruncateStatement) {
            return SqlCategory.DDL;
        }
        if (stmt instanceof SQLStartTransactionStatement || stmt instanceof SQLCommitStatement
                || stmt instanceof SQLRollbackStatement || stmt instanceof SQLSavePointStatement) {
            return SqlCategory.TCL;
        }
        return SqlCategory.OTHER;
    }

    /** 首关键字分类（fallback 路径）。 */
    SqlCategory classifyByText(String sql) {
        String trimmed = sql.stripLeading();
        if (trimmed.isEmpty()) {
            return SqlCategory.OTHER;
        }
        String head = trimmed.split("\\s+", 2)[0].toUpperCase(Locale.ROOT).replace("(", "");
        return switch (head) {
            case "SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN" -> SqlCategory.SELECT;
            case "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE" -> SqlCategory.DML;
            case "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT" -> SqlCategory.DDL;
            case "BEGIN", "START", "COMMIT", "ROLLBACK", "SAVEPOINT" -> SqlCategory.TCL;
            default -> SqlCategory.OTHER;
        };
    }

    /** 尊重单引号/双引号/反引号的分号切分（fallback 路径；不做完整词法）。 */
    static List<String> splitBySemicolon(String script) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (c) {
                case '\'', '"', '`' -> {
                    quote = c;
                    current.append(c);
                }
                case ';' -> {
                    if (!current.toString().isBlank()) {
                        parts.add(current.toString().trim());
                    }
                    current.setLength(0);
                }
                default -> current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }
}
