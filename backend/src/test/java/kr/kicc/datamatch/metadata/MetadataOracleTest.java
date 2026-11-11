package kr.kicc.datamatch.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.it.OracleIntegrationTest;
import kr.kicc.datamatch.it.OracleTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB-02 [Oracle]: 로컬 Oracle 테스트 스키마(SETUP-05)에서 실제 데이터 사전 조회를 확인한다. A쪽=RO_A(test), B쪽=RO_B(prod). */
@OracleIntegrationTest
class MetadataOracleTest {

    private static final String OWNER = "DM_IT_OWNER";

    private DataSourceRegistry registry;
    private MetadataService service;

    @BeforeEach
    void setUp() {
        DataMatchProperties p = new DataMatchProperties();
        p.getEnvironments().put("test", new EnvironmentProperties(OracleTestDatabase.url(),
                OracleTestDatabase.user("RO_A"), OracleTestDatabase.password("RO_A")));
        p.getEnvironments().put("prod", new EnvironmentProperties(OracleTestDatabase.url(),
                OracleTestDatabase.user("RO_B"), OracleTestDatabase.password("RO_B")));
        registry = new DataSourceRegistry(p);
        service = new MetadataService(new OracleMetadataRepository(registry), registry);
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    private TableCandidate only(String env, String table) {
        List<TableCandidate> items = service.searchTables(env, OWNER + "." + table).getItems().stream()
                .filter(c -> c.getName().equals(table)).collect(Collectors.toList());
        assertThat(items).as(table).hasSize(1);
        return items.get(0);
    }

    @Test
    void moreThanThirtyMatchesAreTruncatedToThirty() {
        TableSearchResult result = service.searchTables("test", "dm_it_search");
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getItems()).hasSize(30)
                .allSatisfy(c -> assertThat(c.getName()).startsWith("DM_IT_SEARCH_"));
        assertThat(result.getItems().get(0).getName()).isEqualTo("DM_IT_SEARCH_01");
    }

    @Test
    void underscoreIsSearchedLiterally() {
        TableSearchResult result = service.searchTables("test", "IT_A_B");
        assertThat(result.getItems()).extracting(TableCandidate::getName).contains("DM_IT_A_B").doesNotContain("DM_IT_AXB");
        assertThat(service.searchTables("test", "IT_A%B").getItems()).isEmpty();
    }

    @Test
    void allPkKindsAreCandidatesAndDefaultKeysFollowPk() {
        for (String table : new String[] {"DM_IT_TRX_STR", "DM_IT_TRX_NUM", "DM_IT_COMPOSITE", "DM_IT_NOPK", "DM_IT_DATEPK"}) {
            assertThat(only("test", table)).isEqualTo(new TableCandidate(OWNER, table));
        }
        assertThat(service.describeTable("test", OWNER, "DM_IT_TRX_STR").defaultKeyColumn()).isEqualTo("TRX_NO");
        assertThat(service.describeTable("test", OWNER, "DM_IT_TRX_NUM").defaultKeyColumn()).isEqualTo("TRX_ID");
        TableMetadata comp = service.describeTable("test", OWNER, "DM_IT_COMPOSITE");
        assertThat(comp.getPkColumns()).containsExactly("K1", "K2");
        assertThat(comp.defaultKeyColumn()).isNull();
        assertThat(service.describeTable("test", OWNER, "DM_IT_NOPK").getPkColumns()).isEmpty();
        TableMetadata date = service.describeTable("test", OWNER, "DM_IT_DATEPK");
        assertThat(date.getPkColumns()).containsExactly("D");
        assertThat(date.defaultKeyColumn()).isNull();
        assertThat(date.column("VAL").orElseThrow().isKeyable()).isTrue();
    }

    @Test
    void columnsAreReturnedInColumnOrderWithTypes() {
        TableMetadata types = service.describeTable("test", OWNER, "DM_IT_TYPES");
        assertThat(types.defaultKeyColumn()).isEqualTo("ID");
        assertThat(types.getColumns()).extracting(ColumnMeta::getName).containsExactly(
                "ID", "C_CHAR", "C_VARCHAR", "C_NCHAR", "C_NVARCHAR", "C_NUMBER", "C_NUMSCALE", "C_FLOAT", "C_BDOUBLE",
                "C_DATE", "C_TS", "C_TSTZ", "C_TSLTZ", "C_CLOB", "C_NCLOB", "C_BLOB", "C_RAW", "C_INTERVAL");
        assertThat(types.getColumns()).extracting(ColumnMeta::displayType).startsWith(
                "VARCHAR2(10)", "CHAR(5)", "VARCHAR2(50)", "NCHAR(5)", "NVARCHAR2(50)", "NUMBER", "NUMBER(10,2)");
        assertThat(types.column("C_CLOB").orElseThrow().isClob()).isTrue();
        assertThat(types.column("C_NCLOB").orElseThrow().isClob()).isFalse();
        assertThat(types.column("C_TS").orElseThrow().displayType()).isEqualTo("TIMESTAMP(9)");

        TableMetadata num = service.describeTable("test", OWNER, "DM_IT_TRX_NUM");
        assertThat(num.column(num.defaultKeyColumn()).orElseThrow().isNumber()).isTrue();
        assertThat(num.column("TRX_ID").orElseThrow().displayType()).isEqualTo("NUMBER(15)");
    }

    @Test
    void unknownOrUngrantedTablesAreNotVisible() {
        assertThat(service.searchTables("test", "DM_IT_NO_B_GRANT").getItems()).hasSize(1);
        assertThat(service.searchTables("prod", "DM_IT_NO_B_GRANT").getItems()).isEmpty();
        for (String[] t : new String[][] {{"prod", "DM_IT_NO_B_GRANT"}, {"test", "DM_IT_NOT_EXISTS"}, {"test", "dm_it_trx_str"}}) {
            assertThatThrownBy(() -> service.describeTable(t[0], OWNER, t[1]))
                    .as(t[0] + " " + t[1])
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    /** TA-13: 시스템 스키마가 검색 결과에 포함되는지 현재 동작을 기록한다(필터링하지 않음). */
    @Test
    void recordsWhetherSystemSchemasAppear() {
        TableSearchResult result = service.searchTables("test", "DUAL");
        List<String> owners = result.getItems().stream().map(TableCandidate::getOwner).distinct().collect(Collectors.toList());
        System.out.println("[TA-13] search 'DUAL' as RO_A: items=" + result.getItems().size() + ", truncated="
                + result.isTruncated() + ", owners=" + owners);
        assertThat(owners).doesNotContain(OWNER);
    }
}
