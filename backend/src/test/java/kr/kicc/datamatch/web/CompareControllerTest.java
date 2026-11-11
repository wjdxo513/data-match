package kr.kicc.datamatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.kicc.datamatch.compare.ColumnSpec;
import kr.kicc.datamatch.compare.CompareRunner;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.CompareTaskFactory;
import kr.kicc.datamatch.compare.DecryptErrorCause;
import kr.kicc.datamatch.compare.FieldResultTestFactory;
import kr.kicc.datamatch.compare.MissingSide;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.PairStatus;
import kr.kicc.datamatch.compare.Side;
import kr.kicc.datamatch.compare.TransactionPair;
import kr.kicc.datamatch.config.LocalOnlyFilter;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.job.JobManager;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** BE-05: 비교 API의 응답 형식(요약 JSON 스냅샷, 상세, 상태 코드)을 가짜 러너로 확인한다. */
@WebMvcTest(controllers = CompareController.class)
@Import({GlobalExceptionHandler.class, LocalOnlyFilter.class, JobManager.class, kr.kicc.datamatch.excel.ExcelExporter.class,
        CompareControllerTest.RunnerConfig.class})
class CompareControllerTest {

    private static final TableMetadata TABLE = new TableMetadata("DM_IT_OWNER", "DM_IT_TRX_STR", List.of("TRX_NO"), List.of(
            new ColumnMeta("TRX_NO", "VARCHAR2", 20, null, null, 20), new ColumnMeta("AMOUNT", "NUMBER", 22, 12, 0, 0),
            new ColumnMeta("APPR_MSG", "CLOB", 4000, null, null, 0)));

    static volatile CountDownLatch gate = new CountDownLatch(0);

    @TestConfiguration
    static class RunnerConfig {
        @Bean
        CompareRunner runner() {
            return (task, progress, token) -> {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                progress.accept(3);
                return List.of(
                        new PairResult(1, "A1", "B1", PairStatus.MATCH, null,
                                List.of(FieldResultTestFactory.compared("AMOUNT", null, "100", "100")), 0),
                        new PairResult(2, "A2", "B2", PairStatus.MISMATCH, null, List.of(
                                FieldResultTestFactory.compared("AMOUNT", null, "100", "200"),
                                FieldResultTestFactory.compared("APPR_MSG", "res_cd", "0000", null),
                                FieldResultTestFactory.decryptError("APPR_MSG", DecryptErrorCause.JSON_INVALID, Side.B)), 3),
                        new PairResult(3, "A3", "B3", PairStatus.MISSING, MissingSide.B, List.of(), 0),
                        PairResult.duplicate(4, new TransactionPair(4, "A4", "B4"), 2, 1));
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CompareTaskFactory taskFactory;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private kr.kicc.datamatch.excel.ExcelExporter excelExporter;

    private static final String BODY = "{\"envA\":\"test\",\"envB\":\"prod\",\"owner\":\"DM_IT_OWNER\",\"table\":\"DM_IT_TRX_STR\",\"keyColumn\":\"TRX_NO\","
            + "\"columns\":[{\"name\":\"AMOUNT\"},{\"name\":\"APPR_MSG\",\"decrypt\":{\"policy\":\"KDKEY\"}}],"
            + "\"pairsText\":\"A1,B1\\nA2,B2\\nA3,B3\"}";

    private CompareTask task() {
        return new CompareTask("test", "prod", TABLE, "TRX_NO",
                List.of(ColumnSpec.plain("AMOUNT"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                List.of(new TransactionPair(1, "A1", "B1"), new TransactionPair(2, "A2", "B2"), new TransactionPair(3, "A3", "B3")),
                "2026-11-10T10:00:00");
    }

    private String start() throws Exception {
        return start(task());
    }

    private String start(CompareTask task) throws Exception {
        when(taskFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyList(), anyString())).thenReturn(task);
        String body = mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.total").value(task.getPairs().size()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.jobId");
    }

    private void awaitStatus(String jobId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            String body = mvc.perform(get("/api/compare/" + jobId).header("Host", "localhost")).andReturn().getResponse().getContentAsString();
            if (expected.equals(JsonPath.read(body, "$.status"))) {
                return;
            }
            assertThat(System.currentTimeMillis()).isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    @Test
    void summaryAndDetailResponsesMatchApiFormat() throws Exception {
        gate = new CountDownLatch(0);
        String jobId = start();
        awaitStatus(jobId, "COMPLETED");
        mvc.perform(get("/api/compare/" + jobId).header("Host", "localhost"))
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.errorCode").isEmpty());

        String summary = mvc.perform(get("/api/compare/" + jobId + "/summary").header("Host", "localhost"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals("{"
                + "\"condition\":{\"envA\":\"test\",\"envB\":\"prod\",\"owner\":\"DM_IT_OWNER\",\"table\":\"DM_IT_TRX_STR\",\"keyColumn\":\"TRX_NO\","
                + "  \"columns\":[{\"name\":\"AMOUNT\"},{\"name\":\"APPR_MSG\",\"decrypt\":\"KDKEY\"}],\"comparedAt\":\"2026-11-10T10:00:00\"},"
                + "\"counts\":{\"total\":4,\"match\":1,\"mismatch\":1,\"missing\":1,\"duplicate\":1},"
                + "\"items\":["
                + "  {\"index\":1,\"a\":\"A1\",\"b\":\"B1\",\"status\":\"MATCH\",\"mismatchCount\":0,\"missing\":null,\"duplicate\":null},"
                + "  {\"index\":2,\"a\":\"A2\",\"b\":\"B2\",\"status\":\"MISMATCH\",\"mismatchCount\":3,\"missing\":null,\"duplicate\":null},"
                + "  {\"index\":3,\"a\":\"A3\",\"b\":\"B3\",\"status\":\"MISSING\",\"mismatchCount\":0,\"missing\":\"B\",\"duplicate\":null},"
                + "  {\"index\":4,\"a\":\"A4\",\"b\":\"B4\",\"status\":\"DUPLICATE\",\"mismatchCount\":0,\"missing\":null,"
                + "   \"duplicate\":{\"a\":2,\"b\":1}}]}",
                summary, JSONCompareMode.STRICT);

        String detail = mvc.perform(get("/api/compare/" + jobId + "/pairs/2").header("Host", "localhost"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals("{\"index\":2,\"a\":\"A2\",\"b\":\"B2\",\"status\":\"MISMATCH\",\"missing\":null,\"duplicate\":null,\"rows\":["
                + "{\"name\":\"AMOUNT\",\"a\":\"100\",\"b\":\"200\",\"status\":\"MISMATCH\",\"message\":null},"
                + "{\"name\":\"APPR_MSG.res_cd\",\"a\":\"0000\",\"b\":null,\"status\":\"MISMATCH\",\"message\":null},"
                + "{\"name\":\"APPR_MSG\",\"a\":null,\"b\":null,\"status\":\"DECRYPT_ERROR\",\"message\":\"JSON_INVALID\"}]}",
                detail, JSONCompareMode.STRICT);
        mvc.perform(get("/api/compare/" + jobId + "/pairs/3").header("Host", "localhost"))
                .andExpect(jsonPath("$.missing").value("B"))
                .andExpect(jsonPath("$.rows.length()").value(0));
        mvc.perform(get("/api/compare/" + jobId + "/pairs/4").header("Host", "localhost"))
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicate.a").value(2))
                .andExpect(jsonPath("$.duplicate.b").value(1))
                .andExpect(jsonPath("$.rows.length()").value(0));
        for (String bad : new String[] {"0", "5"}) {
            mvc.perform(get("/api/compare/" + jobId + "/pairs/" + bad).header("Host", "localhost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAIR_NOT_FOUND"));
        }
    }

    @Test
    void runningJobConflictsAndResultsAreNotReady() throws Exception {
        gate = new CountDownLatch(1);
        try {
            String jobId = start();
            mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("JOB_RUNNING"));
            mvc.perform(get("/api/compare/" + jobId + "/summary").header("Host", "localhost"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("JOB_NOT_COMPLETED"));
            mvc.perform(post("/api/compare/" + jobId + "/cancel").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("CANCELLING"));
            gate.countDown();
            awaitStatus(jobId, "CANCELLED");
            mvc.perform(get("/api/compare/" + jobId + "/pairs/1").header("Host", "localhost"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("JOB_NOT_COMPLETED"));
        } finally {
            gate.countDown();
        }
        mvc.perform(get("/api/compare/unknown").header("Host", "localhost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void excelDownloadUsesUtf8FileNameAndFailureKeepsResult() throws Exception {
        gate = new CountDownLatch(0);
        TableMetadata korean = new TableMetadata("DM_IT_OWNER", "거래_테이블", List.of("TRX_NO"), TABLE.getColumns());
        String jobId = start(new CompareTask("test", "prod", korean, "TRX_NO", List.of(ColumnSpec.plain("AMOUNT")),
                List.of(new TransactionPair(1, "A1", "B1")), "2026-11-10T15:15:30"));
        awaitStatus(jobId, "COMPLETED");
        org.springframework.mock.web.MockHttpServletResponse res = mvc.perform(get("/api/compare/" + jobId + "/excel")
                        .header("Host", "localhost"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(res.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String disposition = res.getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;").contains("filename*=UTF-8''");
        assertThat(java.net.URLDecoder.decode(disposition.substring(disposition.indexOf("UTF-8''") + 7), StandardCharsets.UTF_8))
                .isEqualTo("datamatch_test-prod_거래_테이블_20261110-151530.xlsx");
        assertThat(res.getContentAsByteArray()).startsWith((byte) 'P', (byte) 'K');

        org.mockito.Mockito.doThrow(new IllegalStateException("poi broke")).when(excelExporter).export(any(), any());
        mvc.perform(get("/api/compare/" + jobId + "/excel").header("Host", "localhost"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXCEL_FAILED"));
        mvc.perform(get("/api/compare/" + jobId + "/summary").header("Host", "localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.total").value(4));
        mvc.perform(get("/api/compare/other/excel").header("Host", "localhost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void excelForUnfinishedJobIsNotCompleted() throws Exception {
        gate = new CountDownLatch(1);
        try {
            String jobId = start();
            mvc.perform(get("/api/compare/" + jobId + "/excel").header("Host", "localhost"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("JOB_NOT_COMPLETED"));
            gate.countDown();
            awaitStatus(jobId, "COMPLETED");
        } finally {
            gate.countDown();
        }
    }

    @Test
    void validationFailureIs400AndCreatesNoJob() throws Exception {
        gate = new CountDownLatch(0);
        when(taskFactory.create(any(), any(), any(), any(), any(), any(), any())).thenThrow(new DataMatchException(ErrorCode.INVALID_INPUT,
                "invalid pairs input", List.of(Map.of("line", 2, "reason", "NO_DELIMITER"))));
        mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.details[0].line").value(2))
                .andExpect(jsonPath("$.details[0].reason").value("NO_DELIMITER"));
        mvc.perform(post("/api/compare").header("Host", "localhost").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("KDKEY", "NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
