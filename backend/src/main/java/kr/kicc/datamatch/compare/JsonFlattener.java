package kr.kicc.datamatch.compare;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 복호화 결과 JSON의 최상위 필드를 펼친다(기술 설계서 TD-11, 5장 FR-07, PRD PA-09).
 *
 * <ul>
 *   <li>문자열은 그대로, 숫자는 원문 표기 그대로({@code 1.10}은 {@code 1.10}), 불리언은 true/false, JSON null은 null.</li>
 *   <li>중첩 객체·배열은 compact JSON 문자열로 비교한다.</li>
 *   <li>문법 오류는 {@link DecryptErrorCause#JSON_INVALID}, 최상위가 객체가 아니면 {@link DecryptErrorCause#JSON_NOT_OBJECT}.</li>
 * </ul>
 */
public final class JsonFlattener {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private JsonFlattener() {
    }

    public static Map<String, String> flatten(String json) throws JsonFlattenException {
        JsonNode root;
        try {
            root = json == null ? null : MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonFlattenException(DecryptErrorCause.JSON_INVALID);
        }
        if (root == null || root.isMissingNode()) {
            throw new JsonFlattenException(DecryptErrorCause.JSON_INVALID);
        }
        if (!root.isObject()) {
            throw new JsonFlattenException(DecryptErrorCause.JSON_NOT_OBJECT);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            fields.put(e.getKey(), valueOf(e.getValue()));
        }
        return fields;
    }

    private static String valueOf(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBigDecimal()) {
            return node.decimalValue().toPlainString();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    /** JSON 해석 실패. 원문을 담지 않는다. */
    public static final class JsonFlattenException extends Exception {

        private final DecryptErrorCause cause;

        JsonFlattenException(DecryptErrorCause cause) {
            super(cause.name());
            this.cause = cause;
        }

        public DecryptErrorCause errorCause() {
            return cause;
        }
    }
}
