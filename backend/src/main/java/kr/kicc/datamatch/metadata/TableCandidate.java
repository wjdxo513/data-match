package kr.kicc.datamatch.metadata;

import kr.kicc.datamatch.metadata.PkSupport.UnsupportedReason;
import lombok.Value;

/** 테이블 검색 후보(기술 설계서 8장 {@code GET /api/tables}의 items). 선택 불가 사유의 문구는 프론트가 만든다. */
@Value
public class TableCandidate {
    String owner;
    String name;
    boolean selectable;
    UnsupportedReason reason;
    String pkColumn;
}
