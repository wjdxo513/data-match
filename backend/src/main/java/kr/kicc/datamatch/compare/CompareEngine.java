package kr.kicc.datamatch.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.kicc.datamatch.safedb.DecryptException;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import kr.kicc.datamatch.safedb.DecryptSession;

/**
 * 거래번호 짝 하나를 비교한다(실행 계획 BE-03, PRD 6.1·6.2, 기술 설계서 5장 FR-07).
 * DB와 SafeDB에 의존하지 않으며, 행 값은 이미 문자열로 변환된 상태(DB-03의 ValueConverter)로 받는다.
 */
public final class CompareEngine {

    private CompareEngine() {
    }

    /**
     * @param index   입력 순서(1부터)
     * @param pair    거래번호 짝
     * @param columns 선택 순서대로의 비교 컬럼
     * @param rowA    A쪽 행(컬럼명 → 문자열 값). 행이 없으면 null
     * @param rowB    B쪽 행. 행이 없으면 null
     * @param session 작업의 복호화 세션
     */
    public static PairResult compare(int index, TransactionPair pair, List<ColumnSpec> columns,
                                     Map<String, String> rowA, Map<String, String> rowB, DecryptSession session) {
        if (rowA == null || rowB == null) {
            MissingSide missing = rowA == null && rowB == null ? MissingSide.BOTH : rowA == null ? MissingSide.A : MissingSide.B;
            return new PairResult(index, pair.getA(), pair.getB(), PairStatus.MISSING, missing, List.of(), 0);
        }
        List<FieldResult> rows = new ArrayList<>();
        for (ColumnSpec column : columns) {
            String a = rowA.get(column.getName());
            String b = rowB.get(column.getName());
            if (column.isDecrypted()) {
                rows.addAll(compareDecrypted(column.getName(), column.getDecrypt(), a, b, session));
            } else {
                rows.add(FieldResult.compared(column.getName(), null, a, b));
            }
        }
        int mismatches = (int) rows.stream().filter(FieldResult::isMismatchOrError).count();
        PairStatus status = mismatches > 0 ? PairStatus.MISMATCH : PairStatus.MATCH;
        return new PairResult(index, pair.getA(), pair.getB(), status, null, List.copyOf(rows), mismatches);
    }

    private static List<FieldResult> compareDecrypted(String column, DecryptPolicy policy, String cipherA, String cipherB,
                                                      DecryptSession session) {
        if (cipherA == null && cipherB == null) {
            return List.of(FieldResult.compared(column, null, null, null));
        }
        Map<String, String> fieldsA;
        Map<String, String> fieldsB;
        try {
            fieldsA = decryptToFields(cipherA, policy, session, Side.A);
            fieldsB = decryptToFields(cipherB, policy, session, Side.B);
        } catch (SideFailure f) {
            return List.of(FieldResult.decryptError(column, f.cause, f.side));
        }
        Set<String> keys = new LinkedHashSet<>(fieldsA.keySet());
        keys.addAll(fieldsB.keySet());
        List<FieldResult> rows = new ArrayList<>(keys.size());
        for (String key : keys) {
            rows.add(FieldResult.compared(column, key, fieldsA.get(key), fieldsB.get(key)));
        }
        return rows;
    }

    /** 값이 NULL이면 복호화하지 않고 빈 필드 목록으로 본다(없는 쪽 필드는 NULL로 비교, PA-04). */
    private static Map<String, String> decryptToFields(String cipher, DecryptPolicy policy, DecryptSession session, Side side)
            throws SideFailure {
        if (cipher == null) {
            return Collections.emptyMap();
        }
        String plain;
        try {
            plain = session.decrypt(cipher, policy);
        } catch (DecryptException e) {
            throw new SideFailure(DecryptErrorCause.DECRYPT_FAILED, side);
        }
        try {
            return JsonFlattener.flatten(plain);
        } catch (JsonFlattener.JsonFlattenException e) {
            throw new SideFailure(e.errorCause(), side);
        }
    }

    private static final class SideFailure extends Exception {
        private final DecryptErrorCause cause;
        private final Side side;

        SideFailure(DecryptErrorCause cause, Side side) {
            super(null, null, false, false);
            this.cause = cause;
            this.side = side;
        }
    }
}
