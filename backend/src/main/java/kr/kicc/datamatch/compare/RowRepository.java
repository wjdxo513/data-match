package kr.kicc.datamatch.compare;

import java.util.List;
import java.util.Map;

/** 거래 묶음 조회(기술 설계서 6.3절). 단위 테스트에서 대체할 수 있도록 인터페이스로 둔다. */
public interface RowRepository {

    /**
     * 한 청크의 거래번호를 비교 키로 한 번의 쿼리로 조회한다.
     *
     * @return 조회된 거래번호(입력 문자열 그대로) → 행 목록(컬럼명 → 비교 문자열). 비교 키 값이 유일하지 않을 수 있어
     *     같은 키의 행을 모두 담는다(PRD PA-10). 없는 키는 결과에 없다(누락).
     * @throws QueryCancelledException 중지 요청으로 취소된 경우
     * @throws kr.kicc.datamatch.error.DataMatchException {@code DB_CONNECTION_FAILED}, {@code QUERY_TIMEOUT},
     *     {@code DB_QUERY_FAILED}(details.env)
     */
    Map<String, List<Map<String, String>>> fetchRows(String env, RowQuery query, List<String> keys, CancellationToken token);
}
