package kr.kicc.datamatch.web;

import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외를 {@link ApiError} 응답으로 바꾼다(BE-01, 기술 설계서 8장·9.4절·9.5절).
 * 로그에는 오류 코드와 예외 종류만 남기고 요청 본문·값은 남기지 않는다. 예상하지 못한 예외만 스택을 로그 파일에 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataMatchException.class)
    public ResponseEntity<ApiError> handleDataMatch(DataMatchException e) {
        log.warn("request failed: code={}", e.code());
        return ResponseEntity.status(e.code().status()).body(new ApiError(e.code().name(), e.getMessage(), e.details()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception e) {
        log.warn("request failed: code={}, type={}", ErrorCode.INVALID_INPUT, e.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(ApiError.of(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("unexpected error: type={}", e.getClass().getName(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ApiError.of(ErrorCode.INTERNAL_ERROR));
    }
}
