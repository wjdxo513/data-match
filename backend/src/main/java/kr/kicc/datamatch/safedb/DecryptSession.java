package kr.kicc.datamatch.safedb;

/**
 * 한 비교 작업 동안 쓰는 복호화 세션(기술 설계서 TD-10).
 * 구현은 로그인을 작업마다 첫 복호화 때 한 번만 시도하고, 실패하면 이후 호출을 즉시 실패시켜야 한다.
 */
public interface DecryptSession {

    /**
     * 암호문을 복호화해 평문을 돌려준다.
     *
     * @param cipherText null이 아닌 암호문(CLOB 문자열)
     * @param policy     복호화 정책
     * @throws DecryptException 복호화 또는 로그인 실패
     */
    String decrypt(String cipherText, DecryptPolicy policy) throws DecryptException;
}
