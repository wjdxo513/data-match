package kr.kicc.datamatch.compare;

import kr.kicc.datamatch.config.DataSourceRegistry;

/** INT-01: 쿼리 타임아웃을 짧게 설정한 {@link OracleRowRepository}(테스트 전용, 운영 값은 30초 고정). */
public final class TestRowRepositories {

    private TestRowRepositories() {
    }

    public static RowRepository withTimeout(DataSourceRegistry registry, int chunkSize, int timeoutSeconds) {
        return new OracleRowRepository(registry, chunkSize, timeoutSeconds);
    }
}
