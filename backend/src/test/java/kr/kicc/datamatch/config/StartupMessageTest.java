package kr.kicc.datamatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StartupMessageTest {

    @Test
    void usesActualLocalServerPortWhenAvailable() {
        MockEnvironment env = new MockEnvironment().withProperty("local.server.port", "18081").withProperty("server.port", "18080");
        assertThat(new StartupMessage(env).accessMessage()).contains("http://localhost:18081");
    }

    @Test
    void fallsBackToConfiguredOrDefaultPort() {
        assertThat(new StartupMessage(new MockEnvironment().withProperty("server.port", "19090")).accessMessage())
                .contains("http://localhost:19090");
        assertThat(new StartupMessage(new MockEnvironment()).accessMessage()).contains("http://localhost:18080");
    }
}
