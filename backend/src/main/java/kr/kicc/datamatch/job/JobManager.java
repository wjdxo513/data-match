package kr.kicc.datamatch.job;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.kicc.datamatch.compare.CompareFailedException;
import kr.kicc.datamatch.compare.CompareRunner;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.QueryCancelledException;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * 비교 작업 관리(기술 설계서 7.1절, TD-12). 단일 워커 스레드에서 한 번에 하나만 실행하고, 서버가 보관하는 작업은 마지막 1개뿐이다.
 * 새 작업을 만들면 이전 작업과 결과는 즉시 폐기한다(SA-08).
 */
@Component
public class JobManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JobManager.class);

    private final CompareRunner runner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "datamatch-compare");
        t.setDaemon(true);
        return t;
    });

    private CompareJob current;

    public JobManager(CompareRunner runner) {
        this.runner = runner;
    }

    /** 작업을 만들고 실행을 예약한다. 실행 중인 작업이 있으면 {@code JOB_RUNNING}. */
    public synchronized CompareJob start(CompareTask task) {
        if (current != null && current.status().isActive()) {
            throw new DataMatchException(ErrorCode.JOB_RUNNING, "another comparison is running");
        }
        CompareJob job = new CompareJob(UUID.randomUUID().toString(), task);
        current = job;
        log.info("compare job started: id={}, envA={}, envB={}, table={}.{}, pairs={}", job.id(), task.getEnvA(),
                task.getEnvB(), task.getTable().getOwner(), task.getTable().getName(), job.total());
        executor.execute(() -> execute(job));
        return job;
    }

    /** 현재 작업. 다른 ID(이전 작업 포함)는 {@code JOB_NOT_FOUND}. */
    public synchronized CompareJob get(String jobId) {
        if (current == null || !current.id().equals(jobId)) {
            throw new DataMatchException(ErrorCode.JOB_NOT_FOUND, "job not found");
        }
        return current;
    }

    public CompareJob cancel(String jobId) {
        CompareJob job = get(jobId);
        job.requestCancel();
        return job;
    }

    /** 완료된 작업의 결과. 완료되지 않았거나 실패·취소되었으면 {@code JOB_NOT_COMPLETED}. */
    public List<PairResult> results(String jobId) {
        List<PairResult> results = get(jobId).results();
        if (results == null) {
            throw new DataMatchException(ErrorCode.JOB_NOT_COMPLETED, "job not completed");
        }
        return results;
    }

    private void execute(CompareJob job) {
        long start = System.nanoTime();
        try {
            List<PairResult> results = runner.run(job.task(), job::reportProgress, job.token());
            job.complete(results);
        } catch (QueryCancelledException e) {
            job.cancelled();
        } catch (CompareFailedException e) {
            job.fail(e.code(), e.side(), e.env());
        } catch (DataMatchException e) {
            job.fail(e.code(), null, null);
        } catch (RuntimeException e) {
            log.error("compare job crashed: id={}, type={}", job.id(), e.getClass().getName(), e);
            job.fail(ErrorCode.INTERNAL_ERROR, null, null);
        }
        log.info("compare job finished: id={}, status={}, errorCode={}, elapsedMs={}", job.id(), job.status(),
                job.errorCode(), (System.nanoTime() - start) / 1_000_000);
    }

    @Override
    public void destroy() throws InterruptedException {
        CompareJob job;
        synchronized (this) {
            job = current;
        }
        if (job != null) {
            job.requestCancel();
        }
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
