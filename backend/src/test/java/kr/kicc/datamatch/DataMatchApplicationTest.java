package kr.kicc.datamatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** SETUP-01: 스프링 컨텍스트 로드와 서버 바인딩 설정을 확인한다. */
@SpringBootTest
class DataMatchApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        assertThat(environment).isNotNull();
    }

    @Test
    void serverBindsToLocalhostOnPort18080() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.port")).isEqualTo("18080");
    }
}
