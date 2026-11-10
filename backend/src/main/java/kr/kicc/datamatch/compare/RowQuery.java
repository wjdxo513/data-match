package kr.kicc.datamatch.compare;

import java.util.Collections;
import java.util.List;
import kr.kicc.datamatch.metadata.ColumnMeta;
import lombok.Value;

/**
 * {@link SqlBuilder}가 만든 묶음 조회 쿼리. 첫 번째 열은 매칭용 PK이고 그 뒤에 선택 컬럼이 요청 순서로 온다.
 * SQL 문자열에는 식별자만 있고 거래번호는 바인드 변수(?)로만 전달한다.
 */
@Value
public class RowQuery {
    String selectFrom;
    String quotedPk;
    ColumnMeta pkColumn;
    boolean numericPk;
    List<ColumnMeta> columns;

    /** 키 {@code keyCount}개의 IN 목록을 가진 SQL. */
    public String sql(int keyCount) {
        if (keyCount < 1) {
            throw new IllegalArgumentException("at least one key is required");
        }
        return selectFrom + " WHERE " + quotedPk + " IN (" + String.join(", ", Collections.nCopies(keyCount, "?")) + ")";
    }
}
