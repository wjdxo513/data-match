package kr.kicc.datamatch.metadata;

import lombok.Value;

/** ALL_TAB_COLUMNS의 컬럼 정보. */
@Value
public class ColumnMeta {
    String name;
    String dataType;
    Integer dataLength;
    Integer dataPrecision;
    Integer dataScale;
    Integer charLength;

    /** 화면 표시용 타입 문자열(예: {@code VARCHAR2(20)}, {@code NUMBER(15,2)}, {@code CLOB}). */
    public String displayType() {
        String t = dataType == null ? "" : dataType;
        switch (t) {
            case "CHAR":
            case "VARCHAR2":
            case "NCHAR":
            case "NVARCHAR2":
                return charLength != null && charLength > 0 ? t + "(" + charLength + ")" : t;
            case "RAW":
                return dataLength != null ? t + "(" + dataLength + ")" : t;
            case "NUMBER":
                if (dataPrecision == null) {
                    return t;
                }
                return dataScale == null || dataScale == 0
                        ? t + "(" + dataPrecision + ")"
                        : t + "(" + dataPrecision + "," + dataScale + ")";
            default:
                return t;
        }
    }

    public boolean isClob() {
        return "CLOB".equals(dataType);
    }

    /** 비교 키로 쓸 수 있는 타입: 문자형과 NUMBER(PRD PA-19). */
    public boolean isKeyable() {
        return KEYABLE_TYPES.contains(dataType);
    }

    public boolean isNumber() {
        return "NUMBER".equals(dataType);
    }

    private static final java.util.Set<String> KEYABLE_TYPES = java.util.Set.of("CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "NUMBER");
}
