package kr.kicc.datamatch.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/** SETUP-05: 로컬 Oracle 테스트 환경(사용자, 권한, 객체, 합성 데이터)을 확인한다. */
@OracleIntegrationTest
class OracleTestEnvironmentSmokeTest {

    private static final String OWNER = "DM_IT_OWNER";

    @Test
    void readOnlyUsersCanConnect() throws SQLException {
        for (Connection c : new Connection[] {OracleTestDatabase.roA(), OracleTestDatabase.roB()}) {
            try (c; Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT 1 FROM DUAL")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void readOnlyUserCannotModifyData() throws SQLException {
        // 권한 부족 오류: 19c 이하는 ORA-01031, 23ai 이상은 ORA-41900(missing privilege)을 반환한다.
        try (Connection c = OracleTestDatabase.roA(); Statement s = c.createStatement()) {
            String countSql = "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_TRX_STR";
            long before = count(c, countSql);
            for (String dml : new String[] {
                "INSERT INTO DM_IT_OWNER.DM_IT_TRX_STR (TRX_NO) VALUES ('X')",
                "UPDATE DM_IT_OWNER.DM_IT_TRX_STR SET MEMO = 'x' WHERE TRX_NO = 'A000001'",
                "DELETE FROM DM_IT_OWNER.DM_IT_TRX_STR WHERE TRX_NO = 'A000001'"}) {
                assertThatThrownBy(() -> s.executeUpdate(dml))
                        .as(dml)
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).as(e.getMessage()).isIn(1031, 41900));
            }
            assertThat(count(c, countSql)).isEqualTo(before);
        }
    }

    @Test
    void testObjectsAndSeedDataExist() throws SQLException {
        try (Connection c = OracleTestDatabase.roA()) {
            assertThat(count(c, "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = '" + OWNER + "'")).isGreaterThanOrEqualTo(45);
            assertThat(count(c, "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = '" + OWNER + "' AND TABLE_NAME LIKE 'DM\\_IT\\_SEARCH\\_%' ESCAPE '\\'"))
                    .isEqualTo(35);
            assertThat(count(c, "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_TRX_STR WHERE TRX_NO LIKE 'A%'")).isEqualTo(1100);
            assertThat(count(c, "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_TRX_STR WHERE TRX_NO LIKE 'B%'")).isEqualTo(990);
            assertThat(count(c, "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_TRX_NUM")).isEqualTo(2200);
            assertThat(count(c, "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_TYPES")).isEqualTo(4);
            assertThat(count(c, "SELECT COUNT(*) FROM DM_IT_OWNER.DM_IT_SLOW")).isEqualTo(5);
            assertThat(count(c, "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = '" + OWNER + "' AND TABLE_NAME = 'DM_IT_NO_B_GRANT'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void userBCannotSeeTableWithoutGrant() throws SQLException {
        try (Connection c = OracleTestDatabase.roB()) {
            assertThat(count(c, "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = '" + OWNER + "' AND TABLE_NAME = 'DM_IT_NO_B_GRANT'"))
                    .isZero();
        }
    }

    @Test
    void printsLocalDatabaseVersion() throws SQLException {
        try (Connection c = OracleTestDatabase.roA(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT PRODUCT, VERSION_FULL FROM PRODUCT_COMPONENT_VERSION WHERE ROWNUM = 1")) {
            assertThat(rs.next()).isTrue();
            System.out.println("[oracle] " + rs.getString(1) + " " + rs.getString(2)
                    + ", driver " + c.getMetaData().getDriverVersion());
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
