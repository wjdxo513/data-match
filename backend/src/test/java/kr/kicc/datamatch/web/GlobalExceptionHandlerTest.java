package kr.kicc.datamatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.kicc.datamatch.config.LocalOnlyFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** BE-01: 오류 응답 형식과 로그의 민감정보 비노출을 확인한다. */
@WebMvcTest(controllers = TestEndpointsController.class)
@Import({GlobalExceptionHandler.class, LocalOnlyFilter.class})
@TestPropertySource(properties = "datamatch.environments.dev.password=SECRET-PW-123")
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String SECRET_TRX = "SECRET-TRX-98765";

    @Autowired
    private MockMvc mvc;

    @Test
    void dataMatchExceptionIsReturnedAsApiErrorWithDetails() throws Exception {
        mvc.perform(post("/api/__test/invalid").header("Host", "localhost:18080")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pairsText\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("Invalid pairs input"))
                .andExpect(jsonPath("$.details[0].line").value(2))
                .andExpect(jsonPath("$.details[0].reason").value("NO_DELIMITER"));
    }

    @Test
    void dbConnectionFailureMapsTo503() throws Exception {
        mvc.perform(get("/api/__test/db").header("Host", "localhost"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_CONNECTION_FAILED"));
    }

    @Test
    void unexpectedExceptionHidesInternals(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(post("/api/__test/boom").header("Host", "127.0.0.1:18080")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trx\":\"" + SECRET_TRX + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("at kr.").doesNotContain("unexpected failure")
                .doesNotContain("SELECT").doesNotContain("jdbc:");
        assertThat(output.getAll()).doesNotContain(SECRET_TRX).doesNotContain("SECRET-PW-123");
    }

    @Test
    void malformedJsonIsInvalidInput(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/__test/ping").header("Host", "localhost")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trx\":\"" + SECRET_TRX))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(output.getAll()).doesNotContain(SECRET_TRX);
    }
}
