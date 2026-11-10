package kr.kicc.datamatch.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kr.kicc.datamatch.compare.ColumnSpec;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.DecryptErrorCause;
import kr.kicc.datamatch.compare.FieldResult;
import kr.kicc.datamatch.compare.FieldResultTestFactory;
import kr.kicc.datamatch.compare.MissingSide;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.PairStatus;
import kr.kicc.datamatch.compare.Side;
import kr.kicc.datamatch.compare.TransactionPair;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** BE-07: 생성한 엑셀을 POI로 다시 읽어 시트 구성, 서식, 누락 표기, 셀 한도, 수식 차단, 파일명을 확인한다. */
class ExcelExporterTest {

    private final ExcelExporter exporter = new ExcelExporter();

    private static final TableMetadata TABLE = new TableMetadata("DM_IT_OWNER", "DM_IT_TRX_STR", "TRX_NO", "VARCHAR2",
            List.of(new ColumnMeta("TRX_NO", "VARCHAR2", 20, null, null, 20)));

    private static CompareTask task(int pairs) {
        List<TransactionPair> list = new ArrayList<>();
        for (int i = 1; i <= pairs; i++) {
            list.add(new TransactionPair(i, "A" + i, "B" + i));
        }
        return new CompareTask("test", "prod", TABLE,
                List.of(ColumnSpec.plain("AMOUNT"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)), list, "2026-11-10T15:15:30");
    }

    private static final String LONG = "x".repeat(40_000);

    private static List<PairResult> results() {
        return List.of(
                new PairResult(1, "A1", "B1", PairStatus.MATCH, null, List.of(FieldResultTestFactory.compared("AMOUNT", null, "100", "100")), 0),
                new PairResult(2, "A2", "B2", PairStatus.MISMATCH, null, List.of(
                        FieldResultTestFactory.compared("AMOUNT", null, "=1+1", "200"),
                        FieldResultTestFactory.compared("APPR_MSG", "memo", LONG, null),
                        FieldResultTestFactory.decryptError("APPR_MSG", DecryptErrorCause.JSON_INVALID, Side.B)), 3),
                new PairResult(3, "A3", "B3", PairStatus.MISSING, MissingSide.B, List.of(), 0),
                new PairResult(4, "A4", "B4", PairStatus.MISSING, MissingSide.A, List.of(), 0),
                new PairResult(5, "A5", "B5", PairStatus.MISSING, MissingSide.BOTH, List.of(), 0));
    }

    private static XSSFWorkbook read(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String text(XSSFSheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getStringCellValue();
    }

    @Test
    void summarySheetHasConditionCountsAndItems() throws Exception {
        try (XSSFWorkbook wb = read(exporter.export(task(5), results()))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetName(0)).isEqualTo("요약");
            assertThat(wb.getSheetName(1)).isEqualTo("상세");
            XSSFSheet s = wb.getSheet("요약");
            String[][] expected = {
                {"A쪽 환경", "테스트(test)"}, {"B쪽 환경", "운영(prod)"}, {"테이블", "DM_IT_OWNER.DM_IT_TRX_STR"},
                {"비교 컬럼", "AMOUNT, APPR_MSG(복호화 KDKEY)"}, {"비교 일시", "2026-11-10 15:15:30"},
                {"총 쌍", "5"}, {"일치", "1"}, {"불일치", "1"}, {"누락", "3"}};
            for (int i = 0; i < expected.length; i++) {
                assertThat(text(s, i, 0)).isEqualTo(expected[i][0]);
                assertThat(text(s, i, 1)).isEqualTo(expected[i][1]);
            }
            assertThat(text(s, 10, 0)).isEqualTo("순번");
            assertThat(text(s, 10, 5)).isEqualTo("비고");
            assertThat(text(s, 12, 3)).isEqualTo("불일치");
            assertThat(text(s, 12, 4)).isEqualTo("3");
            assertThat(text(s, 12, 5)).isEqualTo("복호화 오류 포함");
            assertThat(text(s, 13, 3)).isEqualTo("누락(B 없음)");
            assertThat(text(s, 15, 5)).isEqualTo("A·B 모두 없음");
        }
    }

    @Test
    void detailSheetRowsHeadersStylesAndMissingLabels() throws Exception {
        try (XSSFWorkbook wb = read(exporter.export(task(5), results()))) {
            XSSFSheet d = wb.getSheet("상세");
            assertThat(text(d, 0, 4)).isEqualTo("A값(테스트)");
            assertThat(text(d, 0, 5)).isEqualTo("B값(운영)");
            // 결과 행 수: 일치 1 + 불일치 3 + 누락 3 = 7 (머리글 제외)
            assertThat(d.getLastRowNum()).isEqualTo(7);
            assertThat(text(d, 1, 6)).isEqualTo("일치");
            assertThat(text(d, 2, 6)).isEqualTo("불일치");
            assertThat(text(d, 3, 5)).isEqualTo("(NULL)");
            assertThat(text(d, 4, 6)).isEqualTo("복호화 오류(JSON 해석 실패, B쪽)");
            assertThat(text(d, 5, 6)).isEqualTo("누락(B 없음)");
            assertThat(text(d, 6, 6)).isEqualTo("누락(A 없음)");
            assertThat(text(d, 7, 6)).isEqualTo("누락(A·B 모두 없음)");

            XSSFCellStyle match = d.getRow(1).getCell(6).getCellStyle();
            XSSFCellStyle mismatch = d.getRow(2).getCell(6).getCellStyle();
            XSSFCellStyle error = d.getRow(4).getCell(6).getCellStyle();
            XSSFCellStyle missing = d.getRow(5).getCell(6).getCellStyle();
            assertThat(match.getIndex()).as("일치 행은 기본 서식").isZero();
            assertThat(match.getFillPattern()).isEqualTo(FillPatternType.NO_FILL);
            assertThat(mismatch.getFillForegroundXSSFColor().getRGB()).isEqualTo(ExcelExporter.MISMATCH_FILL);
            assertThat(mismatch.getFont().getXSSFColor().getRGB()).isEqualTo(ExcelExporter.MISMATCH_FONT);
            assertThat(error.getFillForegroundXSSFColor().getRGB()).isEqualTo(ExcelExporter.MISMATCH_FILL);
            assertThat(missing.getFillForegroundXSSFColor().getRGB()).isEqualTo(ExcelExporter.MISSING_FILL);
            assertThat(missing.getFont().getXSSFColor().getRGB()).isEqualTo(ExcelExporter.MISSING_FONT);
            assertThat(Arrays.equals(ExcelExporter.MISMATCH_FILL, ExcelExporter.MISSING_FILL)).isFalse();
        }
    }

    @Test
    void longValuesAreTruncatedAndFormulasStayStrings() throws Exception {
        try (XSSFWorkbook wb = read(exporter.export(task(5), results()))) {
            XSSFSheet d = wb.getSheet("상세");
            String truncated = text(d, 3, 4);
            assertThat(truncated).hasSize(ExcelExporter.CELL_LIMIT).endsWith("…(이하 생략)");
            XSSFCell formula = d.getRow(2).getCell(4);
            assertThat(formula.getCellType()).isEqualTo(CellType.STRING);
            assertThat(formula.getStringCellValue()).isEqualTo("=1+1");
            for (Row row : d) {
                row.forEach(c -> assertThat(c.getCellType()).isEqualTo(CellType.STRING));
            }
        }
        assertThat(ExcelExporter.truncate("abc")).isEqualTo("abc");
        assertThat(ExcelExporter.truncate(null)).isNull();
    }

    @Test
    void fileNameFollowsRule() {
        assertThat(exporter.fileName(task(1))).isEqualTo("datamatch_test-prod_DM_IT_TRX_STR_20261110-151530.xlsx");
        TableMetadata odd = new TableMetadata("O", "거래/테이블 1", "TRX_NO", "VARCHAR2", TABLE.getColumns());
        CompareTask t = new CompareTask("dev", "prod", odd, List.of(ColumnSpec.plain("TRX_NO")),
                List.of(new TransactionPair(1, "A", "B")), "2026-01-02T03:04:05");
        assertThat(exporter.fileName(t)).isEqualTo("datamatch_dev-prod_거래_테이블_1_20260102-030405.xlsx");
    }

    @Test
    void thirtyThousandRowsAreGeneratedWithoutTempFiles() throws Exception {
        List<PairResult> big = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            List<FieldResult> rows = new ArrayList<>();
            for (int c = 0; c < 30; c++) {
                rows.add(FieldResultTestFactory.compared("APPR_MSG", "f" + c, "value-" + i + "-" + c, c == 0 ? "other" : "value-" + i + "-" + c));
            }
            big.add(new PairResult(i, "A" + i, "B" + i, PairStatus.MISMATCH, null, rows, 1));
        }
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long before = poiTempCount(tmp);
        long start = System.nanoTime();
        byte[] bytes = exporter.export(task(1000), big);
        System.out.println("[BE-07] 30,000 detail rows: " + bytes.length + " bytes in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        try (XSSFWorkbook wb = read(bytes)) {
            assertThat(wb.getSheet("상세").getLastRowNum()).isEqualTo(30_000);
        }
        assertThat(poiTempCount(tmp)).isEqualTo(before);
    }

    @Test
    void sourceDoesNotUseStreamingWorkbookOrTempFiles() throws Exception {
        String src = Files.readString(Path.of("src/main/java/kr/kicc/datamatch/excel/ExcelExporter.java"), StandardCharsets.UTF_8);
        assertThat(src).doesNotContain("SXSSF").doesNotContain("TempFile").doesNotContain("java.io.File")
                .doesNotContain("FileOutputStream").doesNotContain("createTempFile");
    }

    private static long poiTempCount(Path tmp) {
        File[] files = tmp.toFile().listFiles((dir, name) -> name.startsWith("poifiles") || name.startsWith("poi-sxssf"));
        if (files == null) {
            return 0;
        }
        long count = 0;
        for (File f : files) {
            count += f.isDirectory() ? (f.list() == null ? 0 : f.list().length) : 1;
        }
        return count;
    }
}
