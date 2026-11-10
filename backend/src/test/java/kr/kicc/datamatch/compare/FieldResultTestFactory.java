package kr.kicc.datamatch.compare;

/** 다른 패키지 테스트에서 비교 결과 행을 만들기 위한 도우미(FieldResult의 팩토리는 패키지 전용). */
public final class FieldResultTestFactory {

    private FieldResultTestFactory() {
    }

    public static FieldResult compared(String column, String field, String a, String b) {
        return FieldResult.compared(column, field, a, b);
    }

    public static FieldResult decryptError(String column, DecryptErrorCause cause, Side side) {
        return FieldResult.decryptError(column, cause, side);
    }
}
