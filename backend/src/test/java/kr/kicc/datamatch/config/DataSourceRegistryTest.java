package kr.kicc.datamatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** DB-01: 환경 설정 바인딩과 DataSource 생성·연결 정책(기술 설계서 6.1절). 실제 Oracle 연결은 DataSourceRegistryOracleTest. */
@ExtendWith(OutputCaptureExtension.class)
class DataSourceRegistryTest {

    private static final String SECRET_USER = "SECRET_RO_USER";
    private static final String SECRET_PASSWORD = "S3cret-Pw!";

    private final List<HikariConfig> created = new ArrayList<>();
    private final Connection connection = mock(Connection.class);

    private static DataMatchProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("datamatch", DataMatchProperties.class)
                .orElseGet(DataMatchProperties::new);
    }

    private static DataMatchProperties props(String... envs) {
        DataMatchProperties p = new DataMatchProperties();
        for (String env : envs) {
            p.getEnvironments().put(env, new EnvironmentProperties("jdbc:oracle:thin:@//h:1521/S", SECRET_USER, SECRET_PASSWORD));
        }
        return p;
    }

    private DataSourceRegistry registry(DataMatchProperties p) throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(connection);
        return new DataSourceRegistry(p, config -> {
            created.add(config);
            return ds;
        });
    }

    @Test
    void onlyConfiguredKnownEnvironmentsAreAvailable() {
        DataMatchProperties p = bind(Map.of(
                "datamatch.environments.prod.url", "jdbc:oracle:thin:@//p:1521/P",
                "datamatch.environments.prod.username", "ro",
                "datamatch.environments.prod.password", "x",
                "datamatch.environments.dev.url", "jdbc:oracle:thin:@//d:1521/D",
                "datamatch.environments.dev.username", "ro",
                "datamatch.environments.test.url", " ",
                "datamatch.environments.test.username", "ro",
                "datamatch.environments.stage.url", "jdbc:oracle:thin:@//s:1521/S",
                "datamatch.environments.stage.username", "ro"));
        DataSourceRegistry registry = new DataSourceRegistry(p);
        assertThat(registry.availableEnvironments()).containsExactly("dev", "prod");
        assertThat(registry.isAvailable("test")).isFalse();
        assertThat(registry.isAvailable("stage")).isFalse();
        assertThat(registry.isAvailable(null)).isFalse();
        assertThat(new DataSourceRegistry(new DataMatchProperties()).availableEnvironments()).isEmpty();
    }

    @Test
    void sameEnvironmentSharesOneDataSourceAndUnusedEnvironmentsAreNotCreated() throws SQLException {
        DataSourceRegistry registry = registry(props("dev", "test", "prod"));
        assertThat(created).isEmpty();
        registry.withConnection("test", c -> 1);
        registry.withConnection("test", c -> 2);
        assertThat(registry.dataSource("test")).isSameAs(registry.dataSource("test"));
        assertThat(created).extracting(HikariConfig::getPoolName).containsExactly("datamatch-test");
    }

    @Test
    void unknownOrUnconfiguredEnvironmentIsEnvNotConfigured() throws SQLException {
        DataSourceRegistry registry = registry(props("test"));
        for (String env : new String[] {"prod", "stage", null}) {
            assertThatThrownBy(() -> registry.withConnection(env, c -> 1))
                    .isInstanceOf(DataMatchException.class)
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.ENV_NOT_CONFIGURED));
        }
        assertThat(created).isEmpty();
    }

    @Test
    void connectionFailureIsDbConnectionFailedWithoutCredentials(CapturedOutput output) throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException(
                "ORA-01017: invalid username/password; user=" + SECRET_USER + " password=" + SECRET_PASSWORD, "72000", 1017));
        DataSourceRegistry registry = new DataSourceRegistry(props("prod"), config -> ds);

        assertThatThrownBy(() -> registry.withConnection("prod", c -> 1))
                .isInstanceOf(DataMatchException.class)
                .satisfies(e -> {
                    DataMatchException ex = (DataMatchException) e;
                    assertThat(ex.code()).isEqualTo(ErrorCode.DB_CONNECTION_FAILED);
                    assertThat(ex.details()).isEqualTo(Map.of("env", "prod"));
                    assertThat(ex.getCause()).isNull();
                })
                .hasMessageContaining("prod")
                .hasMessageNotContaining(SECRET_USER)
                .hasMessageNotContaining(SECRET_PASSWORD);
        assertThat(output.getAll()).contains("DB connection failed: env=prod")
                .doesNotContain(SECRET_USER)
                .doesNotContain(SECRET_PASSWORD)
                .doesNotContain("jdbc:oracle");
    }

    @Test
    void poolConfigIsReadOnlyWithoutAutoCommitAndLimited() {
        HikariConfig config = DataSourceRegistry.configFor("test",
                new EnvironmentProperties("jdbc:oracle:thin:@//h:1521/S", "ro", "pw"));
        assertThat(config.isReadOnly()).isTrue();
        assertThat(config.isAutoCommit()).isFalse();
        assertThat(config.getMaximumPoolSize()).isEqualTo(2);
        assertThat(config.getConnectionTimeout()).isEqualTo(10_000L);
        assertThat(config.getInitializationFailTimeout()).isNegative();
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@//h:1521/S");
        assertThat(DataSourceRegistry.QUERY_TIMEOUT_SECONDS).isEqualTo(30);
    }

    @Test
    void connectionIsRolledBackAndClosedAfterUseEvenOnError() throws SQLException {
        DataSourceRegistry registry = registry(props("test"));
        String result = registry.withConnection("test", c -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection).rollback();
        verify(connection).close();

        Connection failing = mock(Connection.class);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(failing);
        DataSourceRegistry other = new DataSourceRegistry(props("test"), config -> ds);
        assertThatThrownBy(() -> other.withConnection("test", c -> {
            throw new SQLException("boom");
        })).isInstanceOf(SQLException.class).hasMessage("boom");
        verify(failing).rollback();
        verify(failing).close();
    }

    @Test
    void destroyClosesCreatedPools() throws Exception {
        CloseableDataSource ds = mock(CloseableDataSource.class);
        when(ds.getConnection()).thenReturn(connection);
        DataSourceRegistry registry = new DataSourceRegistry(props("test"), config -> ds);
        registry.withConnection("test", c -> 1);
        registry.destroy();
        verify(ds).close();
    }

    interface CloseableDataSource extends DataSource, AutoCloseable {
    }

    @Test
    void exampleFileKeysMatchBindingClass() throws Exception {
        Properties example = new Properties();
        try (InputStream in = new FileInputStream(Path.of("config/datamatch.properties.example").toFile())) {
            example.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        Set<String> expected = new TreeSet<>();
        for (String env : DataSourceRegistry.ENVIRONMENTS) {
            for (String key : new String[] {"url", "username", "password"}) {
                expected.add("datamatch.environments." + env + "." + key);
            }
        }
        Set<String> datamatchKeys = example.stringPropertyNames().stream()
                .filter(k -> k.startsWith("datamatch.")).collect(Collectors.toCollection(TreeSet::new));
        assertThat(datamatchKeys).isEqualTo(expected);

        Map<String, String> source = example.stringPropertyNames().stream()
                .collect(Collectors.toMap(k -> k, example::getProperty));
        DataMatchProperties bound = bind(source);
        assertThat(new DataSourceRegistry(bound).availableEnvironments()).containsExactly("dev", "test", "prod");
        assertThat(bound.getEnvironments().get("prod").toString()).doesNotContain("<비밀번호>");
    }
}
