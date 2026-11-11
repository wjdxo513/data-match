package kr.kicc.datamatch.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import kr.kicc.datamatch.compare.ColumnSpec;
import kr.kicc.datamatch.compare.CompareTask;
import kr.kicc.datamatch.compare.CompareTaskFactory;
import kr.kicc.datamatch.compare.MissingSide;
import kr.kicc.datamatch.compare.PairResult;
import kr.kicc.datamatch.compare.PairResult.DuplicateCount;
import kr.kicc.datamatch.compare.PairStatus;
import kr.kicc.datamatch.compare.RowStatus;
import kr.kicc.datamatch.compare.Side;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.excel.ExcelExporter;
import kr.kicc.datamatch.job.CompareJob;
import kr.kicc.datamatch.job.JobManager;
import kr.kicc.datamatch.job.JobStatus;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비교 시작·상태·중지·요약·상세 API(기술 설계서 8장). 업무 로직은 {@link CompareTaskFactory}와 {@link JobManager}에 있다. */
@RestController
public class CompareController {

    private static final Logger log = LoggerFactory.getLogger(CompareController.class);
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CompareTaskFactory taskFactory;
    private final JobManager jobs;
    private final ExcelExporter excelExporter;

    public CompareController(CompareTaskFactory taskFactory, JobManager jobs, ExcelExporter excelExporter) {
        this.taskFactory = taskFactory;
        this.jobs = jobs;
        this.excelExporter = excelExporter;
    }

    @PostMapping("/api/compare")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartResponse start(@RequestBody CompareRequest request) {
        List<ColumnSpec> columns = request.getColumns() == null ? List.of() : request.getColumns().stream()
                .map(c -> c.getDecrypt() == null || c.getDecrypt().getPolicy() == null
                        ? ColumnSpec.plain(c.getName())
                        : ColumnSpec.decrypted(c.getName(), c.getDecrypt().getPolicy()))
                .collect(Collectors.toList());
        CompareTask task = taskFactory.create(request.getEnvA(), request.getEnvB(), request.getOwner(), request.getTable(),
                request.getKeyColumn(), columns, request.getPairsText());
        CompareJob job = jobs.start(task);
        return new StartResponse(job.id(), job.total());
    }

    @GetMapping("/api/compare/{jobId}")
    public StatusResponse status(@PathVariable String jobId) {
        CompareJob job = jobs.get(jobId);
        return new StatusResponse(job.status(), job.processed(), job.total(), job.errorCode(), job.failedSide(), job.failedEnv());
    }

    @PostMapping("/api/compare/{jobId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CancelResponse cancel(@PathVariable String jobId) {
        return new CancelResponse(jobs.cancel(jobId).status());
    }

    @GetMapping("/api/compare/{jobId}/summary")
    public SummaryResponse summary(@PathVariable String jobId) {
        List<PairResult> results = jobs.results(jobId);
        CompareTask task = jobs.get(jobId).task();
        Condition condition = new Condition(task.getEnvA(), task.getEnvB(), task.getTable().getOwner(), task.getTable().getName(),
                task.getKeyColumn(),
                task.getColumns().stream().map(c -> new ConditionColumn(c.getName(), c.getDecrypt())).collect(Collectors.toList()),
                task.getComparedAt());
        long match = results.stream().filter(r -> r.getStatus() == PairStatus.MATCH).count();
        long mismatch = results.stream().filter(r -> r.getStatus() == PairStatus.MISMATCH).count();
        long missing = results.stream().filter(r -> r.getStatus() == PairStatus.MISSING).count();
        long duplicate = results.stream().filter(r -> r.getStatus() == PairStatus.DUPLICATE).count();
        List<SummaryItem> items = results.stream()
                .map(r -> new SummaryItem(r.getIndex(), r.getA(), r.getB(), r.getStatus(), r.getMismatchCount(), r.getMissing(),
                        r.getDuplicate()))
                .collect(Collectors.toList());
        return new SummaryResponse(condition, new Counts(results.size(), match, mismatch, missing, duplicate), items);
    }

    @GetMapping("/api/compare/{jobId}/pairs/{index}")
    public DetailResponse pair(@PathVariable String jobId, @PathVariable int index) {
        List<PairResult> results = jobs.results(jobId);
        if (index < 1 || index > results.size()) {
            throw new DataMatchException(ErrorCode.PAIR_NOT_FOUND, "pair not found");
        }
        PairResult r = results.get(index - 1);
        List<DetailRow> rows = r.getRows().stream()
                .map(f -> new DetailRow(f.getName(), f.getA(), f.getB(), f.getStatus(),
                        f.getCause() == null ? null : f.getCause().name()))
                .collect(Collectors.toList());
        return new DetailResponse(r.getIndex(), r.getA(), r.getB(), r.getStatus(), r.getMissing(), r.getDuplicate(), rows);
    }

    /** 엑셀 다운로드(FR-09, TD-16). 생성 실패는 500 {@code EXCEL_FAILED}이고 서버의 결과는 그대로 남는다. */
    @GetMapping("/api/compare/{jobId}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable String jobId) {
        List<PairResult> results = jobs.results(jobId);
        CompareTask task = jobs.get(jobId).task();
        byte[] file;
        try {
            file = excelExporter.export(task, results);
        } catch (Exception e) {
            log.error("excel export failed: type={}", e.getClass().getName(), e);
            throw new DataMatchException(ErrorCode.EXCEL_FAILED, "excel export failed");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(excelExporter.fileName(task), StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX)
                .body(file);
    }

    // ---- 요청·응답 형식(기술 설계서 8장) ----

    @Data
    @NoArgsConstructor
    public static class CompareRequest {
        private String envA;
        private String envB;
        private String owner;
        private String table;
        /** 비교 키 컬럼(필수, PRD PA-10). */
        private String keyColumn;
        private List<ColumnRequest> columns;
        private String pairsText;
    }

    @Data
    @NoArgsConstructor
    public static class ColumnRequest {
        private String name;
        private DecryptRequest decrypt;
    }

    @Data
    @NoArgsConstructor
    public static class DecryptRequest {
        private DecryptPolicy policy;
    }

    @Value
    public static class StartResponse {
        String jobId;
        int total;
    }

    @Value
    public static class StatusResponse {
        JobStatus status;
        int processed;
        int total;
        ErrorCode errorCode;
        Side failedSide;
        String failedEnv;
    }

    @Value
    public static class CancelResponse {
        JobStatus status;
    }

    @Value
    public static class SummaryResponse {
        Condition condition;
        Counts counts;
        List<SummaryItem> items;
    }

    @Value
    public static class Condition {
        String envA;
        String envB;
        String owner;
        String table;
        String keyColumn;
        List<ConditionColumn> columns;
        String comparedAt;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionColumn {
        String name;
        DecryptPolicy decrypt;
    }

    @Value
    public static class Counts {
        long total;
        long match;
        long mismatch;
        long missing;
        long duplicate;
    }

    @Value
    public static class SummaryItem {
        int index;
        String a;
        String b;
        PairStatus status;
        int mismatchCount;
        MissingSide missing;
        /** 키 중복이면 양쪽 조회 건수, 아니면 null. */
        DuplicateCount duplicate;
    }

    @Value
    public static class DetailResponse {
        int index;
        String a;
        String b;
        PairStatus status;
        MissingSide missing;
        DuplicateCount duplicate;
        List<DetailRow> rows;
    }

    /** 상세 행. {@code message}는 복호화 오류 원인 코드이고 화면 문구는 프론트가 만든다(실행 계획 PL-14). */
    @Value
    public static class DetailRow {
        String name;
        String a;
        String b;
        RowStatus status;
        String message;
    }
}
