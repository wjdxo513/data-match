package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** BE-03: JSON 최상위 필드 펼치기 규칙(기술 설계서 5장 FR-07 표)을 확인한다. */
class JsonFlattenerTest {

    @Test
    void mapsValuesByType() throws Exception {
        Map<String, String> m = JsonFlattener.flatten(
                "{\"s\":\"한글 값\",\"i\":12000,\"d\":1.10,\"big\":12345678901234567890,\"t\":true,\"f\":false,"
                        + "\"n\":null,\"o\":{\"k\": \"v\"},\"a\":[1, \"x\"],\"e\":\"\"}");
        assertThat(m).containsExactly(
                entry("s", "한글 값"), entry("i", "12000"), entry("d", "1.10"), entry("big", "12345678901234567890"),
                entry("t", "true"), entry("f", "false"), entry("n", null), entry("o", "{\"k\":\"v\"}"),
                entry("a", "[1,\"x\"]"), entry("e", ""));
    }

    @Test
    void emptyObjectHasNoFields() throws Exception {
        assertThat(JsonFlattener.flatten("{}")).isEmpty();
    }

    @Test
    void invalidJsonIsJsonInvalid() {
        for (String bad : new String[] {"{\"a\":", "", "   ", "not json", "{\"a\":1} trailing", null}) {
            assertThatThrownBy(() -> JsonFlattener.flatten(bad))
                    .as(String.valueOf(bad))
                    .isInstanceOf(JsonFlattener.JsonFlattenException.class)
                    .satisfies(e -> assertThat(((JsonFlattener.JsonFlattenException) e).errorCause())
                            .isEqualTo(DecryptErrorCause.JSON_INVALID));
        }
    }

    @Test
    void nonObjectJsonIsJsonNotObject() {
        for (String other : new String[] {"[1,2]", "123", "\"text\"", "true", "null"}) {
            assertThatThrownBy(() -> JsonFlattener.flatten(other))
                    .as(other)
                    .satisfies(e -> assertThat(((JsonFlattener.JsonFlattenException) e).errorCause())
                            .isEqualTo(DecryptErrorCause.JSON_NOT_OBJECT));
        }
    }

    @Test
    void exceptionMessageDoesNotContainInput() {
        assertThatThrownBy(() -> JsonFlattener.flatten("{\"secret\":\"PLAIN-VALUE\""))
                .hasMessageNotContaining("PLAIN-VALUE");
    }
}
