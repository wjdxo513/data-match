package kr.kicc.datamatch.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import lombok.Value;

/**
 * 비교할 수 있는 테이블의 메타데이터(단일 지원 PK). SQL에 넣는 식별자는 이 객체의 값만 쓴다(NFR-04, 기술 설계서 6.2절).
 */
@Value
public class TableMetadata {
    String owner;
    String name;
    String pkColumn;
    String pkDataType;
    List<ColumnMeta> columns;

    public boolean isNumericPk() {
        return "NUMBER".equals(pkDataType);
    }

    public Optional<ColumnMeta> column(String columnName) {
        return columns.stream().filter(c -> c.getName().equals(columnName)).findFirst();
    }

    /** 메타데이터에 없는 컬럼 이름이 있으면 {@code INVALID_INPUT}으로 거절하고, 있으면 메타데이터의 컬럼을 요청 순서로 돌려준다. */
    public List<ColumnMeta> requireColumns(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "no columns selected");
        }
        return columnNames.stream()
                .map(n -> column(n).orElseThrow(() -> new DataMatchException(ErrorCode.INVALID_INPUT, "unknown column",
                        Map.of("column", String.valueOf(n)))))
                .collect(java.util.stream.Collectors.toList());
    }
}
