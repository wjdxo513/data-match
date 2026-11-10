package kr.kicc.datamatch.compare;

/** 거래번호 입력 오류 코드(기술 설계서 5장 FR-05). 화면 문구는 프론트가 화면 정의서에 따라 매핑한다. */
public enum PairErrorReason {
    NO_DELIMITER,
    TOO_MANY_DELIMITERS,
    EMPTY_A,
    EMPTY_B,
    INVALID_NUMBER
}
