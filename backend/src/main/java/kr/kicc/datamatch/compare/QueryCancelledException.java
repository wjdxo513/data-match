package kr.kicc.datamatch.compare;

/** 사용자 중지로 조회가 취소되었음을 알리는 신호. 오류가 아니라 작업을 {@code CANCELLED}로 끝내는 데 쓴다(기술 설계서 6.5절). */
public class QueryCancelledException extends RuntimeException {

    public QueryCancelledException() {
        super("query cancelled by user", null, false, false);
    }
}
