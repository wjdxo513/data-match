package kr.kicc.datamatch.compare;

import lombok.Value;

/**
 * 컬럼(복호화 컬럼은 JSON 필드) 하나의 비교 결과.
 * {@code name}은 일반 컬럼이면 컬럼명, 복호화 컬럼이면 {@code 컬럼명.필드명}이다(FR-08).
 * 복호화 오류 행은 {@code a}, {@code b}가 null이고 {@code cause}, {@code errorSide}를 가진다.
 */
@Value
public class FieldResult {
    String name;
    String column;
    String field;
    String a;
    String b;
    RowStatus status;
    DecryptErrorCause cause;
    Side errorSide;

    static FieldResult compared(String column, String field, String a, String b) {
        String name = field == null ? column : column + "." + field;
        RowStatus status = java.util.Objects.equals(a, b) ? RowStatus.MATCH : RowStatus.MISMATCH;
        return new FieldResult(name, column, field, a, b, status, null, null);
    }

    static FieldResult decryptError(String column, DecryptErrorCause cause, Side side) {
        return new FieldResult(column, column, null, null, null, RowStatus.DECRYPT_ERROR, cause, side);
    }

    public boolean isMismatchOrError() {
        return status != RowStatus.MATCH;
    }
}
