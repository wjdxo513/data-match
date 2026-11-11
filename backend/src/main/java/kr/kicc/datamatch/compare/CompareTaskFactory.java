package kr.kicc.datamatch.compare;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.TableMetadata;
import org.springframework.stereotype.Component;

/**
 * 비교 요청 검증과 작업 입력 생성(기술 설계서 5장 FR-04·05, 8장, 9.5절). 실패하면 작업을 만들지 않는다.
 *
 * <ol>
 *   <li>A·B 환경 설정 확인({@code ENV_NOT_CONFIGURED})</li>
 *   <li>A쪽 환경 메타데이터로 테이블과 비교 키 확인(없는 테이블, 없거나 문자형·NUMBER가 아닌 비교 키는 {@code INVALID_INPUT})</li>
 *   <li>컬럼 존재·중복, 복호화 대상이 CLOB인지 확인({@code INVALID_INPUT})</li>
 *   <li>거래번호 입력 검증(오류 줄 목록, 1,000쌍 초과, 빈 입력은 {@code INVALID_INPUT})</li>
 * </ol>
 */
@Component
public class CompareTaskFactory {

    private static final DateTimeFormatter COMPARED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DataSourceRegistry registry;
    private final MetadataService metadata;

    public CompareTaskFactory(DataSourceRegistry registry, MetadataService metadata) {
        this.registry = registry;
        this.metadata = metadata;
    }

    public CompareTask create(String envA, String envB, String owner, String table, String keyColumn,
                              List<ColumnSpec> columns, String pairsText) {
        for (String env : new String[] {envA, envB}) {
            if (!registry.isAvailable(env)) {
                throw new DataMatchException(ErrorCode.ENV_NOT_CONFIGURED, "environment not configured");
            }
        }
        if (columns == null || columns.isEmpty()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "no columns selected");
        }
        TableMetadata meta = metadata.describeTable(envA, owner, table);
        ColumnMeta key = meta.requireKeyColumn(keyColumn);
        List<String> names = columns.stream().map(ColumnSpec::getName).collect(Collectors.toList());
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n)) {
                throw new DataMatchException(ErrorCode.INVALID_INPUT, "duplicate column", Map.of("column", String.valueOf(n)));
            }
        }
        List<ColumnMeta> resolved = meta.requireColumns(names);
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isDecrypted() && !resolved.get(i).isClob()) {
                throw new DataMatchException(ErrorCode.INVALID_INPUT, "decrypt target must be a CLOB column",
                        Map.of("column", resolved.get(i).getName()));
            }
        }

        PairsParseResult parsed = PairsParser.parse(pairsText, key.isNumber());
        if (!parsed.getErrors().isEmpty() || parsed.isLimitExceeded()) {
            List<Map<String, Object>> details = new ArrayList<>();
            parsed.getErrors().forEach(e -> details.add(Map.of("line", e.getLine(), "reason", e.getReason().name())));
            if (parsed.isLimitExceeded()) {
                details.add(Map.of("reason", "PAIR_LIMIT_EXCEEDED", "count", parsed.pairCount()));
            }
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "invalid pairs input", details);
        }
        if (parsed.getPairs().isEmpty()) {
            throw new DataMatchException(ErrorCode.INVALID_INPUT, "no pairs");
        }
        return new CompareTask(envA, envB, meta, key.getName(), columns, parsed.getPairs(), LocalDateTime.now().format(COMPARED_AT));
    }
}
