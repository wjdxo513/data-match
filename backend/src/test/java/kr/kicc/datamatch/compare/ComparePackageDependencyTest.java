package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * BE-03: 비교 엔진이 DB·웹 계층 없이 테스트 가능하도록, 순수 비교 로직 파일이 JDBC와 Spring Web을 import하지 않는지 확인한다.
 * (DB-03의 RowRepository·SqlBuilder 등 DB 접근 파일은 이 검사 대상이 아니다.)
 */
class ComparePackageDependencyTest {

    private static final List<String> PURE_FILES = List.of(
            "CompareEngine.java", "JsonFlattener.java", "PairsParser.java", "PairsParseResult.java", "ColumnSpec.java",
            "FieldResult.java", "PairResult.java", "TransactionPair.java", "PairLineError.java");

    @Test
    void pureCompareFilesDoNotImportJdbcOrSpringWeb() throws IOException {
        Path dir = Path.of("src/main/java/kr/kicc/datamatch/compare");
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> PURE_FILES.contains(p.getFileName().toString())).collect(Collectors.toList());
        }
        assertThat(files).hasSize(PURE_FILES.size());
        for (Path f : files) {
            String src = Files.readString(f, StandardCharsets.UTF_8);
            assertThat(src).as(f.getFileName().toString())
                    .doesNotContain("import java.sql")
                    .doesNotContain("import javax.sql")
                    .doesNotContain("import org.springframework.web")
                    .doesNotContain("import org.springframework.jdbc");
        }
    }
}
