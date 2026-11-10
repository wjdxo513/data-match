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
                .map(c -> new ColumnResponse(c.getName(), c.displayType(), c.getName().equals(meta.getPkColumn()), c.isClob()))
                .collect(Collectors.toList());
        return new ColumnsResponse(meta.getPkColumn(), columns);
    }

    @Value
    public static class ColumnsResponse {
        String pkColumn;
        List<ColumnResponse> columns;
    }

    @Value
    public static class ColumnResponse {
        String name;
        String type;
        boolean pk;
        boolean clob;
    }
}
