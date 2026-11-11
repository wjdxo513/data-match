package kr.kicc.datamatch.metadata;

import lombok.Value;

/**
 * 테이블 검색 후보(기술 설계서 8장 {@code GET /api/tables}의 items).
 * PRD v1.4부터 모든 테이블을 선택할 수 있어 소유자·이름만 담는다(PA-10).
 */
@Value
public class TableCandidate {
    String owner;
    String name;
}
