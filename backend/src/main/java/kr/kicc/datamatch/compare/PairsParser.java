package kr.kicc.datamatch.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 거래번호 A·B 짝 입력 파서(실행 계획 BE-02, PRD FR-05·PA-17·PA-19, 화면 정의서 WA-03).
 *
 * <ul>
 *   <li>한 줄에 거래번호A와 거래번호B를 쉼표 또는 탭 하나로 구분한다.</li>
 *   <li>값의 앞뒤 공백을 제거하고, 공백만 있는 줄은 무시한다. 줄 번호는 원문 기준 1부터 센다.</li>
 *   <li>한 줄에는 대표 오류 하나만 보고하며, 오류는 모두 수집한다.</li>
 *   <li>유효한 짝이 {@link #MAX_PAIRS}를 넘으면 {@code limitExceeded}로 알린다.</li>
 * </ul>
 * 같은 규칙을 프론트의 pairsParser.ts가 구현하며 두 구현은 {@code test/fixtures/pairs-cases.json}을 함께 쓴다.
 */
public final class PairsParser {

    public static final int MAX_PAIRS = 1000;

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    private PairsParser() {
    }

    public static PairsParseResult parse(String text, boolean numericPk) {
        List<TransactionPair> pairs = new ArrayList<>();
        List<PairLineError> errors = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            String[] lines = text.split("\\r?\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                parseLine(i + 1, lines[i], numericPk, pairs, errors);
            }
        }
        return new PairsParseResult(pairs, errors, pairs.size() > MAX_PAIRS);
    }

    private static void parseLine(int lineNo, String line, boolean numericPk,
                                  List<TransactionPair> pairs, List<PairLineError> errors) {
        if (line.strip().isEmpty()) {
            return;
        }
        int delimiters = 0;
        int position = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ',' || c == '\t') {
                delimiters++;
                if (position < 0) {
                    position = i;
                }
            }
        }
        if (delimiters == 0) {
            errors.add(new PairLineError(lineNo, PairErrorReason.NO_DELIMITER));
            return;
        }
        if (delimiters > 1) {
            errors.add(new PairLineError(lineNo, PairErrorReason.TOO_MANY_DELIMITERS));
            return;
        }
        String a = line.substring(0, position).strip();
        String b = line.substring(position + 1).strip();
        if (a.isEmpty()) {
            errors.add(new PairLineError(lineNo, PairErrorReason.EMPTY_A));
            return;
        }
        if (b.isEmpty()) {
            errors.add(new PairLineError(lineNo, PairErrorReason.EMPTY_B));
            return;
        }
        if (numericPk && (!NUMBER.matcher(a).matches() || !NUMBER.matcher(b).matches())) {
            errors.add(new PairLineError(lineNo, PairErrorReason.INVALID_NUMBER));
            return;
        }
        pairs.add(new TransactionPair(lineNo, a, b));
    }
}
