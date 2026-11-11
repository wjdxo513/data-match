package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.config.DataSourceRegistry.ConnectionCallback;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** DB-03: 묶음 조회의 실행 횟수·바인딩·설정·매칭·취소·오류 분류를 목 연결로 확인한다. 실제 Oracle은 RowRepositoryOracleTest. */
class OracleRowRepositoryTest {

    private static final TableMetadata STR = new TableMetadata("O", "T", List.of("ID"), List.of(
            new ColumnMeta("ID", "VARCHAR2", 10, null, null, 10), new ColumnMeta("VAL", "VARCHAR2", 10, null, null, 10)));
    private static final TableMetadata NUM = new TableMetadata("O", "N", List.of("ID"), List.of(
            new ColumnMeta("ID", "NUMBER", 22, 15, 0, 0), new ColumnMeta("VAL", "VARCHAR2", 10, null, null, 10)));

    private Connection connection;
    private PreparedStatement ps;
    private ResultSet rs;
    private OracleRowRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        connection = mock(Connection.class);
        ps = mock(PreparedStatement.class);
        rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        DataSourceRegistry registry = mock(DataSourceRegistry.class);
        when(registry.withConnection(anyString(), any())).thenAnswer(inv ->
                ((ConnectionCallback<Object>) inv.getArgument(1)).doInConnection(connection));
        repository = new OracleRowRepository(registry, new DataMatchProperties());
    }

    @Test
    void oneQueryPerChunkWithDistinctBoundKeysTimeoutAndFetchSize() throws Exception {
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("A1", "A2");
        when(rs.getString(2)).thenReturn("v1", "v2");
        Map<String, List<Map<String, String>>> rows = repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")),
                List.of("A1", "A2", "A1", "A3"), new CancellationToken());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1)).prepareStatement(sql.capture());
        assertThat(sql.getValue()).endsWith("IN (?, ?, ?)").doesNotContain("A1");
        verify(ps).setString(1, "A1");
        verify(ps).setString(2, "A2");
        verify(ps).setString(3, "A3");
        verify(ps).setQueryTimeout(30);
        verify(ps).setFetchSize(100);
        verify(ps, times(1)).executeQuery();
        assertThat(rows).containsOnlyKeys("A1", "A2");
        assertThat(rows.get("A2").get(0)).containsExactly(Map.entry("VAL", "v2"));
    }

    @Test
    void inListNeverExceedsChunkSize() {
        List<String> keys = IntStream.rangeClosed(1, 101).mapToObj(i -> "K" + i).collect(Collectors.toList());
        assertThatThrownBy(() -> repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")), keys, new CancellationToken()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")), List.of(), new CancellationToken())).isEmpty();
    }

    @Test
    void numericPkIsBoundAsBigDecimalAndMatchedNumerically() throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getBigDecimal(1)).thenReturn(new BigDecimal("1"));
        when(rs.getString(2)).thenReturn("v");
        Map<String, List<Map<String, String>>> rows = repository.fetchRows("test", SqlBuilder.build(NUM, "ID", List.of("VAL")),
                List.of("1", "1.0", "2"), new CancellationToken());
        verify(ps).setBigDecimal(1, new BigDecimal("1"));
        verify(ps).setBigDecimal(2, new BigDecimal("1.0"));
        verify(ps).setBigDecimal(3, new BigDecimal("2"));
        assertThat(rows).containsOnlyKeys("1", "1.0");
        assertThat(OracleRowRepository.matchKey("100", true)).isEqualTo("100");
        assertThat(OracleRowRepository.matchKey("1.50", true)).isEqualTo("1.5");
        assertThat(OracleRowRepository.matchKey(" A ", false)).isEqualTo(" A ");
    }

    @Test
    void cancelDuringQueryCallsStatementCancelAndSignalsCancellation() throws Exception {
        CancellationToken token = new CancellationToken();
        when(ps.executeQuery()).thenAnswer(inv -> {
            token.cancel();
            throw new SQLException("ORA-01013: user requested cancel of current operation", "72000", 1013);
        });
        assertThatThrownBy(() -> repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")), List.of("A1"), token))
                .isInstanceOf(QueryCancelledException.class);
        verify(ps).cancel();
    }

    @Test
    void alreadyCancelledTokenRunsNoQuery() throws Exception {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertThatThrownBy(() -> repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")), List.of("A1"), token))
                .isInstanceOf(QueryCancelledException.class);
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void sqlErrorsAreClassified() throws Exception {
        when(ps.executeQuery())
                .thenThrow(new SQLRecoverableException("IO Error", null, 17002))
                .thenThrow(new SQLTimeoutException("ORA-01013", "72000", 1013))
                .thenThrow(new SQLException("ORA-00942 secret detail", "42000", 942));
        ErrorCode[] expected = {ErrorCode.DB_CONNECTION_FAILED, ErrorCode.QUERY_TIMEOUT, ErrorCode.DB_QUERY_FAILED};
        for (ErrorCode code : expected) {
            assertThatThrownBy(() -> repository.fetchRows("prod", SqlBuilder.build(STR, "ID", List.of("VAL")), List.of("A1"), new CancellationToken()))
                    .isInstanceOf(DataMatchException.class)
                    .hasMessageNotContaining("secret")
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(code))
                    .satisfies(e -> assertThat(((DataMatchException) e).details()).isEqualTo(Map.of("env", "prod")));
        }
    }

    @Test
    void tokenCancelsRegisteredStatementOnlyWhileRunning() throws Exception {
        CancellationToken token = new CancellationToken();
        PreparedStatement s = mock(PreparedStatement.class);
        assertThat(token.register(s)).isTrue();
        token.unregister(s);
        token.cancel();
        verify(s, never()).cancel();
        assertThat(token.register(s)).isFalse();

        CancellationToken running = new CancellationToken();
        PreparedStatement t = mock(PreparedStatement.class);
        org.mockito.Mockito.doThrow(new SQLException("closed")).when(t).cancel();
        running.register(t);
        running.cancel();
        verify(t).cancel();
        assertThat(running.isCancelled()).isTrue();
    }

    @Test
    void rowsWithTheSameKeyAreAllReturned() throws Exception {
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getString(1)).thenReturn("C2", "C2", "C1");
        when(rs.getString(2)).thenReturn("x2", "x3", "x1");
        Map<String, List<Map<String, String>>> rows = repository.fetchRows("test", SqlBuilder.build(STR, "ID", List.of("VAL")),
                List.of("C1", "C2"), new CancellationToken());
        assertThat(rows.get("C2")).extracting(r -> r.get("VAL")).containsExactly("x2", "x3");
        assertThat(rows.get("C1")).hasSize(1);
    }
}
