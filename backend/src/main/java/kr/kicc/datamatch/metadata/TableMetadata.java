package kr.kicc.datamatch.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import lombok.Value;

/**
 * 테이블 메타데이터와 PK 구성. SQL에 넣는 식별자는 이 객체의 값만 쓴다(NFR-04, 기술 설계서 6.2절).
 * 비교 키는 사용자가 고르며, 단일 PK이고 비교 키 가능 타입이면 그 PK가 기본값이다(PRD PA-10·19).
 */
@Value
public class TableMetadata {
    String owner;
    String name;
    /** PK 구성 컬럼(순서대로). PK가 없으면 빈 목록. */
    List<String> pkColumns;
    List<ColumnMeta> columns;

    public Optional<ColumnMeta> column(String columnName) {
        return columns.stream().filter(c -> c.getName().equals(columnName)).findFirst();
    }

    public boolean isPk(String columnName) {
        return pkColumns.contains(columnName);
    }

    /** 기본 비교 키: PK가 한 컬럼이고 비교 키 가능 타입이면 그 컬럼, 아니면 null. */
    public String defaultKeyColumn() {
        if (pkColumns.size() != 1) {
            return null;
        }
        return column(pkColumns.get(0)).filter(ColumnMeta::isKeyable).map(ColumnMeta::getName).orElse(null);
    }

    /** 비교 키 검증: 메타데이터에 있고 비교 키 가능 타입이어야 한다. 아니면 {@code INVALID_INPUT}. */
    public ColumnMeta requireKeyColumn(String keyColumn) {
        if (keyColumn == null || keyColumn.isBlank()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "key column required");
        }
        ColumnMeta key = column(keyColumn).orElseThrow(() -> new DataMatchException(ErrorCode.INVALID_INPUT,
                "unknown key column", Map.of("column", keyColumn)));
        if (!key.isKeyable()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "key column type not supported", Map.of("column", keyColumn));
        }
        return key;
    }

    /** 메타데이터에 없는 컬럼 이름이 있으면 {@code INVALID_INPUT}으로 거절하고, 있으면 메타데이터의 컬럼을 요청 순서로 돌려준다. */
    public List<ColumnMeta> requireColumns(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "no columns selected");
        }
        return columnNames.stream()
                .map(n -> column(n).orElseThrow(() -> new DataMatchException(ErrorCode.INVALID_INPUT, "unknown column",
                        Map.of("column", String.valueOf(n)))))
                .collect(Collectors.toList());
    }
}
