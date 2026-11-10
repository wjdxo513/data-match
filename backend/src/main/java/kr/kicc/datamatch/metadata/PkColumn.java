package kr.kicc.datamatch.metadata;

import lombok.Value;

/** PK 제약의 컬럼과 데이터 타입(ALL_TAB_COLUMNS.DATA_TYPE). */
@Value
public class PkColumn {
    String name;
    String dataType;
}
