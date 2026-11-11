package kr.kicc.datamatch.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kr.kicc.datamatch.compare.CancellationToken;
import kr.kicc.datamatch.compare.ColumnSpec;
import kr.kicc.datamatch.compare.CompareFailedException;
import kr.kicc.datamatch.compare.CompareRunner;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.MissingSide;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.PairStatus;
import kr.kicc.datamatch.compare.QueryCancelledException;
import kr.kicc.datamatch.compare.Side;
import kr.kicc.datamatch.compare.TransactionPair;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** BE-05: 작업 생명주기(동시 1개, 이전 작업 폐기, 실제 진행률, 중지, 실패, 스레드 정리)를 가짜 러너로 확인한다. */
class JobManagerTest {

    private JobManager manager;

    static CompareTask task(int pairs) {
        TableMetadata t = new TableMetadata("O", "T", List.of("ID"), List.of(new ColumnMeta("ID", "VARCHAR2", 10, null, null, 10)));
        List<TransactionPair> list = new java.util.ArrayList<>();
        for (int i = 1; i <= pairs; i++) {
            list.add(new TransactionPair(i, "A" + i, "B" + i));
        }
        return new CompareTask("test", "prod", t, "ID", List.of(ColumnSpec.plain("ID")), list, "2026-11-10T10:00:00");
    }

    static List<PairResult> results(int n) {
        List<PairResult> r = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            r.add(new PairResult(i, "A" + i, "B" + i, PairStatus.MISSING, MissingSide.B, List.of(), 0));
        }
        return r;
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

    @AfterEach
    void tearDown() throws InterruptedException {
        if (manager != null) {
            manager.destroy();
        }
    }

    /** 단계를 테스트가 직접 진행시키는 러너. */
    static class SteppingRunner implements CompareRunner {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile int reportBeforeWait = -1;

        @Override
        public List<PairResult> run(CompareTask task, java.util.function.IntConsumer progress, CancellationToken token) {
            if (reportBeforeWait >= 0) {
                progress.accept(reportBeforeWait);
            }
            started.countDown();
            try {
                while (!release.await(10, TimeUnit.MILLISECONDS)) {
                    if (token.isCancelled()) {
                        throw new QueryCancelledException();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new QueryCancelledException();
            }
            progress.accept(task.getPairs().size());
            return results(task.getPairs().size());
        }
    }

    @Test
    void secondStartWhileRunningIsJobRunningAndFirstIsUnaffected() throws Exception {
        SteppingRunner runner = new SteppingRunner();
        manager = new JobManager(runner);
        CompareJob first = manager.start(task(3));
        runner.started.await(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> manager.start(task(1)))
                .isInstanceOf(DataMatchException.class)
                .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.JOB_RUNNING));
        assertThat(manager.get(first.id())).isSameAs(first);
        assertThat(first.status()).isEqualTo(JobStatus.RUNNING);
        runner.release.countDown();
        await(() -> first.status() == JobStatus.COMPLETED);
        assertThat(manager.results(first.id())).hasSize(3);
    }

    @Test
    void newJobDiscardsPreviousJob() throws Exception {
        manager = new JobManager((task, progress, token) -> results(task.getPairs().size()));
        CompareJob first = manager.start(task(2));
        await(() -> first.status() == JobStatus.COMPLETED);
        CompareJob second = manager.start(task(1));
        for (Runnable call : List.<Runnable>of(() -> manager.get(first.id()), () -> manager.results(first.id()),
                () -> manager.cancel(first.id()))) {
            assertThatThrownBy(call::run).satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
        }
        assertThat(manager.get(second.id())).isSameAs(second);
    }

    @Test
    void progressReflectsOnlyRunnerReports() throws Exception {
        SteppingRunner runner = new SteppingRunner();
        runner.reportBeforeWait = 100;
        manager = new JobManager(runner);
        CompareJob job = manager.start(task(250));
        runner.started.await(5, TimeUnit.SECONDS);
        assertThat(job.processed()).isEqualTo(100);
        Thread.sleep(300);
        assertThat(job.processed()).isEqualTo(100);
        assertThatThrownBy(() -> manager.results(job.id()))
                .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.JOB_NOT_COMPLETED));
        runner.release.countDown();
        await(() -> job.status() == JobStatus.COMPLETED);
        assertThat(job.processed()).isEqualTo(250);
    }

    @Test
    void cancelMovesToCancellingThenCancelledAndDiscardsResults() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        manager = new JobManager((task, progress, token) -> {
            started.countDown();
            try {
                proceed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            progress.accept(1);
            return results(1); // 러너가 결과를 돌려줘도 중지 요청이 있었으면 폐기한다.
        });
        CompareJob job = manager.start(task(1));
        started.await(5, TimeUnit.SECONDS);
        assertThat(manager.cancel(job.id()).status()).isEqualTo(JobStatus.CANCELLING);
        proceed.countDown();
        await(() -> job.status() == JobStatus.CANCELLED);
        assertThat(job.results()).isNull();
        assertThatThrownBy(() -> manager.results(job.id()))
                .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.JOB_NOT_COMPLETED));
        assertThat(manager.cancel(job.id()).status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void cancellationSignalFromRunnerIsCancelledNotError() throws Exception {
        manager = new JobManager((task, progress, token) -> {
            throw new QueryCancelledException();
        });
        CompareJob job = manager.start(task(1));
        await(() -> !job.status().isActive());
        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.errorCode()).isNull();
    }

    @Test
    void runnerFailureIsFailedWithSideAndEnvAndNoPartialResult() throws Exception {
        manager = new JobManager((task, progress, token) -> {
            progress.accept(100);
            throw new CompareFailedException(ErrorCode.DB_CONNECTION_FAILED, Side.B, "prod");
        });
        CompareJob job = manager.start(task(200));
        await(() -> job.status() == JobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.DB_CONNECTION_FAILED);
        assertThat(job.failedSide()).isEqualTo(Side.B);
        assertThat(job.failedEnv()).isEqualTo("prod");
        assertThat(job.results()).isNull();
        assertThatThrownBy(() -> manager.results(job.id()))
                .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.JOB_NOT_COMPLETED));
    }

    @Test
    void unexpectedErrorsAreFailed() throws Exception {
        manager = new JobManager((task, progress, token) -> {
            throw new IllegalStateException("boom");
        });
        CompareJob job = manager.start(task(1));
        await(() -> job.status() == JobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);

        manager.destroy();
        manager = new JobManager((task, progress, token) -> {
            throw new DataMatchException(ErrorCode.DB_QUERY_FAILED, "x");
        });
        CompareJob other = manager.start(task(1));
        await(() -> other.status() == JobStatus.FAILED);
        assertThat(other.errorCode()).isEqualTo(ErrorCode.DB_QUERY_FAILED);
        assertThat(other.failedSide()).isNull();
    }

    @Test
    void finishedJobsLeaveNoExtraThreadsAndNextJobCanStart() throws Exception {
        // 같은 JVM의 다른 테스트(스프링 컨텍스트)가 만든 워커가 있을 수 있어 증가분만 본다.
        long baseline = workerCount();
        manager = new JobManager((task, progress, token) -> results(task.getPairs().size()));
        for (int i = 0; i < 5; i++) {
            CompareJob job = manager.start(task(1));
            await(() -> job.status() == JobStatus.COMPLETED);
        }
        assertThat(workerCount() - baseline).isLessThanOrEqualTo(1);
        manager.destroy();
        await(() -> workerCount() <= baseline);
        manager = null;
    }

    private static long workerCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("datamatch-compare") && t.isAlive()).count();
    }
}
