package kr.kicc.datamatch.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.kicc.datamatch.config.DataSourceRegistry;
import org.springframework.stereotype.Repository;

/** {@link MetadataRepository}의 Oracle 구현. 모든 값은 바인드 변수로 전달한다(NFR-04). */
@Repository
public class OracleMetadataRepository implements MetadataRepository {

    // FETCH FIRST는 12c 이상이어서 ROWNUM을 쓴다(기술 설계서 6.2절).
    static final String SEARCH_SQL = "SELECT OWNER, TABLE_NAME FROM ("
            + "SELECT OWNER, TABLE_NAME FROM ALL_TABLES"
            + " WHERE UPPER(OWNER || '.' || TABLE_NAME) LIKE ? ESCAPE '\\'"
            + " ORDER BY OWNER, TABLE_NAME"
            + ") WHERE ROWNUM <= ?";

    static final String PK_SQL_PREFIX = "SELECT c.OWNER, c.TABLE_NAME, cc.COLUMN_NAME, tc.DATA_TYPE"
            + " FROM ALL_CONSTRAINTS c"
            + " JOIN ALL_CONS_COLUMNS cc ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME"
            + " AND cc.TABLE_NAME = c.TABLE_NAME"
            + " JOIN ALL_TAB_COLUMNS tc ON tc.OWNER = c.OWNER AND tc.TABLE_NAME = c.TABLE_NAME"
            + " AND tc.COLUMN_NAME = cc.COLUMN_NAME"
            + " WHERE c.CONSTRAINT_TYPE = 'P' AND (";

    static final String COLUMNS_SQL = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, CHAR_LENGTH"
            + " FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID";

    private final DataSourceRegistry registry;

    public OracleMetadataRepository(DataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<TableName> searchTables(String env, String likePattern, int limit) throws SQLException {
        return registry.withConnection(env, c -> {
            try (PreparedStatement ps = prepare(c, SEARCH_SQL)) {
                ps.setString(1, likePattern);
                ps.setInt(2, limit);
                List<TableName> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new TableName(rs.getString(1), rs.getString(2)));
                    }
                }
                return result;
            }
        });
    }

    @Override
    public Map<TableName, List<PkColumn>> findPrimaryKeys(String env, List<TableName> tables) throws SQLException {
        if (tables.isEmpty()) {
            return Collections.emptyMap();
        }
        return registry.withConnection(env, c -> {
            try (PreparedStatement ps = prepare(c, pkSql(tables.size()))) {
                int i = 1;
                for (TableName t : tables) {
                    ps.setString(i++, t.getOwner());
                    ps.setString(i++, t.getName());
                }
                Map<TableName, List<PkColumn>> result = new LinkedHashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.computeIfAbsent(new TableName(rs.getString(1), rs.getString(2)), k -> new ArrayList<>())
                                .add(new PkColumn(rs.getString(3), rs.getString(4)));
                    }
                }
                return result;
            }
        });
    }

    @Override
    public List<ColumnMeta> findColumns(String env, TableName table) throws SQLException {
        return registry.withConnection(env, c -> {
            try (PreparedStatement ps = prepare(c, COLUMNS_SQL)) {
                ps.setString(1, table.getOwner());
                ps.setString(2, table.getName());
                List<ColumnMeta> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ColumnMeta(rs.getString(1), rs.getString(2), intOrNull(rs, 3), intOrNull(rs, 4),
                                intOrNull(rs, 5), intOrNull(rs, 6)));
                    }
                }
                return result;
            }
        });
    }

    static String pkSql(int tableCount) {
        StringBuilder sb = new StringBuilder(PK_SQL_PREFIX);
        for (int i = 0; i < tableCount; i++) {
            sb.append(i == 0 ? "" : " OR ").append("(c.OWNER = ? AND c.TABLE_NAME = ?)");
        }
        return sb.append(") ORDER BY c.OWNER, c.TABLE_NAME, cc.POSITION").toString();
    }

    private static PreparedStatement prepare(Connection c, String sql) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setQueryTimeout(DataSourceRegistry.QUERY_TIMEOUT_SECONDS);
        ps.setFetchSize(100);
        return ps;
    }

    private static Integer intOrNull(ResultSet rs, int index) throws SQLException {
        int v = rs.getInt(index);
        return rs.wasNull() ? null : v;
    }
}
