package kr.kicc.datamatch.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.config.DataSourceRegistry.ConnectionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** DB-02: 메타데이터 SQL이 템플릿 고정이고 검색어·이름은 바인드 변수로만 전달되는지 확인한다(NFR-04). */
class OracleMetadataRepositorySqlTest {

    private Connection connection;
    private PreparedStatement ps;
    private OracleMetadataRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        connection = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        when(registry.withConnection(anyString(), any())).thenAnswer(inv ->
                ((ConnectionCallback<Object>) inv.getArgument(1)).doInConnection(connection));
        repository = new OracleMetadataRepository(registry);
    }

    private String preparedSql() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        return sql.getValue();
    }

    @Test
    void searchBindsPatternAndLimit() throws Exception {
        String pattern = "%X' OR 1=1 --%";
        repository.searchTables("test", pattern, 31);
        String sql = preparedSql();
        assertThat(sql).startsWith("SELECT").contains("FROM ALL_TABLES")
                .contains("LIKE ? ESCAPE '\\'").contains("ROWNUM <= ?").doesNotContain(pattern);
        verify(ps).setString(1, pattern);
        verify(ps).setInt(2, 31);
        verify(ps).setQueryTimeout(30);
    }

    @Test
    void pkQueryBindsOnlyGivenTables() throws Exception {
        repository.findPrimaryKeys("test", List.of(new TableName("O1", "T1"), new TableName("O2", "T2")));
        String sql = preparedSql();
        assertThat(sql).startsWith("SELECT").contains("CONSTRAINT_TYPE = 'P'").doesNotContain("O1").doesNotContain("T2");
        assertThat(sql.split("c\\.OWNER = \\?", -1)).hasSize(3);
        verify(ps).setString(1, "O1");
        verify(ps).setString(2, "T1");
        verify(ps).setString(3, "O2");
        verify(ps).setString(4, "T2");
        assertThat(repository.findPrimaryKeys("test", List.of())).isEmpty();
    }

    @Test
    void columnsQueryOrdersByColumnIdAndBindsNames() throws Exception {
        repository.findColumns("test", new TableName("OWN", "TB"));
        assertThat(preparedSql()).startsWith("SELECT").contains("FROM ALL_TAB_COLUMNS").endsWith("ORDER BY COLUMN_ID");
        verify(ps).setString(1, "OWN");
        verify(ps).setString(2, "TB");
    }
}
