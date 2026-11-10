package kr.kicc.datamatch.compare;

import java.util.List;
import lombok.Value;

/**
 * 거래번호 짝 하나의 비교 결과(FR-08). {@code index}는 입력 순서(1부터).
 * 누락 짝은 {@code rows}가 비어 있고 {@code missing}에 없는 쪽을 담는다.
 * {@code mismatchCount}는 불일치와 복호화 오류 행의 합이다.
 */
@Value
public class PairResult {
    int index;
    String a;
    String b;
    PairStatus status;
    MissingSide missing;
    List<FieldResult> rows;
    int mismatchCount;
}
