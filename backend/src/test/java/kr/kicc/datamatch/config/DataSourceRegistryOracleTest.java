package kr.kicc.datamatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.it.OracleIntegrationTest;
import kr.kicc.datamatch.it.OracleTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB-01 [Oracle]: 로컬 Oracle 테스트 스키마(SETUP-05)로 실제 HikariCP·ojdbc8 연결 동작을 확인한다. */
@OracleIntegrationTest
class DataSourceRegistryOracleTest {

    private DataSourceRegistry registry;

    @BeforeEach
    void setUp() {
        String url = OracleTestDatabase.url();
        DataMatchProperties p = new DataMatchProperties();
        p.getEnvironments().put("test", new EnvironmentProperties(url,
                OracleTestDatabase.user("RO_A"), OracleTestDatabase.password("RO_A")));
        p.getEnvironments().put("prod", new EnvironmentProperties(url,
                OracleTestDatabase.user("RO_B"), OracleTestDatabase.password("RO_B")));
        // 닫힌 포트로 연결 실패를 만든다.
        p.getEnvironments().put("dev", new EnvironmentProperties(url.replaceFirst(":\\d+/", ":1/"),
                OracleTestDatabase.user("RO_A"), OracleTestDatabase.password("RO_A")));
        registry = new DataSourceRegistry(p);
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    @Test
    void readOnlyUsersConnectThroughRegistryWithReadOnlyNonAutoCommitConnections() throws Exception {
        for (String env : new String[] {"test", "prod"}) {
            int one = registry.withConnection(env, c -> {
                assertThat(c.isReadOnly()).isTrue();
                assertThat(c.getAutoCommit()).isFalse();
                try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT 1 FROM DUAL")) {
                    rs.next();
                    return rs.getInt(1);
                }
            });
            assertThat(one).as(env).isEqualTo(1);
        }
    }

    @Test
    void sameEnvironmentSharesOnePool() throws Exception {
        registry.withConnection("test", c -> 1);
        registry.withConnection("test", c -> 1);
        assertThat(registry.dataSource("test")).isSameAs(registry.dataSource("test"));
        assertThat(registry.dataSource("test")).isNotSameAs(registry.dataSource("prod"));
    }

    @Test
    void wrongPortIsDbConnectionFailed() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> registry.withConnection("dev", c -> 1))
                .isInstanceOf(DataMatchException.class)
                .satisfies(e -> {
                    assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.DB_CONNECTION_FAILED);
                    assertThat(((DataMatchException) e).details()).isEqualTo(Map.of("env", "dev"));
                });
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(15_000);
    }
}
