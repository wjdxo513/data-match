package kr.kicc.datamatch.safedb;

/**
 * SafeDB 라이브러리 호출을 감싸는 얇은 인터페이스(BE-04). 단위 테스트에서 대체한다.
 * 읽기 전용 프로그램이므로 암호화 API는 두지 않는다.
 */
public interface SafeDbClient {

    /** SafeDB 서버에 로그인한다. 성공하면 true. */
    boolean login() throws Exception;

    /** 정책(사용자·정책 테이블·정책 컬럼)에 따라 복호화한다. */
    byte[] decrypt(String user, String policyTable, String policyColumn, byte[] cipher) throws Exception;
}
