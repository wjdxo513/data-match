package kr.kicc.datamatch.web;

import java.util.List;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.config.EnvironmentNames;
import lombok.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 선택 가능한 환경 목록(기술 설계서 5장 FR-01, 8장). 접속 정보는 절대 반환하지 않는다. */
@RestController
public class EnvironmentController {

    private final DataSourceRegistry registry;

    public EnvironmentController(DataSourceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/environments")
    public List<EnvironmentResponse> environments() {
        return registry.availableEnvironments().stream()
                .map(id -> new EnvironmentResponse(id, EnvironmentNames.of(id), EnvironmentNames.isProduction(id)))
                .collect(Collectors.toList());
    }

    @Value
    public static class EnvironmentResponse {
        String id;
        String name;
        boolean production;
    }
}
