package kr.kicc.datamatch.compare;

import kr.kicc.datamatch.safedb.DecryptPolicy;
import lombok.Value;

/** 비교 컬럼과 복호화 지정. {@code decrypt}가 null이면 일반 컬럼이다(FR-03, FR-04). */
@Value
public class ColumnSpec {
    String name;
    DecryptPolicy decrypt;

    public static ColumnSpec plain(String name) {
        return new ColumnSpec(name, null);
    }

    public static ColumnSpec decrypted(String name, DecryptPolicy policy) {
        return new ColumnSpec(name, policy);
    }

    public boolean isDecrypted() {
        return decrypt != null;
    }
}
