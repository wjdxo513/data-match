package kr.kicc.datamatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** BE-01: 로그 설정(일별 롤링, 7일 보관)과 부가 엔드포인트 미포함을 확인한다(TD-20, 9.3절). */
class LoggingAndClasspathPolicyTest {

    @Test
    void logbackRollsDailyAndKeepsSevenDays() throws IOException {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml).contains("TimeBasedRollingPolicy").contains("%d{yyyy-MM-dd}")
                .contains("<maxHistory>7</maxHistory>").contains("${LOG_PATH:-logs}");
    }

    @Test
    void actuatorAndH2ConsoleAreNotOnClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.boot.actuate.endpoint.annotation.Endpoint"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.h2.Driver")).isInstanceOf(ClassNotFoundException.class);
    }
}
