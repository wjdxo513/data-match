package kr.kicc.datamatch.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.kicc.datamatch.compare.RowRepository;
import kr.kicc.datamatch.compare.TestRowRepositories;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.config.RecordingRegistries;
import kr.kicc.datamatch.safedb.Decryptor;
import kr.kicc.datamatch.safedb.FakeDecryptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * INT-01 [Oracle]: 전체 애플리케이션 컨텍스트로 API 흐름을 로컬 Oracle 테스트 스키마(SETUP-05)에서 검증한다.
 * 환경: test=DM_IT_RO_A, prod=DM_IT_RO_B, dev=닫힌 포트(연결 실패). 복호화는 가짜, 쿼리 타임아웃은 3초로 줄인다.
 */
@OracleIntegrationTest
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationOracleTest {

    static final List<String> SQL = new CopyOnWriteArrayList<>();
    private static final String OWNER = "DM_IT_OWNER";

    @DynamicPropertySource
    static void environments(DynamicPropertyRegistry r) {
        String url = OracleTestDatabase.url();
        r.add("datamatch.environments.test.url", () -> url);
        r.add("datamatch.environments.test.username", () -> OracleTestDatabase.user("RO_A"));
        r.add("datamatch.environments.test.password", () -> OracleTestDatabase.password("RO_A"));
        r.add("datamatch.environments.prod.url", () -> url);
        r.add("datamatch.environments.prod.username", () -> OracleTestDatabase.user("RO_B"));
        r.add("datamatch.environments.prod.password", () -> OracleTestDatabase.password("RO_B"));
        r.add("datamatch.environments.dev.url", () -> url.replaceFirst(":\\d+/", ":1/"));
        r.add("datamatch.environments.dev.username", () -> OracleTestDatabase.user("RO_A"));
        r.add("datamatch.environments.dev.password", () -> OracleTestDatabase.password("RO_A"));
    }

    @TestConfiguration
    static class Overrides {
        @Bean
        @Primary
        DataSourceRegistry recordingRegistry(DataMatchProperties properties) {
            return RecordingRegistries.create(properties, SQL);
        }

        @Bean
        @Primary
        Decryptor fakeDecryptor() {
            return new FakeDecryptor();
        }

        @Bean
        @Primary
        RowRepository shortTimeoutRows(DataSourceRegistry recordingRegistry, DataMatchProperties properties) {
            return TestRowRepositories.withTimeout(recordingRegistry, properties.getCompare().getChunkSize(), 3);
        }
    }

    @Autowired
    private MockMvc mvc;

    // ---- 도우미 ----

    private String body(String envA, String envB, String table, String columnsJson, String pairsText) {
        return "{\"envA\":\"" + envA + "\",\"envB\":\"" + envB + "\",\"owner\":\"" + OWNER + "\",\"table\":\"" + table
                + "\",\"columns\":" + columnsJson + ",\"pairsText\":" + quote(pairsText) + "}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private String start(String json) throws Exception {
        String res = mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.jobId");
    }

    private String json(String url) throws Exception {
        return mvc.perform(get(url).header("Host", "localhost")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 작업이 끝날 때까지 상태를 조회하고 관찰한 처리 건수를 돌려준다. */
    private String awaitEnd(String jobId, List<Integer> observed) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            String s = json("/api/compare/" + jobId);
            observed.add(JsonPath.read(s, "$.processed"));
            String st = JsonPath.read(s, "$.status");
            if (!st.equals("RUNNING") && !st.equals("CANCELLING")) {
                return s;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("job did not finish");
    }

    private String runToEnd(String json) throws Exception {
        return awaitEnd(start(json), new java.util.ArrayList<>());
    }

    private static String pairs(int from, int to, String fmtA, String fmtB) {
        return IntStream.rangeClosed(from, to).mapToObj(i -> String.format(fmtA + "," + fmtB, i, i)).collect(Collectors.joining("\n"));
    }

    // ---- 시나리오 ----

    @Test
    void sameAndCrossEnvironmentComparisonsJudgeInInputOrderUsingEachSidesEnvironment() throws Exception {
        String strPairs = pairs(1, 20, "A%06d", "B%06d");
        SQL.clear();
        String jobId = start(body("test", "prod", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"}]", strPairs));
        String st = awaitEnd(jobId, new java.util.ArrayList<>());
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("COMPLETED");
        String summary = json("/api/compare/" + jobId + "/summary");
        List<Integer> indexes = JsonPath.read(summary, "$.items[*].index");
        List<String> as = JsonPath.read(summary, "$.items[*].a");
        List<String> statuses = JsonPath.read(summary, "$.items[*].status");
        assertThat(indexes).isEqualTo(IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList()));
        assertThat(as.get(0)).isEqualTo("A000001");
        for (int i = 1; i <= 20; i++) {
            String expected = i % 10 == 0 ? "MISSING" : i % 4 == 0 ? "MISMATCH" : "MATCH";
            assertThat(statuses.get(i - 1)).as("pair " + i).isEqualTo(expected);
        }
        assertThat((String) JsonPath.read(summary, "$.items[9].missing")).isEqualTo("B");
        // A쪽 키는 test(RO_A) 풀, B쪽 키는 prod(RO_B) 풀로 조회했다.
        List<String> rowQueries = SQL.stream().filter(s -> s.contains("FROM \"DM_IT_OWNER\".\"DM_IT_TRX_STR\"")).collect(Collectors.toList());
        assertThat(rowQueries).extracting(s -> s.substring(0, s.indexOf('|'))).containsExactly("datamatch-test", "datamatch-prod");

        // 같은 환경(test-test)에서 같은 키를 비교하면 모두 일치다.
        String sameJob = start(body("test", "test", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"},{\"name\":\"AMOUNT\"}]",
                pairs(1, 5, "A%06d", "A%06d")));
        awaitEnd(sameJob, new java.util.ArrayList<>());
        List<String> sameStatuses = JsonPath.read(json("/api/compare/" + sameJob + "/summary"), "$.items[*].status");
        assertThat(sameStatuses).hasSize(5).containsOnly("MATCH");
    }

    @Test
    void metadataApisJudgePkTypesAndSearchLiterallyWithTruncation() throws Exception {
        for (String[] t : new String[][] {{"DM_IT_TRX_STR", "true", null}, {"DM_IT_TRX_NUM", "true", null},
                {"DM_IT_COMPOSITE", "false", "COMPOSITE_PK"}, {"DM_IT_NOPK", "false", "NO_PK"}, {"DM_IT_DATEPK", "false", "UNSUPPORTED_PK_TYPE"}}) {
            String res = json("/api/tables?env=test&q=" + OWNER + "." + t[0]);
            List<Map<String, Object>> items = JsonPath.read(res, "$.items[?(@.name == '" + t[0] + "')]");
            assertThat(items).as(t[0]).hasSize(1);
            assertThat(items.get(0).get("selectable")).as(t[0]).isEqualTo(Boolean.valueOf(t[1]));
            assertThat(items.get(0).get("reason")).as(t[0]).isEqualTo(t[2]);
        }
        String many = json("/api/tables?env=test&q=DM_IT_SEARCH");
        assertThat((List<?>) JsonPath.read(many, "$.items")).hasSize(30);
        assertThat((Boolean) JsonPath.read(many, "$.truncated")).isTrue();
        List<String> names = JsonPath.read(json("/api/tables?env=test&q=IT_A_B"), "$.items[*].name");
        assertThat(names).contains("DM_IT_A_B").doesNotContain("DM_IT_AXB");
    }

    @Test
    @SuppressWarnings("unchecked")
    void allTypesAreComparedPerConversionTable() throws Exception {
        String cols = "[" + List.of("C_CHAR", "C_VARCHAR", "C_NUMBER", "C_NUMSCALE", "C_DATE", "C_TS", "C_TSTZ", "C_CLOB", "C_BLOB", "C_RAW")
                .stream().map(c -> "{\"name\":\"" + c + "\"}").collect(Collectors.joining(",")) + "]";
        String jobId = start(body("test", "prod", "DM_IT_TYPES", cols, "T1,T2\nT1,T4\nT3,T3"));
        awaitEnd(jobId, new java.util.ArrayList<>());
        String summary = json("/api/compare/" + jobId + "/summary");
        assertThat((List<String>) JsonPath.read(summary, "$.items[*].status")).containsExactly("MATCH", "MISMATCH", "MATCH");
        String d1 = json("/api/compare/" + jobId + "/pairs/1");
        Map<String, String> a = ((List<Map<String, Object>>) JsonPath.read(d1, "$.rows")).stream()
                .collect(Collectors.toMap(r -> (String) r.get("name"), r -> String.valueOf(r.get("a"))));
        assertThat(a).containsEntry("C_CHAR", "ab   ").containsEntry("C_NUMBER", "1.1").containsEntry("C_NUMSCALE", "1234.5")
                .containsEntry("C_DATE", "2026-11-10 13:45:30").containsEntry("C_TS", "2026-11-10 13:45:30.123456789")
                .containsEntry("C_TSTZ", "2026-11-10 13:45:30.123456789 +09:00").containsEntry("C_CLOB", "긴 텍스트 값")
                .containsEntry("C_BLOB", "DEADBEEF").containsEntry("C_RAW", "0A0B0C");
        String d2 = json("/api/compare/" + jobId + "/pairs/2");
        List<String> mismatched = JsonPath.read(d2, "$.rows[?(@.status == 'MISMATCH')].name");
        assertThat(mismatched).contains("C_CHAR", "C_VARCHAR", "C_NUMBER", "C_DATE", "C_TS", "C_TSTZ", "C_CLOB", "C_BLOB", "C_RAW");
        String d3 = json("/api/compare/" + jobId + "/pairs/3");
        assertThat((List<Object>) JsonPath.read(d3, "$.rows[*].a")).containsOnlyNulls();
    }

    @Test
    void fakeCipherJsonFieldsAndBrokenCipherAreCompared() throws Exception {
        String cols = "[{\"name\":\"APPR_MSG\",\"decrypt\":{\"policy\":\"KDKEY\"}}]";
        String jobId = start(body("test", "prod", "DM_IT_TRX_STR", cols, "A000005,B000005\nA000008,B000008\nA000013,B000013"));
        awaitEnd(jobId, new java.util.ArrayList<>());
        String d1 = json("/api/compare/" + jobId + "/pairs/1");
        List<String> names = JsonPath.read(d1, "$.rows[*].name");
        assertThat(names).containsExactly("APPR_MSG.res_cd", "APPR_MSG.amount", "APPR_MSG.approval_no", "APPR_MSG.memo");
        assertThat((List<String>) JsonPath.read(d1, "$.rows[?(@.status == 'MISMATCH')].name")).containsExactly("APPR_MSG.approval_no");
        String d2 = json("/api/compare/" + jobId + "/pairs/2");
        assertThat((List<Object>) JsonPath.read(d2, "$.rows[?(@.name == 'APPR_MSG.memo')].b")).containsOnlyNulls();
        String d3 = json("/api/compare/" + jobId + "/pairs/3");
        assertThat((String) JsonPath.read(d3, "$.rows[0].status")).isEqualTo("DECRYPT_ERROR");
        assertThat((String) JsonPath.read(d3, "$.rows[0].message")).isEqualTo("DECRYPT_FAILED");
    }

    @Test
    void thousandPairsCompleteAndThousandOneAreRejected() throws Exception {
        List<Integer> observed = new java.util.ArrayList<>();
        String jobId = start(body("test", "prod", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"},{\"name\":\"AMOUNT\"}]",
                pairs(1, 1000, "A%06d", "B%06d")));
        String st = awaitEnd(jobId, observed);
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("COMPLETED");
        assertThat((Integer) JsonPath.read(st, "$.processed")).isEqualTo(1000);
        assertThat(observed).allMatch(p -> p % 100 == 0);
        assertThat((Integer) JsonPath.read(json("/api/compare/" + jobId + "/summary"), "$.counts.total")).isEqualTo(1000);
        System.out.println("[INT-01] 1000 pairs observed processed values: " + observed.stream().distinct().collect(Collectors.toList()));

        mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON)
                        .content(body("test", "prod", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"}]", pairs(1, 1001, "A%06d", "B%06d"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.details[0].reason").value("PAIR_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.details[0].count").value(1001));
    }

    @Test
    void nonNumericKeysForNumberPkAreInvalidNumber() throws Exception {
        mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON)
                        .content(body("test", "prod", "DM_IT_TRX_NUM", "[{\"name\":\"AMOUNT\"}]", "1,100001\nX2,100002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].line").value(2))
                .andExpect(jsonPath("$.details[0].reason").value("INVALID_NUMBER"));
        String jobId = start(body("test", "prod", "DM_IT_TRX_NUM", "[{\"name\":\"AMOUNT\"}]", "1,100001\n3,100003"));
        awaitEnd(jobId, new java.util.ArrayList<>());
        assertThat((List<String>) JsonPath.read(json("/api/compare/" + jobId + "/summary"), "$.items[*].status"))
                .containsExactly("MATCH", "MISMATCH");
    }

    @Test
    void slowQueryTimesOutAsFailedWithoutResult() throws Exception {
        String jobId = start(body("test", "prod", "DM_IT_SLOW", "[{\"name\":\"SLOW_COL\"}]", pairs(1, 5, "S%d", "S%d")));
        String st = awaitEnd(jobId, new java.util.ArrayList<>());
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(st, "$.errorCode")).isEqualTo("QUERY_TIMEOUT");
        assertThat((String) JsonPath.read(st, "$.failedSide")).isEqualTo("A");
        mvc.perform(get("/api/compare/" + jobId + "/summary").header("Host", "localhost")).andExpect(status().isConflict());
    }

    @Test
    void cancelDuringSlowQueryEndsCancelledAndDiscardsResult() throws Exception {
        String jobId = start(body("test", "prod", "DM_IT_SLOW", "[{\"name\":\"SLOW_COL\"}]", pairs(1, 5, "S%d", "S%d")));
        Thread.sleep(1000);
        long t0 = System.nanoTime();
        mvc.perform(post("/api/compare/" + jobId + "/cancel").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLING"));
        String st = awaitEnd(jobId, new java.util.ArrayList<>());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("[INT-01] cancel to CANCELLED: " + ms + " ms");
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("CANCELLED");
        assertThat((Object) JsonPath.read(st, "$.errorCode")).isNull();
        assertThat(ms).isLessThan(2_500);
        mvc.perform(get("/api/compare/" + jobId + "/summary").header("Host", "localhost")).andExpect(status().isConflict());
    }

    @Test
    void connectionFailureAndPermissionDifferenceFailWithSide() throws Exception {
        String st = runToEnd(body("test", "dev", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"}]", "A000001,B000001"));
        assertThat((String) JsonPath.read(st, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(st, "$.errorCode")).isEqualTo("DB_CONNECTION_FAILED");
        assertThat((String) JsonPath.read(st, "$.failedSide")).isEqualTo("B");
        assertThat((String) JsonPath.read(st, "$.failedEnv")).isEqualTo("dev");

        String grant = runToEnd(body("test", "prod", "DM_IT_NO_B_GRANT", "[{\"name\":\"VAL\"}]", "G1,G1"));
        assertThat((String) JsonPath.read(grant, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(grant, "$.errorCode")).isEqualTo("DB_QUERY_FAILED");
        assertThat((String) JsonPath.read(grant, "$.failedSide")).isEqualTo("B");
    }

    @AfterAll
    void onlySelectStatementsWereExecuted() {
        List<String> statements = Collections.unmodifiableList(SQL);
        System.out.println("[INT-01] recorded statements: " + statements.size());
        assertThat(statements).isNotEmpty();
        assertThat(statements).allSatisfy(s -> assertThat(s.substring(s.indexOf('|') + 1)).startsWith("SELECT "));
    }

    @Test
    void logsContainNoCredentials(CapturedOutput output) throws Exception {
        runToEnd(body("test", "dev", "DM_IT_TRX_STR", "[{\"name\":\"ORDER_NO\"}]", "A000001,B000001"));
        assertThat(output.getAll()).doesNotContain(OracleTestDatabase.password("RO_A")).doesNotContain(OracleTestDatabase.password("RO_B"))
                .doesNotContain("A000001");
    }
}
