package kr.kicc.datamatch.safedb;

/**
 * 복호화 실패. 메시지에는 암호문과 평문을 넣지 않는다(TD-20).
 * {@code loginFailure}가 true면 SafeDB 로그인 실패로, 같은 작업의 나머지 복호화도 실패한다(TD-10).
 */
public class DecryptException extends Exception {

    private final boolean loginFailure;

    public DecryptException(String message, boolean loginFailure) {
        this(message, loginFailure, null);
    }

    public DecryptException(String message, boolean loginFailure, Throwable cause) {
        super(message, cause);
        this.loginFailure = loginFailure;
    }

    public boolean isLoginFailure() {
        return loginFailure;
    }
}
