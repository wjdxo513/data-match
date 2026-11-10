package kr.kicc.datamatch.compare;

import java.util.ArrayList;
import java.util.List;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;

/**
 * 묶음 조회 SQL 생성(기술 설계서 6.3절, NFR-04). 식별자는 메타데이터에서 읽은 값만 큰따옴표로 감싸 쓰며,
 * 메타데이터에 없는 컬럼은 SQL을 만들기 전에 거절한다. 만드는 SQL은 항상 SELECT 한 가지다.
 */
public final class SqlBuilder {

    private SqlBuilder() {
    }

    public static RowQuery build(TableMetadata table, List<String> columnNames) {
        List<ColumnMeta> selected = table.requireColumns(columnNames);
        ColumnMeta pk = table.column(table.getPkColumn())
                .orElseThrow(() -> new IllegalStateException("PK column missing from metadata"));
        String quotedPk = quote(pk.getName());

        List<String> select = new ArrayList<>();
        select.add(ColumnKind.of(pk.getDataType()).selectExpression(quotedPk));
        for (ColumnMeta c : selected) {
            select.add(ColumnKind.of(c.getDataType()).selectExpression(quote(c.getName())));
        }
        String selectFrom = "SELECT " + String.join(", ", select)
                + " FROM " + quote(table.getOwner()) + "." + quote(table.getName());
        return new RowQuery(selectFrom, quotedPk, pk, table.isNumericPk(), List.copyOf(selected));
    }

    static String quote(String identifier) {
        if (identifier == null || identifier.isEmpty() || identifier.indexOf('"') >= 0 || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid identifier");
        }
        return "\"" + identifier + "\"";
    }
}
