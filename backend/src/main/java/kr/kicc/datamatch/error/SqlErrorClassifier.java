package kr.kicc.datamatch.error;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;

/**
 * DB 오류 분류(기술 설계서 6.5절). 드라이버 메시지는 접속 정보를 담을 수 있어 분류에는 예외 타입, SQLState, 오류 번호만 쓴다.
 */
public final class SqlErrorClassifier {

    /** 사용자 요청 취소 또는 쿼리 타임아웃으로 Oracle이 반환하는 ORA-01013. */
    public static final int ORA_USER_CANCEL = 1013;

    // 연결 계열 Oracle 오류 번호: IO 오류, 닫힌 연결, 통신 단절, 리스너·서비스, 인스턴스 미기동, 로그인 거부·잠김.
    private static final Set<Integer> CONNECTION_CODES = Set.of(
            17002, 17008, 17410, 3113, 3114, 3135, 12170, 12505, 12514, 12516, 12519, 12520, 12528, 12541,
            1033, 1034, 1089, 1017, 28000);

    public enum Kind {
        CONNECTION,
        TIMEOUT,
        CANCELLED,
        OTHER
    }

    private SqlErrorClassifier() {
    }

    /**
     * @param cancelRequested 사용자가 중지를 요청했는지. ORA-01013은 중지 요청이 있으면 취소, 없으면 타임아웃이다.
     */
    public static Kind classify(SQLException e, boolean cancelRequested) {
        if (e instanceof SQLTimeoutException || e.getErrorCode() == ORA_USER_CANCEL) {
            return cancelRequested ? Kind.CANCELLED : Kind.TIMEOUT;
        }
        String state = e.getSQLState();
        if (e instanceof SQLRecoverableException || e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException
                || (state != null && state.startsWith("08")) || CONNECTION_CODES.contains(e.getErrorCode())) {
            return Kind.CONNECTION;
        }
        return Kind.OTHER;
    }

    /** 취소가 아닌 분류를 업무 예외로 바꾼다. 메시지에는 드라이버 원문을 넣지 않는다. */
    public static DataMatchException toException(Kind kind, String env, Object details) {
        switch (kind) {
            case CONNECTION:
                return new DataMatchException(ErrorCode.DB_CONNECTION_FAILED, "DB connection failed (env=" + env + ")", details);
            case TIMEOUT:
                return new DataMatchException(ErrorCode.QUERY_TIMEOUT, "query timed out (env=" + env + ")", details);
            case OTHER:
                return new DataMatchException(ErrorCode.DB_QUERY_FAILED, "query failed (env=" + env + ")", details);
            default:
                throw new IllegalArgumentException("cancellation is not an error: " + kind);
        }
    }
}
