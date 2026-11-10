package kr.kicc.datamatch.safedb;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * SafeDB 복호화기(BE-04, 기술 설계서 TD-10, 5장 FR-07).
 *
 * <ul>
 *   <li>사용자 {@code SAFEDB}, 정책 테이블 {@code SAFEDB.POLICY}, 정책 컬럼 {@code KDKEY}/{@code PANEN}을 쓴다(pgweb-boot와 동일).</li>
 *   <li>복호화 결과는 KDKEY는 EUC-KR, PANEN은 UTF-8로 디코딩한다(TA-07).</li>
 *   <li>로그인은 세션(비교 작업)마다 첫 복호화 때 한 번만 시도하고, 실패하면 그 세션의 나머지 복호화를 즉시 실패시킨다.
 *       서버에 연결되지 않으면 로그인 한 번이 약 110초 걸리기 때문이다.</li>
 *   <li>라이브러리의 스레드 안전성이 확인되지 않아 호출을 한 번에 하나씩 직렬화한다(TA-11).</li>
 *   <li>예외 메시지에는 암호문과 평문을 넣지 않는다(TD-20).</li>
 * </ul>
 */
@Component
public class SafeDbDecryptor implements Decryptor {

    static final String SAFEDB_USER = "SAFEDB";
    static final String POLICY_TABLE = "SAFEDB.POLICY";
    private static final Charset KDKEY_CHARSET = Charset.forName("EUC-KR");

    private final SafeDbClient client;
    private final Object lock = new Object();

    public SafeDbDecryptor(SafeDbClient client) {
        this.client = client;
    }

    @Override
    public DecryptSession openSession() {
        return new Session();
    }

    static Charset charsetOf(DecryptPolicy policy) {
        return policy == DecryptPolicy.KDKEY ? KDKEY_CHARSET : StandardCharsets.UTF_8;
    }

    private final class Session implements DecryptSession {

        private boolean loggedIn;
        private boolean loginFailed;

        @Override
        public String decrypt(String cipherText, DecryptPolicy policy) throws DecryptException {
            synchronized (lock) {
                ensureLoggedIn();
                byte[] plain;
                try {
                    plain = client.decrypt(SAFEDB_USER, POLICY_TABLE, policy.name(), cipherText.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new DecryptException("SafeDB decrypt failed", false);
                }
                if (plain == null) {
                    throw new DecryptException("SafeDB decrypt returned no data", false);
                }
                return new String(plain, charsetOf(policy));
            }
        }

        private void ensureLoggedIn() throws DecryptException {
            if (loginFailed) {
                throw new DecryptException("SafeDB login failed earlier in this job", true);
            }
            if (loggedIn) {
                return;
            }
            boolean ok;
            try {
                ok = client.login();
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                loginFailed = true;
                throw new DecryptException("SafeDB login failed", true);
            }
            loggedIn = true;
        }
    }
}
