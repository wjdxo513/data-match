package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.it.OracleIntegrationTest;
import kr.kicc.datamatch.it.OracleTestDatabase;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.OracleMetadataRepository;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import kr.kicc.datamatch.safedb.FakeDecryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-06 [Oracle]: 실제 RowRepository와 가짜 Decryptor로 1,000쌍 파이프라인을 실행한다. A=RO_A(test), B=RO_B(prod). */
@OracleIntegrationTest
class CompareServiceOracleTest {

    private DataSourceRegistry registry;
    private CompareTaskFactory factory;
    private CompareService service;

    @BeforeEach
    void setUp() {
        DataMatchProperties p = new DataMatchProperties();
        p.getEnvironments().put("test", new EnvironmentProperties(OracleTestDatabase.url(),
                OracleTestDatabase.user("RO_A"), OracleTestDatabase.password("RO_A")));
        p.getEnvironments().put("prod", new EnvironmentProperties(OracleTestDatabase.url(),
                OracleTestDatabase.user("RO_B"), OracleTestDatabase.password("RO_B")));
        registry = new DataSourceRegistry(p);
        MetadataService metadata = new MetadataService(new OracleMetadataRepository(registry), registry);
        factory = new CompareTaskFactory(registry, metadata);
        service = new CompareService(new OracleRowRepository(registry, p), new FakeDecryptor(), p);
    }

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    @Test
    void thousandPairPipelineCompletesWithChunkProgress() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            text.append(String.format("A%06d,B%06d%n", i, i));
        }
        CompareTask task = factory.create("test", "prod", "DM_IT_OWNER", "DM_IT_TRX_STR",
                List.of(ColumnSpec.plain("ORDER_NO"), ColumnSpec.plain("AMOUNT"), ColumnSpec.plain("MEMO"),
                        ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), text.toString());
        List<Integer> reports = new ArrayList<>();
        long start = System.nanoTime();
        List<PairResult> results = service.run(task, reports::add, new CancellationToken());
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(results).hasSize(1000);
        assertThat(reports).containsExactly(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000);
        Map<PairStatus, Long> counts = results.stream().collect(Collectors.groupingBy(PairResult::getStatus, Collectors.counting()));
        System.out.println("[BE-06] 1000 pairs in " + ms + " ms, counts=" + counts);
        assertThat(counts.get(PairStatus.MISSING)).isEqualTo(100); // i % 10 == 0 은 B 없음
        assertThat(counts).containsKeys(PairStatus.MATCH, PairStatus.MISMATCH);
        Map<Integer, PairResult> byIndex = results.stream().collect(Collectors.toMap(PairResult::getIndex, Function.identity()));
        assertThat(byIndex.get(10).getMissing()).isEqualTo(MissingSide.B);
        assertThat(byIndex.get(13).getRows()).anySatisfy(r -> assertThat(r.getStatus()).isEqualTo(RowStatus.DECRYPT_ERROR));
        assertThat(byIndex.get(17).getRows()).anySatisfy(r -> assertThat(r.getCause()).isEqualTo(DecryptErrorCause.JSON_INVALID));
        assertThat(byIndex.get(19).getRows()).anySatisfy(r -> assertThat(r.getCause()).isEqualTo(DecryptErrorCause.JSON_NOT_OBJECT));
    }
}
