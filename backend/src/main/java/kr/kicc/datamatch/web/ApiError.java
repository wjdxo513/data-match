package kr.kicc.datamatch.web;

import kr.kicc.datamatch.error.ErrorCode;

/**
 * 모든 API 오류 응답의 형식 {@code {code, message, details}}(기술 설계서 8장).
 * 스택 트레이스, SQL, 접속 정보, 평문 값은 담지 않는다.
 */
public final class ApiError {

    private final String code;
    private final String message;
    private final Object details;

    public ApiError(String code, String message, Object details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public static ApiError of(ErrorCode code) {
        return new ApiError(code.name(), code.defaultMessage(), null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getDetails() {
        return details;
    }
}
