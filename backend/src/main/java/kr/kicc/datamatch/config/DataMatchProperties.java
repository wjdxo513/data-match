package kr.kicc.datamatch.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@code datamatch.properties}의 환경별 접속 정보(기술 설계서 5장 FR-01, TD-06).
 * 키 형식은 {@code datamatch.environments.<dev|test|prod>.<url|username|password>}이다.
 */
@Component
@ConfigurationProperties(prefix = "datamatch")
public class DataMatchProperties {

    private final Map<String, EnvironmentProperties> environments = new LinkedHashMap<>();
    private final Compare compare = new Compare();

    public Map<String, EnvironmentProperties> getEnvironments() {
        return environments;
    }

    public Compare getCompare() {
        return compare;
    }

    /** 비교 실행 설정. {@code datamatch.compare.chunk-size}(기본 100, 기술 설계서 7.2절). */
    public static class Compare {

        private int chunkSize = 100;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            if (chunkSize < 1 || chunkSize > 1000) {
                throw new IllegalArgumentException("datamatch.compare.chunk-size must be between 1 and 1000");
            }
            this.chunkSize = chunkSize;
        }
    }

    public static class EnvironmentProperties {

        private String url;
        private String username;
        private String password;

        public EnvironmentProperties() {
        }

        public EnvironmentProperties(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        /** URL과 계정이 모두 있어야 사용 가능한 환경이다. */
        public boolean isConfigured() {
            return StringUtils.hasText(url) && StringUtils.hasText(username);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        /** 접속 정보가 로그에 남지 않도록 값을 출력하지 않는다(TD-20). */
        @Override
        public String toString() {
            return "EnvironmentProperties[configured=" + isConfigured() + "]";
        }
    }
}
