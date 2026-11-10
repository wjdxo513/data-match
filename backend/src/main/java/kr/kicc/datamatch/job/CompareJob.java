package kr.kicc.datamatch.job;

import java.util.List;
import kr.kicc.datamatch.compare.CancellationToken;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.Side;
import kr.kicc.datamatch.error.ErrorCode;

/**
 * 비교 작업 하나(기술 설계서 7.1절). 상태 변경은 이 객체의 잠금으로 직렬화한다.
 * 결과는 {@code COMPLETED}일 때만 공개하며, 실패·취소 시 부분 결과는 버린다(PA-17, PA-18).
 */
public class CompareJob {

    private final String id;
    private final CompareTask task;
    private final CancellationToken token = new CancellationToken();

    private JobStatus status = JobStatus.RUNNING;
    private int processed;
    private List<PairResult> results;
    private ErrorCode errorCode;
    private Side failedSide;
    private String failedEnv;

    CompareJob(String id, CompareTask task) {
        this.id = id;
        this.task = task;
    }

    public String id() {
        return id;
    }

    public CompareTask task() {
        return task;
    }

    public int total() {
        return task.getPairs().size();
    }

    CancellationToken token() {
        return token;
    }

    public synchronized JobStatus status() {
        return status;
    }

    public synchronized int processed() {
        return processed;
    }

    public synchronized ErrorCode errorCode() {
        return errorCode;
    }

    public synchronized Side failedSide() {
        return failedSide;
    }

    public synchronized String failedEnv() {
        return failedEnv;
    }

    /** 완료된 결과. 완료되지 않았으면 null. */
    public synchronized List<PairResult> results() {
        return status == JobStatus.COMPLETED ? results : null;
    }

    synchronized void reportProgress(int value) {
        if (status.isActive()) {
            processed = Math.max(0, Math.min(value, total()));
        }
    }

    /** 중지 요청. 실행 중이면 CANCELLING으로 바꾸고 실행 중인 Statement를 취소한다. */
    void requestCancel() {
        synchronized (this) {
            if (status != JobStatus.RUNNING) {
                return;
            }
            status = JobStatus.CANCELLING;
        }
        token.cancel();
    }

    synchronized void complete(List<PairResult> pairResults) {
        if (status == JobStatus.CANCELLING || token.isCancelled()) {
            status = JobStatus.CANCELLED;
            return;
        }
        results = List.copyOf(pairResults);
        processed = total();
        status = JobStatus.COMPLETED;
    }

    synchronized void cancelled() {
        status = JobStatus.CANCELLED;
    }

    synchronized void fail(ErrorCode code, Side side, String env) {
        if (status == JobStatus.CANCELLING) {
            status = JobStatus.CANCELLED;
            return;
        }
        errorCode = code;
        failedSide = side;
        failedEnv = env;
        status = JobStatus.FAILED;
    }
}
