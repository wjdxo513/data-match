package kr.kicc.datamatch.it;

import java.sql.Connection;

/**
 * 로컬 Oracle 테스트 스키마와 합성 데이터를 DM_IT_OWNER 로 적재한다(SETUP-05).
 * Gradle {@code oracleTestSetup} 작업이 실행한다. 사용자 생성(00_create_users.sql)은 관리자가 따로 실행한다.
 */
public final class OracleTestSchemaLoader {

    private OracleTestSchemaLoader() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection owner = OracleTestDatabase.owner()) {
            owner.setAutoCommit(true);
            OracleTestDatabase.runScript(owner, "/oracle/10_schema.sql");
            OracleTestDatabase.runScript(owner, "/oracle/20_seed.sql");
        }
        System.out.println("[oracleTestSetup] DM_IT_ 테스트 스키마와 합성 데이터를 적재했습니다.");
    }
}
