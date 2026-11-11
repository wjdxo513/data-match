package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.job.CompareJob;
import kr.kicc.datamatch.job.JobManager;
import kr.kicc.datamatch.job.JobStatus;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import kr.kicc.datamatch.safedb.FakeDecryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** BE-06: 청크 파이프라인(조회 횟수, 매칭, 진행률, 취소, 오류 분류, 복호화 오류)을 가짜 RowRepository·Decryptor로 확인한다. */
@ExtendWith(OutputCaptureExtension.class)
class CompareServiceTest {

    private static final TableMetadata TABLE = new TableMetadata("O", "T", List.of("ID"), List.of(
            new ColumnMeta("ID", "VARCHAR2", 20, null, null, 20), new ColumnMeta("AMOUNT", "NUMBER", 22, null, null, 0),
            new ColumnMeta("APPR_MSG", "CLOB", 4000, null, null, 0)));

    /** 환경별로 미리 넣어 둔 행을 돌려주고 호출을 기록하는 가짜 저장소. */
    static class FakeRows implements RowRepository {
        final Map<String, Map<String, List<Map<String, String>>>> data = new HashMap<>();
        final List<String> envCalls = new ArrayList<>();
        final List<List<String>> keyCalls = new ArrayList<>();
        Runnable onCall = () -> { };
        RuntimeException failOn;
        String failEnv;

        void put(String env, String key, String... kv) {
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < kv.length; i += 2) {
                row.put(kv[i], kv[i + 1]);
            }
            // 같은 키를 여러 번 넣으면 키 중복이 된다.
            data.computeIfAbsent(env, e -> new HashMap<>()).computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        @Override
        public Map<String, List<Map<String, String>>> fetchRows(String env, RowQuery query, List<String> keys, CancellationToken token) {
            if (token.isCancelled()) {
                throw new QueryCancelledException();
            }
            envCalls.add(env);
            keyCalls.add(List.copyOf(keys));
            onCall.run();
            if (failOn != null && env.equals(failEnv)) {
                throw failOn;
            }
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            for (String k : keys) {
                List<Map<String, String>> found = data.getOrDefault(env, Map.of()).get(k);
                if (found != null) {
                    result.put(k, found);
                }
            }
            return result;
        }
    }

    private FakeRows rows;
    private FakeDecryptor decryptor;
    private CompareService service;

    @BeforeEach
    void setUp() {
        rows = new FakeRows();
        decryptor = new FakeDecryptor();
        service = new CompareService(rows, decryptor, 100);
    }

    private static CompareTask task(List<ColumnSpec> columns, List<TransactionPair> pairs) {
        return new CompareTask("test", "prod", TABLE, "ID", columns, pairs, "2026-11-10T10:00:00");
    }

    private static List<TransactionPair> pairs(int n) {
        List<TransactionPair> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new TransactionPair(i, "A" + i, "B" + i));
        }
        return list;
    }

    @Test
    void thousandPairsMakeTenChunksWithOneDistinctQueryPerSide() {
        List<TransactionPair> pairs = pairs(1000);
        pairs.set(1, new TransactionPair(2, "A1", "B1")); // 같은 청크의 중복 키
        service.run(task(List.of(ColumnSpec.plain("AMOUNT")), pairs), p -> { }, new CancellationToken());
        assertThat(rows.envCalls).hasSize(20);
        for (int i = 0; i < 10; i++) {
            assertThat(rows.envCalls.get(2 * i)).isEqualTo("test");
            assertThat(rows.envCalls.get(2 * i + 1)).isEqualTo("prod");
        }
        assertThat(rows.keyCalls.get(0)).hasSize(99).doesNotHaveDuplicates().allMatch(k -> k.startsWith("A"));
        assertThat(rows.keyCalls.get(1)).hasSize(99).doesNotHaveDuplicates().allMatch(k -> k.startsWith("B"));
        assertThat(rows.keyCalls).allSatisfy(keys -> assertThat(keys.size()).isLessThanOrEqualTo(100));
    }

    @Test
    void duplicateKeysAreMatchedToEveryPairInInputOrder() {
        rows.put("test", "A1", "AMOUNT", "100");
        rows.put("test", "A2", "AMOUNT", "200");
        rows.put("prod", "B1", "AMOUNT", "100");
        rows.put("prod", "B2", "AMOUNT", "999");
        List<TransactionPair> pairs = List.of(new TransactionPair(1, "A2", "B2"), new TransactionPair(2, "A1", "B1"),
                new TransactionPair(4, "A1", "B2"), new TransactionPair(5, "A9", "B1"));
        List<PairResult> r = service.run(task(List.of(ColumnSpec.plain("AMOUNT")), pairs), p -> { }, new CancellationToken());
        assertThat(r).extracting(PairResult::getIndex).containsExactly(1, 2, 3, 4);
        assertThat(r).extracting(PairResult::getA).containsExactly("A2", "A1", "A1", "A9");
        assertThat(r).extracting(PairResult::getStatus)
                .containsExactly(PairStatus.MISMATCH, PairStatus.MATCH, PairStatus.MISMATCH, PairStatus.MISSING);
        assertThat(r.get(3).getMissing()).isEqualTo(MissingSide.A);
    }

    @Test
    void progressIsReportedPerChunk() {
        List<Integer> reports = new ArrayList<>();
        service.run(task(List.of(ColumnSpec.plain("AMOUNT")), pairs(250)), reports::add, new CancellationToken());
        assertThat(reports).containsExactly(100, 200, 250);
    }

    @Test
    void cancelBetweenChunksStopsFurtherQueries() {
        CancellationToken token = new CancellationToken();
        rows.onCall = () -> {
            if (rows.envCalls.size() == 2) {
                token.cancel();
            }
        };
        List<Integer> reports = new ArrayList<>();
        assertThatThrownBy(() -> service.run(task(List.of(ColumnSpec.plain("AMOUNT")), pairs(300)), reports::add, token))
                .isInstanceOf(QueryCancelledException.class);
        assertThat(rows.envCalls).hasSize(2);
        assertThat(reports).containsExactly(100);
    }

    @Test
    void cancelIsCheckedBeforeEachDecryptRow() {
        for (int i = 1; i <= 3; i++) {
            rows.put("test", "A" + i, "APPR_MSG", FakeDecryptor.encrypt("{}"));
            rows.put("prod", "B" + i, "APPR_MSG", FakeDecryptor.encrypt("{}"));
        }
        CancellationToken token = new CancellationToken();
        FakeDecryptor counting = new FakeDecryptor() {
            @Override
            public kr.kicc.datamatch.safedb.DecryptSession openSession() {
                kr.kicc.datamatch.safedb.DecryptSession s = super.openSession();
                return (c, p) -> {
                    String plain = s.decrypt(c, p);
                    if (calls().size() == 2) {
                        token.cancel();
                    }
                    return plain;
                };
            }
        };
        CompareService svc = new CompareService(rows, counting, 100);
        assertThatThrownBy(() -> svc.run(task(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), pairs(3)), p -> { }, token))
                .isInstanceOf(QueryCancelledException.class);
        assertThat(counting.calls()).hasSize(2);
    }

    @Test
    void dbErrorsBecomeFailuresWithSideAndEnv() {
        ErrorCode[] codes = {ErrorCode.DB_CONNECTION_FAILED, ErrorCode.QUERY_TIMEOUT, ErrorCode.DB_QUERY_FAILED};
        for (ErrorCode code : codes) {
            FakeRows failing = new FakeRows();
            failing.failEnv = "prod";
            failing.failOn = new DataMatchException(code, "x", Map.of("env", "prod"));
            List<Integer> reports = new ArrayList<>();
            CompareService svc = new CompareService(failing, decryptor, 100);
            assertThatThrownBy(() -> svc.run(task(List.of(ColumnSpec.plain("AMOUNT")), pairs(10)), reports::add, new CancellationToken()))
                    .isInstanceOf(CompareFailedException.class)
                    .satisfies(e -> {
                        CompareFailedException f = (CompareFailedException) e;
                        assertThat(f.code()).isEqualTo(code);
                        assertThat(f.side()).isEqualTo(Side.B);
                        assertThat(f.env()).isEqualTo("prod");
                    });
            assertThat(reports).isEmpty();
        }
    }

    @Test
    void individualDecryptErrorsDoNotFailTheJob() {
        rows.put("test", "A1", "AMOUNT", "1", "APPR_MSG", "BROKEN");
        rows.put("prod", "B1", "AMOUNT", "1", "APPR_MSG", FakeDecryptor.encrypt("{\"a\":1}"));
        rows.put("test", "A2", "AMOUNT", "2", "APPR_MSG", FakeDecryptor.encrypt("{\"a\":1}"));
        rows.put("prod", "B2", "AMOUNT", "2", "APPR_MSG", FakeDecryptor.encrypt("{\"a\":1}"));
        List<PairResult> r = service.run(task(List.of(ColumnSpec.plain("AMOUNT"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                pairs(2)), p -> { }, new CancellationToken());
        assertThat(r.get(0).getRows()).extracting(FieldResult::getStatus).containsExactly(RowStatus.MATCH, RowStatus.DECRYPT_ERROR);
        assertThat(r.get(1).getStatus()).isEqualTo(PairStatus.MATCH);
    }

    @Test
    void loginFailureMakesRemainingDecryptsErrorsWithoutRetryAndJobCompletes() throws Exception {
        for (int i = 1; i <= 150; i++) {
            rows.put("test", "A" + i, "APPR_MSG", FakeDecryptor.encrypt("{}"));
            rows.put("prod", "B" + i, "APPR_MSG", FakeDecryptor.encrypt("{}"));
        }
        FakeDecryptor failing = new FakeDecryptor().failLogin();
        JobManager jobs = new JobManager(new CompareService(rows, failing, 100));
        try {
            CompareJob job = jobs.start(task(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), pairs(150)));
            await(() -> !job.status().isActive());
            assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(failing.loginAttempts()).isEqualTo(1);
            assertThat(job.results()).allSatisfy(p -> assertThat(p.getRows()).extracting(FieldResult::getStatus)
                    .containsExactly(RowStatus.DECRYPT_ERROR));
        } finally {
            jobs.destroy();
        }
    }

    @Test
    void missingPairsAndNullValuesAreNotDecrypted() {
        rows.put("test", "A1", "APPR_MSG", FakeDecryptor.encrypt("{}"));
        rows.put("test", "A2", "APPR_MSG", null);
        rows.put("prod", "B2", "APPR_MSG", null);
        List<PairResult> r = service.run(task(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), pairs(2)),
                p -> { }, new CancellationToken());
        assertThat(r.get(0).getStatus()).isEqualTo(PairStatus.MISSING);
        assertThat(r.get(0).getRows()).isEmpty();
        assertThat(r.get(1).getStatus()).isEqualTo(PairStatus.MATCH);
        assertThat(decryptor.calls()).isEmpty();
    }

    @Test
    void cancelledQueryFromRepositoryEndsJobAsCancelledNotError() throws Exception {
        RowRepository cancelling = (env, query, keys, token) -> {
            token.cancel();
            throw new QueryCancelledException();
        };
        JobManager jobs = new JobManager(new CompareService(cancelling, decryptor, 100));
        try {
            CompareJob job = jobs.start(task(List.of(ColumnSpec.plain("AMOUNT")), pairs(5)));
            await(() -> !job.status().isActive());
            assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
            assertThat(job.errorCode()).isNull();
            assertThat(job.results()).isNull();
        } finally {
            jobs.destroy();
        }
    }

    @Test
    void logsContainNoTransactionNumbersValuesOrPlaintext(CapturedOutput output) {
        rows.put("test", "SECRETTRX-A1", "AMOUNT", "SECRET-VALUE-1", "APPR_MSG", FakeDecryptor.encrypt("{\"card\":\"SECRET-PLAIN\"}"));
        rows.put("prod", "SECRETTRX-B1", "AMOUNT", "SECRET-VALUE-2", "APPR_MSG", FakeDecryptor.encrypt("{\"card\":\"SECRET-PLAIN2\"}"));
        service.run(task(List.of(ColumnSpec.plain("AMOUNT"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                List.of(new TransactionPair(1, "SECRETTRX-A1", "SECRETTRX-B1"))), p -> { }, new CancellationToken());
        FakeRows failing = new FakeRows();
        failing.failEnv = "test";
        failing.failOn = new DataMatchException(ErrorCode.DB_QUERY_FAILED, "x", Map.of("env", "test"));
        assertThatThrownBy(() -> new CompareService(failing, decryptor, 100).run(task(List.of(ColumnSpec.plain("AMOUNT")),
                List.of(new TransactionPair(1, "SECRETTRX-A1", "SECRETTRX-B1"))), p -> { }, new CancellationToken()))
                .isInstanceOf(CompareFailedException.class);
        assertThat(output.getAll()).contains("chunk done").contains("compare failed")
                .doesNotContain("SECRET");
    }

    static void await(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.get()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void duplicateKeysAreJudgedBeforeMissingWithoutComparingOrDecrypting() {
        String cipher = FakeDecryptor.encrypt("{}");
        rows.put("test", "A1", "APPR_MSG", cipher);
        rows.put("test", "A1", "APPR_MSG", cipher); // A쪽 2건
        rows.put("prod", "B1", "APPR_MSG", cipher);
        rows.put("prod", "B2", "APPR_MSG", cipher);
        rows.put("prod", "B2", "APPR_MSG", cipher); // A2 없음 + B쪽 2건
        rows.put("test", "A3", "APPR_MSG", cipher);
        rows.put("prod", "B3", "APPR_MSG", cipher);
        List<PairResult> r = service.run(task(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), pairs(3)),
                p -> { }, new CancellationToken());
        assertThat(r).extracting(PairResult::getStatus).containsExactly(PairStatus.DUPLICATE, PairStatus.DUPLICATE, PairStatus.MATCH);
        assertThat(r.get(0).getDuplicate()).isEqualTo(new PairResult.DuplicateCount(2, 1));
        assertThat(r.get(1).getDuplicate()).isEqualTo(new PairResult.DuplicateCount(0, 2));
        assertThat(r.get(1).getMissing()).isNull();
        assertThat(r.get(0).getRows()).isEmpty();
        assertThat(r.get(2).getDuplicate()).isNull();
        assertThat(decryptor.calls()).hasSize(2);
    }
}
