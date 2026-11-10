package kr.kicc.datamatch.metadata;

import java.util.List;
import java.util.Set;
import lombok.Value;

/** PK 판정(PRD PA-10, PA-19): 단일 PK이고 타입이 문자형 또는 NUMBER여야 비교할 수 있다. */
@Value
public class PkSupport {

    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "NUMBER");

    boolean selectable;
    UnsupportedReason reason;
    String pkColumn;
    String pkDataType;

    public static PkSupport judge(List<PkColumn> pk) {
        if (pk == null || pk.isEmpty()) {
            return new PkSupport(false, UnsupportedReason.NO_PK, null, null);
        }
        if (pk.size() > 1) {
            return new PkSupport(false, UnsupportedReason.COMPOSITE_PK, null, null);
        }
        PkColumn column = pk.get(0);
        if (!SUPPORTED_TYPES.contains(column.getDataType())) {
            return new PkSupport(false, UnsupportedReason.UNSUPPORTED_PK_TYPE, column.getName(), column.getDataType());
        }
        return new PkSupport(true, null, column.getName(), column.getDataType());
    }

    public boolean isNumeric() {
        return "NUMBER".equals(pkDataType);
    }

    public enum UnsupportedReason {
        NO_PK,
        COMPOSITE_PK,
        UNSUPPORTED_PK_TYPE
    }
}
