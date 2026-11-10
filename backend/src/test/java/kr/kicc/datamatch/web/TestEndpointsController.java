package kr.kicc.datamatch.web;

import java.util.List;
import java.util.Map;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BE-01 테스트 전용 컨트롤러(필터·예외 처리 확인용). 운영 코드에는 포함되지 않는다. */
@RestController
@RequestMapping("/api/__test")
public class TestEndpointsController {

    @GetMapping("/ping")
    String ping() {
        return "pong";
    }

    @PostMapping("/ping")
    String postPing(@RequestBody Map<String, Object> body) {
        return "pong";
    }

    @PostMapping("/invalid")
    String invalid(@RequestBody Map<String, Object> body) {
        throw new DataMatchException(ErrorCode.INVALID_INPUT, "Invalid pairs input",
                List.of(Map.of("line", 2, "reason", "NO_DELIMITER")));
    }

    @PostMapping("/boom")
    String boom(@RequestBody Map<String, Object> body) {
        throw new IllegalStateException("unexpected failure");
    }

    @GetMapping("/db")
    String db() {
        throw new DataMatchException(ErrorCode.DB_CONNECTION_FAILED, "Database connection failed: env=dev");
    }
}
