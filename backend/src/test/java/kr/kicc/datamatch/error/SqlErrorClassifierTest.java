package kr.kicc.datamatch.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.util.Map;
import kr.kicc.datamatch.error.SqlErrorClassifier.Kind;
import org.junit.jupiter.api.Test;

/** DB-03: SQLException 분류(기술 설계서 6.5절). */
class SqlErrorClassifierTest {

    @Test
    void classifiesConnectionTimeoutCancelAndOther() {
        assertThat(SqlErrorClassifier.classify(new SQLRecoverableException("IO Error", null, 17002), false)).isEqualTo(Kind.CONNECTION);
        assertThat(SqlErrorClassifier.classify(new SQLException("x", "08006", 0), false)).isEqualTo(Kind.CONNECTION);
        assertThat(SqlErrorClassifier.classify(new SQLException("ORA-03113", "61000", 3113), false)).isEqualTo(Kind.CONNECTION);
        assertThat(SqlErrorClassifier.classify(new SQLTimeoutException("ORA-01013", "72000", 1013), false)).isEqualTo(Kind.TIMEOUT);
        assertThat(SqlErrorClassifier.classify(new SQLException("ORA-01013", "72000", 1013), false)).isEqualTo(Kind.TIMEOUT);
        assertThat(SqlErrorClassifier.classify(new SQLException("ORA-01013", "72000", 1013), true)).isEqualTo(Kind.CANCELLED);
        assertThat(SqlErrorClassifier.classify(new SQLTimeoutException("ORA-01013", "72000", 1013), true)).isEqualTo(Kind.CANCELLED);
        assertThat(SqlErrorClassifier.classify(new SQLException("ORA-00942", "42000", 942), false)).isEqualTo(Kind.OTHER);
        assertThat(SqlErrorClassifier.classify(new SQLException("ORA-41900", "42000", 41900), true)).isEqualTo(Kind.OTHER);
        assertThat(SqlErrorClassifier.classify(new SQLException("no state"), false)).isEqualTo(Kind.OTHER);
    }

    @Test
    void mapsKindsToErrorCodesWithoutDriverText() {
        Map<String, String> details = Map.of("env", "test");
        assertThat(SqlErrorClassifier.toException(Kind.CONNECTION, "test", details).code()).isEqualTo(ErrorCode.DB_CONNECTION_FAILED);
        assertThat(SqlErrorClassifier.toException(Kind.TIMEOUT, "test", details).code()).isEqualTo(ErrorCode.QUERY_TIMEOUT);
        DataMatchException other = SqlErrorClassifier.toException(Kind.OTHER, "test", details);
        assertThat(other.code()).isEqualTo(ErrorCode.DB_QUERY_FAILED);
        assertThat(other.details()).isEqualTo(details);
        assertThat(other.getMessage()).doesNotContain("ORA-");
        assertThatThrownBy(() -> SqlErrorClassifier.toException(Kind.CANCELLED, "test", details))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
