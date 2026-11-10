package kr.kicc.datamatch.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** BE-01: 모든 오류 코드에 HTTP 상태가 하나씩 정해져 있는지 확인한다(기술 설계서 8장 표와 일치). */
class ErrorCodeTest {

    @Test
    void everyCodeHasExpectedStatus() {
        Map<ErrorCode, HttpStatus> expected = new LinkedHashMap<>();
        expected.put(ErrorCode.INVALID_INPUT, HttpStatus.BAD_REQUEST);
        expected.put(ErrorCode.ENV_NOT_CONFIGURED, HttpStatus.BAD_REQUEST);
        expected.put(ErrorCode.TABLE_NOT_SUPPORTED, HttpStatus.BAD_REQUEST);
        expected.put(ErrorCode.JOB_RUNNING, HttpStatus.CONFLICT);
        expected.put(ErrorCode.JOB_NOT_FOUND, HttpStatus.NOT_FOUND);
        expected.put(ErrorCode.JOB_NOT_COMPLETED, HttpStatus.CONFLICT);
        expected.put(ErrorCode.PAIR_NOT_FOUND, HttpStatus.NOT_FOUND);
        expected.put(ErrorCode.DB_CONNECTION_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
        expected.put(ErrorCode.DB_QUERY_FAILED, HttpStatus.BAD_GATEWAY);
        expected.put(ErrorCode.QUERY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        expected.put(ErrorCode.EXCEL_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        expected.put(ErrorCode.FORBIDDEN_REQUEST, HttpStatus.FORBIDDEN);
        expected.put(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        expected.put(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(ErrorCode.values()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((code, status) -> {
            assertThat(code.status()).as(code.name()).isEqualTo(status);
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
        });
    }

    @Test
    void exceptionCarriesCodeMessageAndDetails() {
        DataMatchException e = new DataMatchException(ErrorCode.JOB_RUNNING);
        assertThat(e.code()).isEqualTo(ErrorCode.JOB_RUNNING);
        assertThat(e.getMessage()).isEqualTo(ErrorCode.JOB_RUNNING.defaultMessage());
        assertThat(e.details()).isNull();
        assertThat(new DataMatchException(ErrorCode.INVALID_INPUT, "m", "d").details()).isEqualTo("d");
    }
}
