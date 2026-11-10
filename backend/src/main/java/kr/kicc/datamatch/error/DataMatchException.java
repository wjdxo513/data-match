package kr.kicc.datamatch.error;

/**
 * DataMatch 업무 예외. 메시지에는 거래번호, 컬럼 값, 평문, 접속 정보를 넣지 않는다(TD-20).
 * {@code details}는 화면이 쓰는 구조화된 정보(예: 오류 줄 목록)이며 응답의 {@code details}로 전달된다.
 */
public class DataMatchException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object details;

    public DataMatchException(ErrorCode code) {
        this(code, code.defaultMessage(), null, null);
    }

    public DataMatchException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public DataMatchException(ErrorCode code, String message, Object details) {
        this(code, message, details, null);
    }

    public DataMatchException(ErrorCode code, String message, Object details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Object details() {
        return details;
    }
}
