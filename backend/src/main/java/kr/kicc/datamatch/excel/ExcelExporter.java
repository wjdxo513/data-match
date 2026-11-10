package kr.kicc.datamatch.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.FieldResult;
import kr.kicc.datamatch.compare.MissingSide;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.PairStatus;
import kr.kicc.datamatch.compare.RowStatus;
import kr.kicc.datamatch.config.EnvironmentNames;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 비교 결과 엑셀(기술 설계서 5장 FR-09, TD-16·17). 서버에 보관된 결과로 메모리(XSSF)에서 만들며 DB를 다시 조회하지 않고 임시 파일을 만들지 않는다.
 * 모든 값은 문자열 셀로 기록하므로 {@code =}로 시작해도 수식이 되지 않는다.
 */
@Component
public class ExcelExporter {

    /** 셀 문자 한도(32,767자). */
    static final int CELL_LIMIT = 32_767;
    static final String TRUNCATED_SUFFIX = "…(이하 생략)";
    static final String NULL_TEXT = "(NULL)";

    /** 불일치·복호화 오류: 빨간 글자, 연한 빨간 배경. 누락: 호박색. */
    static final byte[] MISMATCH_FONT = {(byte) 0xC0, 0x00, 0x00};
    static final byte[] MISMATCH_FILL = {(byte) 0xFF, (byte) 0xE0, (byte) 0xE0};
    static final byte[] MISSING_FONT = {(byte) 0x8A, 0x52, 0x00};
    static final byte[] MISSING_FILL = {(byte) 0xFF, (byte) 0xF0, (byte) 0xC8};

    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] export(CompareTask task, List<PairResult> results) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            writeSummary(wb.createSheet("요약"), task, results, styles);
            writeDetail(wb.createSheet("상세"), task, results, styles);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** {@code datamatch_{A환경}-{B환경}_{테이블}_{yyyyMMdd-HHmmss}.xlsx}. 거래번호는 넣지 않는다. */
    public String fileName(CompareTask task) {
        String time = LocalDateTime.parse(task.getComparedAt(), IN).format(FILE_TIME);
        return "datamatch_" + safe(task.getEnvA()) + "-" + safe(task.getEnvB()) + "_" + safe(task.getTable().getName())
                + "_" + time + ".xlsx";
    }

    private void writeSummary(Sheet sheet, CompareTask task, List<PairResult> results, Styles styles) {
        long match = results.stream().filter(r -> r.getStatus() == PairStatus.MATCH).count();
        long mismatch = results.stream().filter(r -> r.getStatus() == PairStatus.MISMATCH).count();
        long missing = results.stream().filter(r -> r.getStatus() == PairStatus.MISSING).count();
        String columns = task.getColumns().stream()
                .map(c -> c.isDecrypted() ? c.getName() + "(복호화 " + c.getDecrypt() + ")" : c.getName())
                .collect(Collectors.joining(", "));
        String[][] condition = {
            {"A쪽 환경", envLabel(task.getEnvA())},
            {"B쪽 환경", envLabel(task.getEnvB())},
            {"테이블", task.getTable().getOwner() + "." + task.getTable().getName()},
            {"비교 컬럼", columns},
            {"비교 일시", LocalDateTime.parse(task.getComparedAt(), IN).format(SHOW_TIME)},
            {"총 쌍", String.valueOf(results.size())},
            {"일치", String.valueOf(match)},
            {"불일치", String.valueOf(mismatch)},
            {"누락", String.valueOf(missing)},
        };
        int r = 0;
        for (String[] kv : condition) {
            Row row = sheet.createRow(r++);
            cell(row, 0, kv[0], styles.header);
            cell(row, 1, kv[1], null);
        }
        r++;
        header(sheet.createRow(r++), styles, "순번", "거래번호A", "거래번호B", "판정", "불일치 수", "비고");
        for (PairResult p : results) {
            Row row = sheet.createRow(r++);
            CellStyle style = styles.forPair(p.getStatus());
            cell(row, 0, String.valueOf(p.getIndex()), style);
            cell(row, 1, p.getA(), style);
            cell(row, 2, p.getB(), style);
            cell(row, 3, pairLabel(p), style);
            cell(row, 4, String.valueOf(p.getMismatchCount()), style);
            cell(row, 5, remark(p), style);
        }
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 24 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(5, 24 * 256);
    }

    private void writeDetail(Sheet sheet, CompareTask task, List<PairResult> results, Styles styles) {
        int r = 0;
        header(sheet.createRow(r++), styles, "순번", "거래번호A", "거래번호B", "컬럼·필드",
                "A값(" + EnvironmentNames.of(task.getEnvA()) + ")", "B값(" + EnvironmentNames.of(task.getEnvB()) + ")", "상태");
        for (PairResult p : results) {
            if (p.getStatus() == PairStatus.MISSING) {
                Row row = sheet.createRow(r++);
                CellStyle style = styles.missing;
                cell(row, 0, String.valueOf(p.getIndex()), style);
                cell(row, 1, p.getA(), style);
                cell(row, 2, p.getB(), style);
                cell(row, 3, "", style);
                cell(row, 4, "", style);
                cell(row, 5, "", style);
                cell(row, 6, missingLabel(p.getMissing()), style);
                continue;
            }
            for (FieldResult f : p.getRows()) {
                Row row = sheet.createRow(r++);
                CellStyle style = f.getStatus() == RowStatus.MATCH ? null : styles.mismatch;
                cell(row, 0, String.valueOf(p.getIndex()), style);
                cell(row, 1, p.getA(), style);
                cell(row, 2, p.getB(), style);
                cell(row, 3, f.getName(), style);
                cell(row, 4, f.getStatus() == RowStatus.DECRYPT_ERROR ? "" : valueText(f.getA()), style);
                cell(row, 5, f.getStatus() == RowStatus.DECRYPT_ERROR ? "" : valueText(f.getB()), style);
                cell(row, 6, rowLabel(f), style);
            }
        }
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 28 * 256);
        sheet.setColumnWidth(4, 40 * 256);
        sheet.setColumnWidth(5, 40 * 256);
        sheet.setColumnWidth(6, 30 * 256);
    }

    static String envLabel(String env) {
        return EnvironmentNames.of(env) + "(" + env + ")";
    }

    static String pairLabel(PairResult p) {
        switch (p.getStatus()) {
            case MATCH:
                return "일치";
            case MISMATCH:
                return "불일치";
            default:
                return missingLabel(p.getMissing());
        }
    }

    static String missingLabel(MissingSide side) {
        if (side == MissingSide.BOTH) {
            return "누락(A·B 모두 없음)";
        }
        return "누락(" + side + " 없음)";
    }

    static String remark(PairResult p) {
        if (p.getStatus() == PairStatus.MISSING) {
            return p.getMissing() == MissingSide.BOTH ? "A·B 모두 없음" : p.getMissing() + " 없음";
        }
        return p.getRows().stream().anyMatch(f -> f.getStatus() == RowStatus.DECRYPT_ERROR) ? "복호화 오류 포함" : "";
    }

    static String rowLabel(FieldResult f) {
        switch (f.getStatus()) {
            case MATCH:
                return "일치";
            case MISMATCH:
                return "불일치";
            default:
                String cause = f.getCause() == null || f.getCause().name().equals("DECRYPT_FAILED") ? "복호화 실패" : "JSON 해석 실패";
                return "복호화 오류(" + cause + (f.getErrorSide() == null ? "" : ", " + f.getErrorSide() + "쪽") + ")";
        }
    }

    static String valueText(String value) {
        return value == null ? NULL_TEXT : value;
    }

    static String truncate(String value) {
        if (value == null || value.length() <= CELL_LIMIT) {
            return value;
        }
        return value.substring(0, CELL_LIMIT - TRUNCATED_SUFFIX.length()) + TRUNCATED_SUFFIX;
    }

    private static void header(Row row, Styles styles, String... names) {
        for (int i = 0; i < names.length; i++) {
            cell(row, i, names[i], styles.header);
        }
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(truncate(value == null ? "" : value));
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private static final class Styles {
        final XSSFCellStyle header;
        final XSSFCellStyle mismatch;
        final XSSFCellStyle missing;

        Styles(XSSFWorkbook wb) {
            header = wb.createCellStyle();
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            mismatch = filled(wb, MISMATCH_FONT, MISMATCH_FILL);
            missing = filled(wb, MISSING_FONT, MISSING_FILL);
        }

        CellStyle forPair(PairStatus status) {
            switch (status) {
                case MISMATCH:
                    return mismatch;
                case MISSING:
                    return missing;
                default:
                    return null;
            }
        }

        private static XSSFCellStyle filled(XSSFWorkbook wb, byte[] font, byte[] fill) {
            XSSFCellStyle style = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setColor(new XSSFColor(font, null));
            f.setUnderline(Font.U_NONE);
            style.setFont(f);
            style.setFillForegroundColor(new XSSFColor(fill, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }
}
