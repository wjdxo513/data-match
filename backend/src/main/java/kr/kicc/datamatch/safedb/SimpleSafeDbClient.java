package kr.kicc.datamatch.safedb;

import com.initech.safedb.SimpleSafeDB;
import org.springframework.stereotype.Component;

/**
 * 실제 SafeDB 라이브러리({@code SimpleSafeDB}) 호출부(BE-04, 기술 설계서 TD-10).
 * 처음 사용할 때 라이브러리를 초기화하므로 SafeDB 설정이 없어도 애플리케이션은 기동된다.
 * 서버 연결이 필요해 자동 테스트 대상이 아니며(실행 계획 D-01), 사내망 PC의 INT-04(M-03, M-10)에서 검증한다.
 */
@Component
public class SimpleSafeDbClient implements SafeDbClient {

    private SimpleSafeDB instance;

    private synchronized SimpleSafeDB instance() throws Exception {
        if (instance == null) {
            instance = SimpleSafeDB.getInstance();
        }
        return instance;
    }

    @Override
    public boolean login() throws Exception {
        return instance().login();
    }

    @Override
    public byte[] decrypt(String user, String policyTable, String policyColumn, byte[] cipher) throws Exception {
        return instance().decrypt(user, policyTable, policyColumn, cipher);
    }
}
