package kr.kicc.datamatch.config;

import java.util.Map;

/** 환경 ID의 화면 표시 이름(기술 설계서 5장 FR-01). 환경 목록 API와 엑셀 머리글이 함께 쓴다. */
public final class EnvironmentNames {

    private static final Map<String, String> NAMES = Map.of("dev", "개발", "test", "테스트", "prod", "운영");

    private EnvironmentNames() {
    }

    public static String of(String env) {
        return NAMES.getOrDefault(env, env);
    }

    public static boolean isProduction(String env) {
        return "prod".equals(env);
    }
}
