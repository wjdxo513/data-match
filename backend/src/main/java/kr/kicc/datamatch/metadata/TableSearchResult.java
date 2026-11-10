package kr.kicc.datamatch.metadata;

import java.util.List;
import lombok.Value;

/** 테이블 검색 결과. 후보가 30건을 넘으면 30건만 담고 {@code truncated}가 true다(TD-07). */
@Value
public class TableSearchResult {
    List<TableCandidate> items;
    boolean truncated;
}
