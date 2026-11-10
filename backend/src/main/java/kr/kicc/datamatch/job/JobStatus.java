package kr.kicc.datamatch.job;

/** 비교 작업 상태(기술 설계서 7.1절, TD-12). */
public enum JobStatus {
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isActive() {
        return this == RUNNING || this == CANCELLING;
    }
}
