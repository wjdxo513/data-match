package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.it.OracleIntegrationTest;
import kr.kicc.datamatch.it.OracleTestDatabase;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.OracleMetadataRepository;
import kr.kicc.datamatch.metadata.TableMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB-03 [Oracle]: 로컬 Oracle 테스트 스키마에서 실제 변환·바인딩·누락·타임아웃·취소를 확인한다. */
@OracleIntegrationTest
class RowRepositoryOracleTest {

    private static final String OWNER = "DM_IT_OWNER";

    private DataSourceRegistry registry;
    private MetadataService metadata;
    private OracleRowRepository repository;

    @BeforeEach
    void setUp() {
        DataMatchProperties p = new DataMatchProperties();
        p.getEnvironments().put("test", new EnvironmentProperties(OracleTestDatabase.url(),
                OracleTestDatabase.user("RO_A"), OracleTestDatabase.password("RO_A")));
        registry = new DataSourceRegistry(p);
        metadata = new MetadataService(new OracleMetadataRepository(registry), registry);
        repository = new OracleRowRepository(registry, p);
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    @Test
    void convertsAllTypesPerTechnicalDesignTable() {
        TableMetadata types = metadata.describeTable("test", OWNER, "DM_IT_TYPES");
        List<String> cols = List.of("C_CHAR", "C_VARCHAR", "C_NCHAR", "C_NVARCHAR", "C_NUMBER", "C_NUMSCALE", "C_FLOAT",
                "C_BDOUBLE", "C_DATE", "C_TS", "C_TSTZ", "C_TSLTZ", "C_CLOB", "C_NCLOB", "C_BLOB", "C_RAW", "C_INTERVAL");
        Map<String, Map<String, String>> rows = repository.fetchRows("test", SqlBuilder.build(types, cols),
                List.of("T1", "T2", "T3", "T4"), new CancellationToken());
        Map<String, String> t1 = rows.get("T1");
        System.out.println("[DB-03] DM_IT_TYPES T1 = " + t1);
        assertThat(t1).containsEntry("C_CHAR", "ab   ")
                .containsEntry("C_VARCHAR", "Hello World")
                .containsEntry("C_NVARCHAR", "한글 텍스트")
                .containsEntry("C_NUMBER", "1.1")
                .containsEntry("C_NUMSCALE", "1234.5")
                .containsEntry("C_FLOAT", "3.25")
                .containsEntry("C_DATE", "2026-11-10 13:45:30")
                .containsEntry("C_TS", "2026-11-10 13:45:30.123456789")
                .containsEntry("C_TSTZ", "2026-11-10 13:45:30.123456789 +09:00")
                .containsEntry("C_TSLTZ", "2026-11-10 13:45:30.000000000")
                .containsEntry("C_CLOB", "긴 텍스트 값")
                .containsEntry("C_NCLOB", "한글 NCLOB")
                .containsEntry("C_BLOB", "DEADBEEF")
                .containsEntry("C_RAW", "0A0B0C");
        assertThat(t1.get("C_BDOUBLE")).startsWith("1.5");
        // INTERVAL은 6.4의 "그 밖의 타입: getString"이며 ojdbc8은 '1 2:3:4.0' 형식을 돌려준다.
        assertThat(t1.get("C_INTERVAL")).isEqualTo("1 2:3:4.0");
        assertThat(t1.get("C_NCHAR")).isEqualTo("가나   ");
        assertThat(rows.get("T2")).isEqualTo(t1);
        assertThat(rows.get("T3")).containsOnlyKeys(cols.toArray(new String[0])).allSatisfy((k, v) -> assertThat(v).as(k).isNull());
        Map<String, String> t4 = rows.get("T4");
        for (String c : new String[] {"C_CHAR", "C_VARCHAR", "C_NUMBER", "C_DATE", "C_TS", "C_TSTZ", "C_CLOB", "C_BLOB", "C_RAW", "C_INTERVAL"}) {
            assertThat(t4.get(c)).as(c).isNotEqualTo(t1.get(c));
        }
    }

    @Test
    void bindsStringAndNumberPksAndOmitsMissingKeys() {
        TableMetadata str = metadata.describeTable("test", OWNER, "DM_IT_TRX_STR");
        Map<String, Map<String, String>> s = repository.fetchRows("test", SqlBuilder.build(str, List.of("ORDER_NO", "AMOUNT")),
                List.of("A000001", "NOPE", "B000002"), new CancellationToken());
        assertThat(s).containsOnlyKeys("A000001", "B000002");
        assertThat(s.get("A000001")).containsEntry("ORDER_NO", "ORD-1").containsEntry("AMOUNT", "100");

        TableMetadata num = metadata.describeTable("test", OWNER, "DM_IT_TRX_NUM");
        Map<String, Map<String, String>> n = repository.fetchRows("test", SqlBuilder.build(num, List.of("ORDER_NO", "AMOUNT")),
                List.of("1", "2.0", "99999999"), new CancellationToken());
        assertThat(n).containsOnlyKeys("1", "2.0");
        assertThat(n.get("1")).containsEntry("ORDER_NO", "ORD-1").containsEntry("AMOUNT", "1.5");
    }

    @Test
    void slowQueryTimesOut() {
        TableMetadata slow = metadata.describeTable("test", OWNER, "DM_IT_SLOW");
        OracleRowRepository shortTimeout = new OracleRowRepository(registry, 100, 3);
        long start = System.nanoTime();
        assertThatThrownBy(() -> shortTimeout.fetchRows("test", SqlBuilder.build(slow, List.of("SLOW_COL")),
                List.of("S1", "S2", "S3", "S4", "S5"), new CancellationToken()))
                .isInstanceOf(DataMatchException.class)
                .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.QUERY_TIMEOUT));
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[DB-03] timeout(3s) raised after " + ms + " ms");
        assertThat(ms).isLessThan(9_000);
    }

    @Test
    void cancelStopsRunningQueryWithOra01013() throws Exception {
        TableMetadata slow = metadata.describeTable("test", OWNER, "DM_IT_SLOW");
        CancellationToken token = new CancellationToken();
        CompletableFuture<Throwable> result = CompletableFuture.supplyAsync(() -> {
            try {
                repository.fetchRows("test", SqlBuilder.build(slow, List.of("SLOW_COL")), List.of("S1", "S2", "S3", "S4", "S5"), token);
                return null;
            } catch (Throwable t) {
                return t;
            }
        });
        Thread.sleep(1500);
        long start = System.nanoTime();
        token.cancel();
        Throwable thrown = result.get(15, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[DB-03] cancel returned after " + ms + " ms: " + thrown);
        assertThat(thrown).isInstanceOf(QueryCancelledException.class);
        assertThat(ms).isLessThan(5_000);
    }
}
