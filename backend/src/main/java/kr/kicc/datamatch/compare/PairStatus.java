package kr.kicc.datamatch.compare;

/** 짝 단위 판정(PRD 6.1). */
public enum PairStatus {
    MATCH,
    MISMATCH,
    MISSING,
    /** 비교 키로 A 또는 B 행이 2건 이상 조회됨(PRD v1.4 PA-10). 컬럼 비교를 하지 않는다. */
    DUPLICATE
}
