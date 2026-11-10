package kr.kicc.datamatch.compare;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.SqlErrorClassifier;
import kr.kicc.datamatch.metadata.ColumnMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * {@link RowRepository}의 Oracle 구현. 청크의 키를 중복 제거해 한 번에 조회하고(IN 목록은 청크 크기 이하),
 * 쿼리 타임아웃 30초와 페치 크기 100을 적용한다. PK가 NUMBER이면 {@link BigDecimal}로 바인딩하고 수치로 매칭한다.
 */
@Repository
public class OracleRowRepository implements RowRepository {

    static final int FETCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(OracleRowRepository.class);

    private final DataSourceRegistry registry;
    private final int maxKeys;
    private final int queryTimeoutSeconds;

    @Autowired
    public OracleRowRepository(DataSourceRegistry registry, DataMatchProperties properties) {
        this(registry, properties.getCompare().getChunkSize(), DataSourceRegistry.QUERY_TIMEOUT_SECONDS);
    }

    OracleRowRepository(DataSourceRegistry registry, int maxKeys, int queryTimeoutSeconds) {
        this.registry = registry;
        this.maxKeys = maxKeys;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public Map<String, Map<String, String>> fetchRows(String env, RowQuery query, List<String> keys, CancellationToken token) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(keys));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        if (distinct.size() > maxKeys) {
            throw new IllegalArgumentException("too many keys for one chunk: " + distinct.size());
        }
        if (token.isCancelled()) {
            throw new QueryCancelledException();
        }
        try {
            return registry.withConnection(env, c -> {
                try (PreparedStatement ps = c.prepareStatement(query.sql(distinct.size()))) {
                    ps.setQueryTimeout(queryTimeoutSeconds);
                    ps.setFetchSize(FETCH_SIZE);
                    for (int i = 0; i < distinct.size(); i++) {
                        if (query.isNumericPk()) {
                            ps.setBigDecimal(i + 1, new BigDecimal(distinct.get(i)));
                        } else {
                            ps.setString(i + 1, distinct.get(i));
                        }
                    }
                    if (!token.register(ps)) {
                        throw new QueryCancelledException();
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        return readRows(rs, query, distinct, token);
                    } finally {
                        token.unregister(ps);
                    }
                }
            });
        } catch (SQLException e) {
            SqlErrorClassifier.Kind kind = SqlErrorClassifier.classify(e, token.isCancelled());
            if (kind == SqlErrorClassifier.Kind.CANCELLED) {
                throw new QueryCancelledException();
            }
            log.warn("row query failed: env={}, kind={}, sqlState={}, vendorCode={}", env, kind, e.getSQLState(), e.getErrorCode());
            throw SqlErrorClassifier.toException(kind, env, Map.of("env", env));
        }
    }

    private static Map<String, Map<String, String>> readRows(ResultSet rs, RowQuery query, List<String> keys,
                                                             CancellationToken token) throws SQLException {
        Map<String, List<String>> inputByKey = new HashMap<>();
        for (String k : keys) {
            inputByKey.computeIfAbsent(matchKey(k, query.isNumericPk()), x -> new ArrayList<>()).add(k);
        }
        ColumnKind pkKind = ColumnKind.of(query.getPkColumn().getDataType());
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        while (rs.next()) {
            if (token.isCancelled()) {
                throw new QueryCancelledException();
            }
            String pk = ValueConverter.read(rs, 1, pkKind);
            Map<String, String> row = new LinkedHashMap<>();
            int index = 2;
            for (ColumnMeta c : query.getColumns()) {
                row.put(c.getName(), ValueConverter.read(rs, index++, ColumnKind.of(c.getDataType())));
            }
            for (String input : inputByKey.getOrDefault(matchKey(pk, query.isNumericPk()), List.of())) {
                rows.put(input, row);
            }
        }
        return rows;
    }

    /** NUMBER PK는 표기(예: {@code 1}, {@code 1.0})와 무관하게 수치로 매칭한다. */
    static String matchKey(String key, boolean numeric) {
        if (key == null || !numeric) {
            return key;
        }
        return new BigDecimal(key).stripTrailingZeros().toPlainString();
    }
}
