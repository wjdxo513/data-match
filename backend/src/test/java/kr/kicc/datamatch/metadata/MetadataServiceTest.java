package kr.kicc.datamatch.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.PkSupport.UnsupportedReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** DB-02: 테이블 검색·PK 판정·컬럼 조회와 오류 분류(기술 설계서 6.2절, TD-07). */
class MetadataServiceTest {

    private MetadataRepository repository;
    private MetadataService service;

    @BeforeEach
    void setUp() {
        repository = mock(MetadataRepository.class);
        DataMatchProperties props = new DataMatchProperties();
        props.getEnvironments().put("test", new EnvironmentProperties("jdbc:oracle:thin:@//h:1/S", "ro", "pw"));
        service = new MetadataService(repository, new DataSourceRegistry(props));
    }

    private static TableName t(String name) {
        return new TableName("OWN", name);
    }

    private static void assertCode(Throwable e, ErrorCode expected) {
        assertThat(e).isInstanceOf(DataMatchException.class);
        assertThat(((DataMatchException) e).code()).isEqualTo(expected);
    }

    @Test
    void shortQueryIsInvalidInputWithoutQuerying() {
        for (String q : new String[] {"", " ", "A", " B ", null}) {
            assertThatThrownBy(() -> service.searchTables("test", q)).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void unconfiguredEnvironmentIsEnvNotConfigured() {
        assertThatThrownBy(() -> service.searchTables("prod", "TB")).satisfies(e -> assertCode(e, ErrorCode.ENV_NOT_CONFIGURED));
        assertThatThrownBy(() -> service.describeTable("dev", "O", "T")).satisfies(e -> assertCode(e, ErrorCode.ENV_NOT_CONFIGURED));
        verifyNoInteractions(repository);
    }

    @Test
    void likePatternIsCaseInsensitiveAndEscapesWildcards() throws Exception {
        assertThat(MetadataService.likePattern("tb_pay%x\\y")).isEqualTo("%TB\\_PAY\\%X\\\\Y%");
        assertThat(MetadataService.likePattern("payzone.tb")).isEqualTo("%PAYZONE.TB%");
        when(repository.searchTables(anyString(), anyString(), anyInt())).thenReturn(List.of());
        service.searchTables("test", "  a_b ");
        verify(repository).searchTables("test", "%A\\_B%", 31);
        verify(repository, never()).findPrimaryKeys(anyString(), anyList());
    }

    @Test
    void moreThanThirtyCandidatesAreTruncatedAndPkIsJudgedOnlyForReturnedOnes() throws Exception {
        List<TableName> rows = IntStream.rangeClosed(1, 31).mapToObj(i -> t(String.format("T%02d", i))).collect(Collectors.toList());
        when(repository.searchTables(anyString(), anyString(), anyInt())).thenReturn(rows);
        when(repository.findPrimaryKeys(anyString(), anyList())).thenReturn(Map.of());
        TableSearchResult result = service.searchTables("test", "T0");
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getItems()).hasSize(30);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TableName>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).findPrimaryKeys(eq("test"), captor.capture());
        assertThat(captor.getValue()).hasSize(30).doesNotContain(t("T31"));
    }

    @Test
    void candidatesCarryPkJudgement() throws Exception {
        List<TableName> rows = List.of(t("STR"), t("NUM"), t("COMP"), t("NOPK"), t("DATEPK"));
        when(repository.searchTables(anyString(), anyString(), anyInt())).thenReturn(rows);
        when(repository.findPrimaryKeys(anyString(), anyList())).thenReturn(Map.of(
                t("STR"), List.of(new PkColumn("TRX_NO", "VARCHAR2")),
                t("NUM"), List.of(new PkColumn("TRX_ID", "NUMBER")),
                t("COMP"), List.of(new PkColumn("K1", "VARCHAR2"), new PkColumn("K2", "NUMBER")),
                t("DATEPK"), List.of(new PkColumn("D", "DATE"))));
        TableSearchResult result = service.searchTables("test", "OWN");
        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getItems()).containsExactly(
                new TableCandidate("OWN", "STR", true, null, "TRX_NO"),
                new TableCandidate("OWN", "NUM", true, null, "TRX_ID"),
                new TableCandidate("OWN", "COMP", false, UnsupportedReason.COMPOSITE_PK, null),
                new TableCandidate("OWN", "NOPK", false, UnsupportedReason.NO_PK, null),
                new TableCandidate("OWN", "DATEPK", false, UnsupportedReason.UNSUPPORTED_PK_TYPE, null));
    }

    @Test
    void describeTableReturnsColumnsInOrderWithPk() throws Exception {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("TRX_ID", "NUMBER", 22, 15, 0, 0),
                new ColumnMeta("APPR_MSG", "CLOB", 4000, null, null, 0));
        when(repository.findColumns("test", t("NUM"))).thenReturn(columns);
        when(repository.findPrimaryKeys("test", List.of(t("NUM")))).thenReturn(Map.of(t("NUM"), List.of(new PkColumn("TRX_ID", "NUMBER"))));
        TableMetadata meta = service.describeTable("test", "OWN", "NUM");
        assertThat(meta.getPkColumn()).isEqualTo("TRX_ID");
        assertThat(meta.isNumericPk()).isTrue();
        assertThat(meta.getColumns()).extracting(ColumnMeta::getName).containsExactly("TRX_ID", "APPR_MSG");
    }

    @Test
    void unknownTableIsRejectedAndUnsupportedPkIsTableNotSupported() throws Exception {
        when(repository.findColumns(anyString(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.describeTable("test", "OWN", "NOPE")).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.describeTable("test", " ", "T")).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));

        when(repository.findColumns("test", t("COMP"))).thenReturn(List.of(new ColumnMeta("K1", "VARCHAR2", 10, null, null, 10)));
        when(repository.findPrimaryKeys(anyString(), anyList())).thenReturn(Map.of(
                t("COMP"), List.of(new PkColumn("K1", "VARCHAR2"), new PkColumn("K2", "VARCHAR2"))));
        assertThatThrownBy(() -> service.describeTable("test", "OWN", "COMP"))
                .satisfies(e -> assertCode(e, ErrorCode.TABLE_NOT_SUPPORTED))
                .satisfies(e -> assertThat(((DataMatchException) e).details()).isEqualTo(Map.of("reason", "COMPOSITE_PK")));
    }

    @Test
    void unknownColumnsAreRejected() {
        TableMetadata meta = new TableMetadata("OWN", "T", "ID", "VARCHAR2",
                List.of(new ColumnMeta("ID", "VARCHAR2", 10, null, null, 10), new ColumnMeta("MSG", "CLOB", 4000, null, null, 0)));
        assertThat(meta.requireColumns(List.of("MSG", "ID"))).extracting(ColumnMeta::getName).containsExactly("MSG", "ID");
        assertThat(meta.isNumericPk()).isFalse();
        assertThatThrownBy(() -> meta.requireColumns(List.of("ID", "id"))).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> meta.requireColumns(List.of("ID\" FROM DUAL --"))).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> meta.requireColumns(List.of())).satisfies(e -> assertCode(e, ErrorCode.INVALID_INPUT));
    }

    @Test
    void sqlErrorsAreClassified() throws Exception {
        when(repository.searchTables(anyString(), anyString(), anyInt()))
                .thenThrow(new SQLException("ORA-00942: table or view does not exist", "42000", 942))
                .thenThrow(new SQLException("IO Error", "08006", 17002))
                .thenThrow(new SQLTimeoutException("ORA-01013", "72000", 1013));
        assertThatThrownBy(() -> service.searchTables("test", "TB"))
                .satisfies(e -> assertCode(e, ErrorCode.DB_QUERY_FAILED))
                .hasMessageNotContaining("ORA-00942");
        assertThatThrownBy(() -> service.searchTables("test", "TB")).satisfies(e -> assertCode(e, ErrorCode.DB_CONNECTION_FAILED));
        assertThatThrownBy(() -> service.searchTables("test", "TB")).satisfies(e -> assertCode(e, ErrorCode.QUERY_TIMEOUT));
        when(repository.findColumns(anyString(), any())).thenThrow(new SQLException("x", "42000", 1031));
        assertThatThrownBy(() -> service.describeTable("test", "O", "T")).satisfies(e -> assertCode(e, ErrorCode.DB_QUERY_FAILED));
    }

    @Test
    void connectionFailureFromRegistryPassesThrough() throws Exception {
        when(repository.searchTables(anyString(), anyString(), anyInt()))
                .thenThrow(new DataMatchException(ErrorCode.DB_CONNECTION_FAILED, "DB connection failed (env=test)", Map.of("env", "test")));
        assertThatThrownBy(() -> service.searchTables("test", "TB")).satisfies(e -> assertCode(e, ErrorCode.DB_CONNECTION_FAILED));
    }
}
