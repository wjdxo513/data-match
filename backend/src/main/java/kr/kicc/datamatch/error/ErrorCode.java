package kr.kicc.datamatch.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드와 HTTP 상태(기술 설계서 8장·9.5절, 실행 계획 BE-01·PL-04).
 * 메시지는 로그·개발 확인용 영문이며, 화면 문구는 프론트가 코드로 매핑한다.
 */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid input"),
    ENV_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "Environment is not configured"),
    TABLE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Table is not supported"),
    JOB_RUNNING(HttpStatus.CONFLICT, "Another comparison job is running"),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Job not found"),
    JOB_NOT_COMPLETED(HttpStatus.CONFLICT, "Job is not completed"),
    PAIR_NOT_FOUND(HttpStatus.NOT_FOUND, "Pair not found"),
    DB_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Database connection failed"),
    DB_QUERY_FAILED(HttpStatus.BAD_GATEWAY, "Database query failed"),
    QUERY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Database query timed out"),
    EXCEL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Excel generation failed"),
    FORBIDDEN_REQUEST(HttpStatus.FORBIDDEN, "Request is not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only application/json is accepted"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
