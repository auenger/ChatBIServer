package com.meper.chatbi.query;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SqlCategory;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlClassifierTest {

    private final SqlClassifier classifier = new SqlClassifier();

    @ParameterizedTest(name = "{0}")
    @EnumSource(DatabaseType.class)
    void splitsAndClassifiesCommonScript(DatabaseType type) {
        List<AnalyzedStatement> statements = classifier.analyze(type, """
                SELECT id FROM users;
                INSERT INTO t VALUES (1);
                UPDATE t SET a = 2;
                DELETE FROM t;
                CREATE TABLE t (id INT);
                """);
        assertEquals(5, statements.size());
        assertEquals(SqlCategory.SELECT, statements.get(0).category());
        assertEquals(SqlCategory.DML, statements.get(1).category());
        assertEquals(SqlCategory.DML, statements.get(2).category());
        assertEquals(SqlCategory.DML, statements.get(3).category());
        assertEquals(SqlCategory.DDL, statements.get(4).category());
    }

    @Test
    void stringLiteralSemicolonStaysSingleStatement() {
        List<AnalyzedStatement> statements = classifier.analyze(DatabaseType.MYSQL,
                "SELECT 'a;b' AS v FROM DUAL");
        assertEquals(1, statements.size());
    }

    @Test
    void unparseableScriptFallsBackToKeywordClassification() {
        List<AnalyzedStatement> statements = classifier.analyze(DatabaseType.MYSQL,
                "WHATEVER FOO BAR; SELECT 1");
        assertEquals(2, statements.size());
        assertEquals(SqlCategory.OTHER, statements.get(0).category());
        assertEquals(SqlCategory.SELECT, statements.get(1).category());
    }

    @Test
    void quotedSemicolonHandledInFallbackSplit() {
        // WHATEVER 无法解析 → fallback 切分；字符串里的分号不得切断语句
        List<String> parts = SqlClassifier.splitBySemicolon("FOO 'a;b' BAR; SELECT 1");
        assertEquals(2, parts.size());
        assertEquals("FOO 'a;b' BAR", parts.get(0));
    }

    @Test
    void oracleProbeAndTclCategories() {
        List<AnalyzedStatement> statements = classifier.analyze(DatabaseType.ORACLE,
                "SELECT 1 FROM DUAL; COMMIT");
        assertEquals(SqlCategory.SELECT, statements.get(0).category());
        assertEquals(SqlCategory.TCL, statements.get(1).category());
    }

    @Test
    void blankScriptRejected() {
        assertThrows(IllegalArgumentException.class, () -> classifier.analyze(DatabaseType.MYSQL, "  ;  "));
    }

    @Test
    void formatsSqlWithTargetDialect() {
        String formatted = classifier.format(DatabaseType.MYSQL,
                "select id,name from users where id=1 order by name");
        assertTrue(formatted.contains("SELECT"));
        assertTrue(formatted.contains("FROM users"));
        assertTrue(formatted.contains("ORDER BY name"));
    }

    @Test
    void blankFormatRejected() {
        assertThrows(IllegalArgumentException.class, () -> classifier.format(DatabaseType.POSTGRESQL, "  "));
    }
}
