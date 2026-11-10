package kr.kicc.datamatch.compare;

import java.util.List;
import kr.kicc.datamatch.metadata.TableMetadata;
import lombok.Value;

/**
 * 검증을 통과한 비교 요청의 불변 사본(기술 설계서 5장 FR-08, SA-09). 요청 객체가 나중에 바뀌어도 영향받지 않는다.
 * 테이블·컬럼은 A쪽 환경의 메타데이터에서 확인한 값이다(PA-15).
 */
@Value
public class CompareTask {
    String envA;
    String envB;
    TableMetadata table;
    List<ColumnSpec> columns;
    List<TransactionPair> pairs;
    /** 비교 일시(ISO-8601, 초 단위). */
    String comparedAt;

    public CompareTask(String envA, String envB, TableMetadata table, List<ColumnSpec> columns,
                       List<TransactionPair> pairs, String comparedAt) {
        this.envA = envA;
        this.envB = envB;
        this.table = table;
        this.columns = List.copyOf(columns);
        this.pairs = List.copyOf(pairs);
        this.comparedAt = comparedAt;
    }
}
