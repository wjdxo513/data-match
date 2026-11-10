package kr.kicc.datamatch.safedb;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 복호화기(BE-03, 로컬 Oracle 테스트 데이터와 같은 형식).
 * 암호문이 {@code FAKEENC:}로 시작하면 그 뒤를 평문으로 돌려주고, 아니면 복호화 실패로 처리한다.
 * {@link #failLogin()}을 켜면 세션의 첫 호출에서 로그인 실패를 내고 이후 호출도 즉시 실패시킨다(TD-10).
 */
public class FakeDecryptor implements Decryptor {

    public static final String PREFIX = "FAKEENC:";

    private final List<String> calls = new ArrayList<>();
    private boolean loginFails;
    private int loginAttempts;

    public static String encrypt(String plain) {
        return PREFIX + plain;
    }

    public FakeDecryptor failLogin() {
        this.loginFails = true;
        return this;
    }

    public List<String> calls() {
        return calls;
    }

    public int loginAttempts() {
        return loginAttempts;
    }

    @Override
    public DecryptSession openSession() {
        return new DecryptSession() {
            private boolean loggedIn;
            private boolean loginFailed;

            @Override
            public String decrypt(String cipherText, DecryptPolicy policy) throws DecryptException {
                calls.add(policy + ":" + cipherText);
                if (loginFailed) {
                    throw new DecryptException("SafeDB login failed earlier in this job", true);
                }
                if (!loggedIn) {
                    loginAttempts++;
                    if (loginFails) {
                        loginFailed = true;
                        throw new DecryptException("SafeDB login failed", true);
                    }
                    loggedIn = true;
                }
                if (!cipherText.startsWith(PREFIX)) {
                    throw new DecryptException("decrypt failed", false);
                }
                return cipherText.substring(PREFIX.length());
            }
        };
    }
}
