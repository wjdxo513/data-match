package kr.kicc.datamatch.metadata;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.error.SqlErrorClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 테이블 검색과 컬럼·PK·비교 키 조회(기술 설계서 5장 FR-02·03, 6.2절, TD-07). 메타데이터는 항상 A쪽 환경에서 조회한다(PA-15).
 * 전체 메타데이터를 캐시하지 않는다.
 */
@Service
public class MetadataService {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_CANDIDATES = 30;

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final MetadataRepository repository;
    private final DataSourceRegistry registry;

    public MetadataService(MetadataRepository repository, DataSourceRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    public TableSearchResult searchTables(String env, String query) {
        requireEnvironment(env);
        String q = query == null ? "" : query.strip();
        if (q.length() < MIN_QUERY_LENGTH) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "query must be at least 2 characters");
        }
        try {
            List<TableName> rows = repository.searchTables(env, likePattern(q), MAX_CANDIDATES + 1);
            boolean truncated = rows.size() > MAX_CANDIDATES;
            List<TableName> tables = truncated ? rows.subList(0, MAX_CANDIDATES) : rows;
            // 모든 테이블을 선택할 수 있으므로(PRD PA-10) 후보에는 PK 판정을 하지 않는다.
            List<TableCandidate> items = tables.stream()
                    .map(t -> new TableCandidate(t.getOwner(), t.getName()))
                    .collect(Collectors.toList());
            return new TableSearchResult(items, truncated);
        } catch (SQLException e) {
            throw translate(env, e);
        }
    }

    /**
     * 테이블의 컬럼과 PK 구성을 조회한다. 메타데이터에 없는(또는 권한이 없는) 테이블은 {@code INVALID_INPUT}이다.
     * 비교 키 기본값과 검증은 {@link TableMetadata}가 맡는다(PRD PA-10·19).
     */
    public TableMetadata describeTable(String env, String owner, String table) {
        requireEnvironment(env);
        if (isBlank(owner) || isBlank(table)) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "owner and table are required");
        }
        TableName name = new TableName(owner, table);
        try {
            List<ColumnMeta> columns = repository.findColumns(env, name);
            if (columns.isEmpty()) {
                throw new DataMatchException(ErrorCode.INVALID_INPUT, "unknown table");
            }
            List<PkColumn> pk = repository.findPrimaryKeys(env, List.of(name)).getOrDefault(name, List.of());
            List<String> pkColumns = pk.stream().map(PkColumn::getName).collect(Collectors.toList());
            return new TableMetadata(owner, table, pkColumns, List.copyOf(columns));
        } catch (SQLException e) {
            throw translate(env, e);
        }
    }

    private void requireEnvironment(String env) {
        if (!registry.isAvailable(env)) {
            throw new DataMatchException(ErrorCode.ENV_NOT_CONFIGURED, "environment not configured");
        }
    }

    /** 대소문자를 무시하고 부분 일치로 찾으며 {@code %}, {@code _}, {@code \}는 리터럴로 검색한다. */
    static String likePattern(String query) {
        String escaped = query.toUpperCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** 조회 중 오류 분류(기술 설계서 6.5절): 연결 실패, 타임아웃, 그 밖의 DB 오류(TA-05). 메타데이터 조회에는 중지가 없다. */
    private static DataMatchException translate(String env, SQLException e) {
        SqlErrorClassifier.Kind kind = SqlErrorClassifier.classify(e, false);
        log.warn("metadata query failed: env={}, kind={}, sqlState={}, vendorCode={}", env, kind, e.getSQLState(), e.getErrorCode());
        return SqlErrorClassifier.toException(kind, env, Map.of("env", env));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
