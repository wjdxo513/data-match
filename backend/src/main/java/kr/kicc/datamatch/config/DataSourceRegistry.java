package kr.kicc.datamatch.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 환경별 읽기 전용 DataSource 관리(기술 설계서 6.1절, TD-05).
 *
 * <ul>
 *   <li>DataSource는 환경을 처음 사용할 때 만들고(최대 풀 2, 연결 획득 제한 10초), A·B가 같은 환경이면 공유한다.</li>
 *   <li>연결은 읽기 전용·자동 커밋 해제이며 {@link #withConnection} 이 사용 후 롤백하고 닫는다.</li>
 *   <li>연결 실패는 {@link ErrorCode#DB_CONNECTION_FAILED}와 환경 ID로 바꾸고, 접속 정보는 메시지·로그에 남기지 않는다.</li>
 * </ul>
 */
@Component
public class DataSourceRegistry implements DisposableBean {

    /** 지원하는 환경 ID(표시 순서). */
    public static final List<String> ENVIRONMENTS = List.of("dev", "test", "prod");

    /** 쿼리 타임아웃(초, PRD NFR-02). 모든 Statement에 적용한다. */
    public static final int QUERY_TIMEOUT_SECONDS = 30;

    static final int MAX_POOL_SIZE = 2;
    static final long CONNECTION_TIMEOUT_MS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistry.class);

    private final DataMatchProperties properties;
    private final Function<HikariConfig, DataSource> factory;
    private final ConcurrentMap<String, DataSource> pools = new ConcurrentHashMap<>();

    @Autowired
    public DataSourceRegistry(DataMatchProperties properties) {
        this(properties, HikariDataSource::new);
    }

    DataSourceRegistry(DataMatchProperties properties, Function<HikariConfig, DataSource> factory) {
        this.properties = properties;
        this.factory = factory;
    }

    /** 접속 정보가 설정된 환경 ID 목록(dev, test, prod 순). */
    public List<String> availableEnvironments() {
        return ENVIRONMENTS.stream().filter(this::isAvailable).collect(Collectors.toList());
    }

    public boolean isAvailable(String env) {
        if (env == null || !ENVIRONMENTS.contains(env)) {
            return false;
        }
        EnvironmentProperties p = properties.getEnvironments().get(env);
        return p != null && p.isConfigured();
    }

    /**
     * 환경의 연결로 작업을 수행한 뒤 롤백하고 닫는다. 연결 획득 실패는 {@code DB_CONNECTION_FAILED}이고,
     * 작업 중 발생한 {@link SQLException}은 호출자가 분류하도록 그대로 던진다.
     */
    public <T> T withConnection(String env, ConnectionCallback<T> callback) throws SQLException {
        Connection connection = connect(env);
        try {
            return callback.doInConnection(connection);
        } finally {
            try {
                connection.rollback();
            } catch (SQLException e) {
                log.debug("rollback failed: env={}, sqlState={}", env, e.getSQLState());
            }
            try {
                connection.close();
            } catch (SQLException e) {
                log.debug("close failed: env={}, sqlState={}", env, e.getSQLState());
            }
        }
    }

    private Connection connect(String env) {
        DataSource dataSource = dataSource(env);
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            // 드라이버 메시지에는 계정·URL이 들어갈 수 있어 SQLState와 오류 번호만 남긴다.
            log.warn("DB connection failed: env={}, sqlState={}, vendorCode={}", env, e.getSQLState(), e.getErrorCode());
            throw new DataMatchException(ErrorCode.DB_CONNECTION_FAILED, "DB connection failed (env=" + env + ")",
                    Map.of("env", env));
        }
    }

    DataSource dataSource(String env) {
        if (!isAvailable(env)) {
            throw new DataMatchException(ErrorCode.ENV_NOT_CONFIGURED, "environment not configured");
        }
        return pools.computeIfAbsent(env, id -> factory.apply(configFor(id, properties.getEnvironments().get(id))));
    }

    static HikariConfig configFor(String env, EnvironmentProperties p) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("datamatch-" + env);
        config.setJdbcUrl(p.getUrl());
        config.setUsername(p.getUsername());
        config.setPassword(p.getPassword());
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setReadOnly(true);
        config.setAutoCommit(false);
        // 풀 생성 시 연결하지 않는다. 연결 실패는 첫 getConnection()에서 DB_CONNECTION_FAILED가 된다.
        config.setInitializationFailTimeout(-1);
        return config;
    }

    /** 서버 종료 시 만든 풀을 모두 닫는다. */
    @Override
    public void destroy() {
        pools.values().forEach(ds -> {
            if (ds instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) ds).close();
                } catch (Exception e) {
                    log.debug("pool close failed: {}", e.getClass().getSimpleName());
                }
            }
        });
        pools.clear();
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T doInConnection(Connection connection) throws SQLException;
    }
}
