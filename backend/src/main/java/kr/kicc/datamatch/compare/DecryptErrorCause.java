package kr.kicc.datamatch.compare;

/** 복호화 오류 원인(기술 설계서 5장 FR-07). */
public enum DecryptErrorCause {
    DECRYPT_FAILED,
    JSON_INVALID,
    JSON_NOT_OBJECT
}
