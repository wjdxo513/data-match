package kr.kicc.datamatch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.kicc.datamatch.web.GlobalExceptionHandler;
import kr.kicc.datamatch.web.TestEndpointsController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** BE-01: 로컬 웹 서버 보안 필터(TD-19)를 확인한다. */
@WebMvcTest(controllers = TestEndpointsController.class)
@Import({LocalOnlyFilter.class, GlobalExceptionHandler.class})
class LocalOnlyFilterTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "localhost:18080", "127.0.0.1", "127.0.0.1:18080", "[::1]", "[::1]:18080",
        "LOCALHOST:18080"})
    void allowsLoopbackHosts(String host) throws Exception {
        mvc.perform(get("/api/__test/ping").header("Host", host))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.example", "evil.example:18080", "192.168.0.10:18080", "localhost.evil.example",
        "127.0.0.1.nip.io"})
    void rejectsOtherHosts(String host) throws Exception {
        mvc.perform(get("/api/__test/ping").header("Host", host))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_REQUEST"));
    }

    @Test
    void rejectsForeignOriginAndAllowsLocalOrMissingOrigin() throws Exception {
        mvc.perform(get("/api/__test/ping").header("Host", "localhost:18080").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/__test/ping").header("Host", "localhost:18080").header("Origin", "null"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/__test/ping").header("Host", "localhost:18080").header("Origin", "http://localhost:18080"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/__test/ping").header("Host", "localhost:18080").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/__test/ping").header("Host", "localhost:18080"))
                .andExpect(status().isOk());
    }

    @Test
    void mutatingRequestsRequireJson() throws Exception {
        mvc.perform(post("/api/__test/ping").header("Host", "localhost").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/__test/ping").header("Host", "localhost")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("a=b"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/__test/ping").header("Host", "localhost"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/__test/ping").header("Host", "localhost")
                        .contentType("application/json;charset=UTF-8").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void helperRulesHandleEdgeCases() {
        assertThat(LocalOnlyFilter.isAllowedHost(null)).isFalse();
        assertThat(LocalOnlyFilter.isAllowedHost(" ")).isFalse();
        assertThat(LocalOnlyFilter.isAllowedHost("[::1")).isFalse();
        assertThat(LocalOnlyFilter.isAllowedOrigin("http://[::1]:18080")).isTrue();
        assertThat(LocalOnlyFilter.isAllowedOrigin("not a uri")).isFalse();
        assertThat(LocalOnlyFilter.isJson("not/a type;;")).isFalse();
    }
}
