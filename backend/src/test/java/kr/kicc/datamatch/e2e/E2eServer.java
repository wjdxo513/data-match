package kr.kicc.datamatch.e2e;

import java.io.IOException;
import kr.kicc.datamatch.DataMatchApplication;
import kr.kicc.datamatch.compare.RowRepository;
import kr.kicc.datamatch.compare.TestRowRepositories;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.safedb.Decryptor;
import kr.kicc.datamatch.safedb.FakeDecryptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * INT-02 E2E 서버(실행 계획 PL-05). 테스트 클래스패스로 {@code gradlew e2eServer}가 실행한다.
 *
 * <ul>
 *   <li>복호화는 가짜 {@link FakeDecryptor}(로컬 Oracle 합성 데이터의 {@code FAKEENC:} 형식)이며 SafeDB에 접속하지 않는다.</li>
 *   <li>타임아웃 시나리오(SN-E01-4)를 재현하려고 쿼리 타임아웃을 {@code datamatch.e2e.query-timeout-seconds}(기본 8초)로 줄인다.</li>
 *   <li>접속 정보는 Gradle 작업이 환경 변수({@code DATAMATCH_ENVIRONMENTS_*})로 넘기며 파일에 쓰지 않는다.</li>
 * </ul>
 * 이 클래스와 가짜 복호화는 {@code src/test}에 있어 배포 zip에 포함되지 않는다.
 */
public final class E2eServer {

    private E2eServer() {
    }

    public static void main(String[] args) {
        int timeout = Integer.getInteger("datamatch.e2e.query-timeout-seconds", 8);
        SpringApplication app = new SpringApplication(DataMatchApplication.class);
        app.addInitializers(ctx -> {
            GenericApplicationContext gac = (GenericApplicationContext) ctx;
            gac.getBeanFactory().registerSingleton("e2eExcludeTestTypes", new ExcludeTestTypes());
            gac.registerBean("e2eDecryptor", Decryptor.class, FakeDecryptor::new, bd -> bd.setPrimary(true));
            gac.registerBean("e2eRowRepository", RowRepository.class,
                    () -> TestRowRepositories.withTimeout(gac.getBean(DataSourceRegistry.class), 100, timeout),
                    bd -> bd.setPrimary(true));
        });
        app.run(args);
    }

    /** 테스트 소스의 테스트 클래스·테스트 전용 설정·테스트용 컨트롤러가 컴포넌트 스캔에 잡히지 않게 한다. */
    static final class ExcludeTestTypes extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
            String name = reader.getClassMetadata().getClassName();
            return name.endsWith("Test") || name.contains("Test$") || name.contains(".Test")
                    || reader.getAnnotationMetadata().hasAnnotation(TestConfiguration.class.getName());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ExcludeTestTypes;
        }

        @Override
        public int hashCode() {
            return ExcludeTestTypes.class.hashCode();
        }
    }
}
