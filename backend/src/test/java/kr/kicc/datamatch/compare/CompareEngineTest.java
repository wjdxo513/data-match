package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import kr.kicc.datamatch.safedb.DecryptSession;
import kr.kicc.datamatch.safedb.FakeDecryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-03: 판정 규칙(PRD 6.1)과 복호화·JSON 비교(PRD 6.2, 기술 설계서 5장)를 확인한다. */
class CompareEngineTest {

    private static final TransactionPair PAIR = new TransactionPair(1, "A1", "B1");

    private FakeDecryptor decryptor;
    private DecryptSession session;

    @BeforeEach
    void setUp() {
        decryptor = new FakeDecryptor();
        session = decryptor.openSession();
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private PairResult compare(List<ColumnSpec> columns, Map<String, String> a, Map<String, String> b) {
        return CompareEngine.compare(1, PAIR, columns, a, b, session);
    }

    @Test
    void bothNullIsMatchAndOneSideNullIsMismatch() {
        PairResult r = compare(List.of(ColumnSpec.plain("MEMO"), ColumnSpec.plain("NOTE")),
                row("MEMO", null, "NOTE", "x"), row("MEMO", null, "NOTE", null));
        assertThat(r.getRows()).extracting(FieldResult::getName, FieldResult::getStatus)
                .containsExactly(tuple("MEMO", RowStatus.MATCH), tuple("NOTE", RowStatus.MISMATCH));
        assertThat(r.getStatus()).isEqualTo(PairStatus.MISMATCH);
        assertThat(r.getMismatchCount()).isEqualTo(1);
    }

    @Test
    void whitespaceCaseAndNumberFormatDifferencesAreMismatches() {
        PairResult r = compare(
                List.of(ColumnSpec.plain("S"), ColumnSpec.plain("C"), ColumnSpec.plain("N")),
                row("S", "abc", "C", "Hello", "N", "1.10"), row("S", "abc ", "C", "hello", "N", "1.1"));
        assertThat(r.getRows()).extracting(FieldResult::getStatus).containsOnly(RowStatus.MISMATCH);
        assertThat(r.getMismatchCount()).isEqualTo(3);
    }

    @Test
    void allEqualIsMatchAndKeepsColumnOrder() {
        PairResult r = compare(List.of(ColumnSpec.plain("B_COL"), ColumnSpec.plain("A_COL")),
                row("A_COL", "1", "B_COL", "2"), row("A_COL", "1", "B_COL", "2"));
        assertThat(r.getStatus()).isEqualTo(PairStatus.MATCH);
        assertThat(r.getMismatchCount()).isZero();
        assertThat(r.getRows()).extracting(FieldResult::getName).containsExactly("B_COL", "A_COL");
        assertThat(r.getIndex()).isEqualTo(1);
        assertThat(r.getA()).isEqualTo("A1");
        assertThat(r.getB()).isEqualTo("B1");
    }

    @Test
    void missingRowsAreReportedWithoutComparingOrDecrypting() {
        List<ColumnSpec> cols = List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY));
        Map<String, String> present = row("APPR_MSG", FakeDecryptor.encrypt("{}"));
        assertThat(compare(cols, null, present).getMissing()).isEqualTo(MissingSide.A);
        assertThat(compare(cols, present, null).getMissing()).isEqualTo(MissingSide.B);
        PairResult both = compare(cols, null, null);
        assertThat(both.getMissing()).isEqualTo(MissingSide.BOTH);
        assertThat(both.getStatus()).isEqualTo(PairStatus.MISSING);
        assertThat(both.getRows()).isEmpty();
        assertThat(both.getMismatchCount()).isZero();
        assertThat(decryptor.calls()).isEmpty();
    }

    @Test
    void decryptedColumnIsFlattenedInAKeyOrderThenBOnlyKeys() {
        PairResult r = compare(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                row("APPR_MSG", FakeDecryptor.encrypt("{\"res_cd\":\"0000\",\"amount\":100,\"memo\":\"a\"}")),
                row("APPR_MSG", FakeDecryptor.encrypt("{\"extra\":true,\"amount\":100,\"res_cd\":\"9999\"}")));
        assertThat(r.getRows()).extracting(FieldResult::getName, FieldResult::getA, FieldResult::getB, FieldResult::getStatus)
                .containsExactly(
                        tuple("APPR_MSG.res_cd", "0000", "9999", RowStatus.MISMATCH),
                        tuple("APPR_MSG.amount", "100", "100", RowStatus.MATCH),
                        tuple("APPR_MSG.memo", "a", null, RowStatus.MISMATCH),
                        tuple("APPR_MSG.extra", null, "true", RowStatus.MISMATCH));
        assertThat(r.getRows()).extracting(FieldResult::getColumn).containsOnly("APPR_MSG");
        assertThat(r.getMismatchCount()).isEqualTo(3);
    }

    @Test
    void nestedValuesAreComparedAsCompactJsonAndNumberTextIsPreserved() {
        PairResult r = compare(List.of(ColumnSpec.decrypted("M", DecryptPolicy.PANEN)),
                row("M", FakeDecryptor.encrypt("{\"obj\":{\"x\": 1},\"arr\":[1, 2],\"n\":1.10}")),
                row("M", FakeDecryptor.encrypt("{\"obj\":{\"x\":1},\"arr\":[1,2],\"n\":1.1}")));
        assertThat(r.getRows()).extracting(FieldResult::getName, FieldResult::getA, FieldResult::getStatus)
                .containsExactly(
                        tuple("M.obj", "{\"x\":1}", RowStatus.MATCH),
                        tuple("M.arr", "[1,2]", RowStatus.MATCH),
                        tuple("M.n", "1.10", RowStatus.MISMATCH));
        assertThat(decryptor.calls()).allMatch(c -> c.startsWith("PANEN:"));
    }

    @Test
    void decryptFailuresBecomeSingleErrorRowAndOtherColumnsContinue() {
        List<ColumnSpec> cols = List.of(ColumnSpec.plain("ORDER_NO"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY),
                ColumnSpec.plain("AMOUNT"));
        PairResult broken = compare(cols, row("ORDER_NO", "1", "APPR_MSG", "BROKEN", "AMOUNT", "5"),
                row("ORDER_NO", "1", "APPR_MSG", FakeDecryptor.encrypt("{}"), "AMOUNT", "5"));
        assertThat(broken.getRows()).extracting(FieldResult::getName, FieldResult::getStatus, FieldResult::getCause,
                        FieldResult::getErrorSide)
                .containsExactly(
                        tuple("ORDER_NO", RowStatus.MATCH, null, null),
                        tuple("APPR_MSG", RowStatus.DECRYPT_ERROR, DecryptErrorCause.DECRYPT_FAILED, Side.A),
                        tuple("AMOUNT", RowStatus.MATCH, null, null));
        assertThat(broken.getRows().get(1).getA()).isNull();
        assertThat(broken.getRows().get(1).getB()).isNull();
        assertThat(broken.getStatus()).isEqualTo(PairStatus.MISMATCH);
        assertThat(broken.getMismatchCount()).isEqualTo(1);
    }

    @Test
    void invalidJsonAndNonObjectJsonAreDecryptErrorsWithSide() {
        List<ColumnSpec> cols = List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY));
        FieldResult invalid = compare(cols, row("APPR_MSG", FakeDecryptor.encrypt("{}")),
                row("APPR_MSG", FakeDecryptor.encrypt("{\"a\":"))).getRows().get(0);
        assertThat(invalid.getCause()).isEqualTo(DecryptErrorCause.JSON_INVALID);
        assertThat(invalid.getErrorSide()).isEqualTo(Side.B);
        FieldResult notObject = compare(cols, row("APPR_MSG", FakeDecryptor.encrypt("[1,2]")),
                row("APPR_MSG", FakeDecryptor.encrypt("{}"))).getRows().get(0);
        assertThat(notObject.getCause()).isEqualTo(DecryptErrorCause.JSON_NOT_OBJECT);
        assertThat(notObject.getErrorSide()).isEqualTo(Side.A);
        assertThat(notObject.isMismatchOrError()).isTrue();
    }

    @Test
    void bothNullDecryptedColumnIsSingleMatchRowWithoutDecrypting() {
        PairResult r = compare(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                row("APPR_MSG", null), row("APPR_MSG", null));
        assertThat(r.getRows()).extracting(FieldResult::getName, FieldResult::getStatus)
                .containsExactly(tuple("APPR_MSG", RowStatus.MATCH));
        assertThat(r.getStatus()).isEqualTo(PairStatus.MATCH);
        assertThat(decryptor.calls()).isEmpty();
    }

    @Test
    void oneSideNullDecryptedColumnComparesFieldsAgainstNull() {
        PairResult r = compare(List.of(ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                row("APPR_MSG", FakeDecryptor.encrypt("{\"a\":\"1\"}")), row("APPR_MSG", null));
        assertThat(r.getRows()).extracting(FieldResult::getName, FieldResult::getA, FieldResult::getB, FieldResult::getStatus)
                .containsExactly(tuple("APPR_MSG.a", "1", null, RowStatus.MISMATCH));
        assertThat(decryptor.calls()).hasSize(1);
    }

    @Test
    void loginFailureMakesDecryptedColumnsErrorsButPairStillCompletes() {
        session = new FakeDecryptor().failLogin().openSession();
        PairResult r = compare(List.of(ColumnSpec.plain("ORDER_NO"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)),
                row("ORDER_NO", "1", "APPR_MSG", FakeDecryptor.encrypt("{}")),
                row("ORDER_NO", "1", "APPR_MSG", FakeDecryptor.encrypt("{}")));
        assertThat(r.getRows()).extracting(FieldResult::getStatus).containsExactly(RowStatus.MATCH, RowStatus.DECRYPT_ERROR);
        assertThat(r.getRows().get(1).getCause()).isEqualTo(DecryptErrorCause.DECRYPT_FAILED);
    }
}
