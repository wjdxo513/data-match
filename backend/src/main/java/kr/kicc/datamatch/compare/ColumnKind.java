package kr.kicc.datamatch.compare;

import java.util.Locale;
import java.util.Set;

/**
 * Oracle 타입별 비교 문자열 변환 방식(기술 설계서 6.4절, TD-09). 날짜·시간은 NLS 설정과 무관하도록 SQL에서 형식을 지정해 변환한다.
 */
public enum ColumnKind {
    /** 문자형, CLOB·NCLOB·LONG, BINARY_FLOAT·BINARY_DOUBLE: {@code getString}. */
    TEXT,
    /** NUMBER, FLOAT: {@code getBigDecimal().toPlainString()}. */
    NUMBER,
    DATE,
    TIMESTAMP,
    TIMESTAMP_TZ,
    TIMESTAMP_LTZ,
    /** BLOB, RAW, LONG RAW: 대문자 16진수. */
    BINARY,
    /** 그 밖의 타입(INTERVAL 등): {@code getString}을 시도하고 읽지 못하면 전체 조회 실패(TA-08). */
    OTHER;

    private static final Set<String> TEXT_TYPES = Set.of(
            "CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "CLOB", "NCLOB", "LONG", "BINARY_FLOAT", "BINARY_DOUBLE");
    private static final Set<String> BINARY_TYPES = Set.of("BLOB", "RAW", "LONG RAW");

    public static ColumnKind of(String dataType) {
        String t = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        if (TEXT_TYPES.contains(t)) {
            return TEXT;
        }
        if ("NUMBER".equals(t) || "FLOAT".equals(t)) {
            return NUMBER;
        }
        if ("DATE".equals(t)) {
            return DATE;
        }
        if (t.startsWith("TIMESTAMP")) {
            if (t.endsWith("WITH LOCAL TIME ZONE")) {
                return TIMESTAMP_LTZ;
            }
            return t.endsWith("WITH TIME ZONE") ? TIMESTAMP_TZ : TIMESTAMP;
        }
        if (BINARY_TYPES.contains(t)) {
            return BINARY;
        }
        return OTHER;
    }

    /** SELECT 목록에 넣을 식. {@code quotedColumn}은 메타데이터에서 읽어 큰따옴표로 감싼 컬럼명이다. */
    public String selectExpression(String quotedColumn) {
        switch (this) {
            case DATE:
                return "TO_CHAR(" + quotedColumn + ", 'YYYY-MM-DD HH24:MI:SS')";
            case TIMESTAMP:
            case TIMESTAMP_LTZ:
                return "TO_CHAR(" + quotedColumn + ", 'YYYY-MM-DD HH24:MI:SS.FF9')";
            case TIMESTAMP_TZ:
                return "TO_CHAR(" + quotedColumn + ", 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM')";
            default:
                return quotedColumn;
        }
    }
}
