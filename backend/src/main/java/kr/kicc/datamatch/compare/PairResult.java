package kr.kicc.datamatch.compare;

import java.util.List;
import lombok.Value;

/**
 * 거래번호 짝 하나의 비교 결과(FR-08). {@code index}는 입력 순서(1부터).
 * 누락 짝은 {@code rows}가 비어 있고 {@code missing}에 없는 쪽을 담는다.
 * 키 중복 짝은 {@code rows}가 비어 있고 {@code duplicate}에 양쪽 조회 건수를 담는다(PRD v1.4 PA-10).
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
    DuplicateCount duplicate;

    public PairResult(int index, String a, String b, PairStatus status, MissingSide missing, List<FieldResult> rows,
                      int mismatchCount, DuplicateCount duplicate) {
        this.index = index;
        this.a = a;
        this.b = b;
        this.status = status;
        this.missing = missing;
        this.rows = rows;
        this.mismatchCount = mismatchCount;
        this.duplicate = duplicate;
    }

    public PairResult(int index, String a, String b, PairStatus status, MissingSide missing, List<FieldResult> rows,
                      int mismatchCount) {
        this(index, a, b, status, missing, rows, mismatchCount, null);
    }

    /** 키 중복 짝: 컬럼 비교 없이 양쪽 조회 건수만 기록한다. */
    public static PairResult duplicate(int index, TransactionPair pair, int countA, int countB) {
        return new PairResult(index, pair.getA(), pair.getB(), PairStatus.DUPLICATE, null, List.of(), 0,
                new DuplicateCount(countA, countB));
    }

    /** 비교 키로 조회한 A·B 행 수. 어느 쪽이든 2 이상이면 키 중복이다. */
    @Value
    public static class DuplicateCount {
        int a;
        int b;
    }
}
