package kr.kicc.datamatch.metadata;

import lombok.Value;

/** 데이터 사전에서 읽은 소유자·테이블 이름. */
@Value
public class TableName {
    String owner;
    String name;
}
