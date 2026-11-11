package kr.kicc.datamatch.web;

import java.util.List;
import java.util.stream.Collectors;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.TableMetadata;
import kr.kicc.datamatch.metadata.TableSearchResult;
import lombok.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 테이블 검색과 컬럼·PK 조회(기술 설계서 8장). 메타데이터는 화면의 A쪽 환경으로 조회한다(PA-15). */
@RestController
public class MetadataController {

    private final MetadataService service;

    public MetadataController(MetadataService service) {
        this.service = service;
    }

    @GetMapping("/api/tables")
    public TableSearchResult searchTables(@RequestParam String env, @RequestParam String q) {
        return service.searchTables(env, q);
    }

    @GetMapping("/api/tables/columns")
    public ColumnsResponse columns(@RequestParam String env, @RequestParam String owner, @RequestParam String table) {
        TableMetadata meta = service.describeTable(env, owner, table);
        List<ColumnResponse> columns = meta.getColumns().stream()
                .map(c -> new ColumnResponse(c.getName(), c.displayType(), meta.isPk(c.getName()), c.isClob(), c.isKeyable()))
                .collect(Collectors.toList());
        return new ColumnsResponse(meta.defaultKeyColumn(), columns);
    }

    @Value
    public static class ColumnsResponse {
        /** 기본 비교 키(단일 PK이고 비교 키 가능 타입일 때). 없으면 null(PRD PA-10). */
        String keyColumn;
        List<ColumnResponse> columns;
    }

    @Value
    public static class ColumnResponse {
        String name;
        String type;
        boolean pk;
        boolean clob;
        /** 비교 키로 고를 수 있는지(문자형·NUMBER, PRD PA-19). */
        boolean keyable;
    }
}
