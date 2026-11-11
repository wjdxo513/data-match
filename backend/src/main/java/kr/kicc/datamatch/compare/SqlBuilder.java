package kr.kicc.datamatch.compare;

import java.util.ArrayList;
import java.util.List;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;

/**
 * 묶음 조회 SQL 생성(기술 설계서 6.3절, NFR-04). 식별자는 메타데이터에서 읽은 값만 큰따옴표로 감싸 쓰며,
 * 메타데이터에 없는 컬럼이나 비교 키로 쓸 수 없는 컬럼은 SQL을 만들기 전에 거절한다. 만드는 SQL은 항상 SELECT 한 가지다.
 */
public final class SqlBuilder {

    private SqlBuilder() {
    }

    /**
     * @param keyColumn 거래번호로 행을 찾을 비교 키 컬럼(PRD PA-10). 메타데이터에 있고 문자형·NUMBER여야 한다.
     */
    public static RowQuery build(TableMetadata table, String keyColumn, List<String> columnNames) {
        ColumnMeta key = table.requireKeyColumn(keyColumn);
        List<ColumnMeta> selected = table.requireColumns(columnNames);
        String quotedKey = quote(key.getName());

        List<String> select = new ArrayList<>();
        select.add(ColumnKind.of(key.getDataType()).selectExpression(quotedKey));
        for (ColumnMeta c : selected) {
            select.add(ColumnKind.of(c.getDataType()).selectExpression(quote(c.getName())));
        }
        String selectFrom = "SELECT " + String.join(", ", select)
                + " FROM " + quote(table.getOwner()) + "." + quote(table.getName());
        return new RowQuery(selectFrom, quotedKey, key, key.isNumber(), List.copyOf(selected));
    }

    static String quote(String identifier) {
        if (identifier == null || identifier.isEmpty() || identifier.indexOf('"') >= 0 || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid identifier");
        }
        return "\"" + identifier + "\"";
    }
}
