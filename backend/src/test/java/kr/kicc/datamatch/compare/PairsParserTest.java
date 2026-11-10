package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * BE-02: 공유 테스트 케이스(test/fixtures/pairs-cases.json)로 파서를 검증한다.
 * 같은 파일을 프론트 pairsParser.ts 테스트(FE-01)도 사용한다.
 */
class PairsParserTest {

    private static final File FIXTURE = new File("../test/fixtures/pairs-cases.json");

    @TestFactory
    Stream<DynamicTest> sharedFixtureCases() throws IOException {
        JsonNode root = new ObjectMapper().readTree(FIXTURE);
        assertThat(root.get("maxPairs").asInt()).isEqualTo(PairsParser.MAX_PAIRS);
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> verifyCase(c)));
        }
        return tests.stream();
    }

    private static void verifyCase(JsonNode c) {
        PairsParseResult result = PairsParser.parse(inputOf(c), c.get("numericPk").asBoolean());
        JsonNode expected = c.get("expected");

        if (expected.has("pairs")) {
            List<List<Object>> expectedPairs = new ArrayList<>();
            for (JsonNode p : expected.get("pairs")) {
                expectedPairs.add(List.of(p.get(0).asInt(), p.get(1).asText(), p.get(2).asText()));
            }
            List<List<Object>> actualPairs = result.getPairs().stream()
                    .map(p -> List.<Object>of(p.getLine(), p.getA(), p.getB()))
                    .collect(Collectors.toList());
            assertThat(actualPairs).isEqualTo(expectedPairs);
        }

        List<List<Object>> expectedErrors = new ArrayList<>();
        for (JsonNode e : expected.get("errors")) {
            expectedErrors.add(List.of(e.get(0).asInt(), e.get(1).asText()));
        }
        List<List<Object>> actualErrors = result.getErrors().stream()
                .map(e -> List.<Object>of(e.getLine(), e.getReason().name()))
                .collect(Collectors.toList());
        assertThat(actualErrors).isEqualTo(expectedErrors);

        assertThat(result.pairCount()).isEqualTo(expected.get("pairCount").asInt());
        assertThat(result.isLimitExceeded()).isEqualTo(expected.get("limitExceeded").asBoolean());
    }

    private static String inputOf(JsonNode c) {
        if (c.has("repeat")) {
            int count = c.get("repeat").get("count").asInt();
            String template = c.get("repeat").get("template").asText();
            return IntStream.rangeClosed(1, count)
                    .mapToObj(n -> template.replace("{n}", String.valueOf(n)))
                    .collect(Collectors.joining("\n"));
        }
        return c.get("input").asText();
    }

    @Test
    void nullInputIsEmpty() {
        PairsParseResult result = PairsParser.parse(null, false);
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validityRequiresPairsNoErrorsAndWithinLimit() {
        assertThat(PairsParser.parse("A1,B1", false).isValid()).isTrue();
        assertThat(PairsParser.parse("A1,B1\nbad", false).isValid()).isFalse();
        assertThat(PairsParser.parse("", false).isValid()).isFalse();
        String overLimit = IntStream.rangeClosed(1, PairsParser.MAX_PAIRS + 1)
                .mapToObj(n -> "A" + n + ",B" + n).collect(Collectors.joining("\n"));
        PairsParseResult over = PairsParser.parse(overLimit, false);
        assertThat(over.isLimitExceeded()).isTrue();
        assertThat(over.isValid()).isFalse();
        assertThat(over.isEmpty()).isFalse();
    }

    @Test
    void resultListsAreImmutable() {
        PairsParseResult result = PairsParser.parse("A1,B1", false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.getPairs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
