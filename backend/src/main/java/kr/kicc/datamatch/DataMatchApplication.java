package kr.kicc.datamatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * DataMatch 애플리케이션 시작 클래스.
 *
 * <p>환경별 DataSource는 {@code DataSourceRegistry}가 필요할 때 직접 만들므로(TD-05)
 * Spring Boot의 단일 DataSource 자동 구성은 사용하지 않는다.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class DataMatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataMatchApplication.class, args);
    }
}
