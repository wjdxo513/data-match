package kr.kicc.datamatch.safedb;

/** SafeDB 복호화 정책(PRD FR-04, 기술 설계서 TD-10). 기본은 KDKEY다. */
public enum DecryptPolicy {
    KDKEY,
    PANEN
}
