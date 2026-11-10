package kr.kicc.datamatch.compare;

import kr.kicc.datamatch.error.ErrorCode;

/** 비교 작업 전체 실패(기술 설계서 6.5절, PA-17). 실패한 쪽(A/B)과 환경을 상태 응답에 담는다. */
public class CompareFailedException extends RuntimeException {

    private final ErrorCode code;
    private final Side side;
    private final String env;

    public CompareFailedException(ErrorCode code, Side side, String env) {
        super("compare failed: " + code + " side=" + side + " env=" + env, null, false, false);
        this.code = code;
        this.side = side;
        this.env = env;
    }

    public ErrorCode code() {
        return code;
    }

    public Side side() {
        return side;
    }

    public String env() {
        return env;
    }
}
