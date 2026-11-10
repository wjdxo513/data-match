package kr.kicc.datamatch.it;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 로컬 Oracle 테스트 스키마가 필요한 테스트(실행 계획의 [Oracle] 항목).
 * {@code gradlew oracleTest} 로 실행하며, 환경 변수가 없으면 건너뛰어 미검증으로 남는다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("oracle")
@EnabledIfEnvironmentVariable(named = OracleTestDatabase.URL, matches = ".+")
public @interface OracleIntegrationTest {
}
