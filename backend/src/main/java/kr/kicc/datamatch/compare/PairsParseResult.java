package kr.kicc.datamatch.compare;

import java.util.List;
import lombok.Value;

/** 거래번호 짝 파싱 결과(BE-02). */
@Value
public class PairsParseResult {

    /** 유효한 짝(입력 순서). */
    List<TransactionPair> pairs;

    /** 줄 단위 형식 오류(원문 줄 번호 순). */
    List<PairLineError> errors;

    /** 유효한 짝이 최대 건수(1,000쌍)를 넘었는지 여부(PAIR_LIMIT_EXCEEDED). */
    boolean limitExceeded;

    public PairsParseResult(List<TransactionPair> pairs, List<PairLineError> errors, boolean limitExceeded) {
        this.pairs = List.copyOf(pairs);
        this.errors = List.copyOf(errors);
        this.limitExceeded = limitExceeded;
    }

    public int pairCount() {
        return pairs.size();
    }

    /** 유효한 짝도 오류도 없는 빈 입력인지(SA-04). */
    public boolean isEmpty() {
        return pairs.isEmpty() && errors.isEmpty();
    }

    /** 비교를 시작할 수 있는지: 짝이 있고, 오류가 없고, 최대 건수 이하(PA-17). */
    public boolean isValid() {
        return !pairs.isEmpty() && errors.isEmpty() && !limitExceeded;
    }
}
