package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.config.DataMatchProperties.EnvironmentProperties;
import kr.kicc.datamatch.config.DataSourceRegistry;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.MetadataService;
import kr.kicc.datamatch.metadata.TableMetadata;
import kr.kicc.datamatch.safedb.DecryptPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-05: 비교 요청 검증(환경, 메타데이터, 복호화 대상, 거래번호 입력)과 불변 사본. */
class CompareTaskFactoryTest {

    private static final TableMetadata STR = new TableMetadata("O", "T", List.of("ID"), List.of(
            new ColumnMeta("ID", "VARCHAR2", 10, null, null, 10), new ColumnMeta("AMOUNT", "NUMBER", 22, null, null, 0),
            new ColumnMeta("APPR_MSG", "CLOB", 4000, null, null, 0)));
    private static final TableMetadata NUM = new TableMetadata("O", "N", List.of("ID"), List.of(
            new ColumnMeta("ID", "NUMBER", 22, 15, 0, 0)));

    private MetadataService metadata;
    private CompareTaskFactory factory;

    @BeforeEach
    void setUp() {
        DataMatchProperties p = new DataMatchProperties();
        p.getEnvironments().put("test", new EnvironmentProperties("jdbc:x", "u", "p"));
        p.getEnvironments().put("prod", new EnvironmentProperties("jdbc:x", "u", "p"));
        metadata = mock(MetadataService.class);
        when(metadata.describeTable("test", "O", "T")).thenReturn(STR);
        when(metadata.describeTable("test", "O", "N")).thenReturn(NUM);
        factory = new CompareTaskFactory(new DataSourceRegistry(p), metadata);
    }

    private static ErrorCode code(Throwable e) {
        return ((DataMatchException) e).code();
    }

    @Test
    void createsImmutableTaskFromAEnvironmentMetadata() {
        List<ColumnSpec> cols = new ArrayList<>(List.of(ColumnSpec.plain("AMOUNT"), ColumnSpec.decrypted("APPR_MSG", DecryptPolicy.KDKEY)));
        CompareTask task = factory.create("test", "prod", "O", "T", "ID", cols, "A1,B1\r\nA2\tB2");
        verify(metadata).describeTable("test", "O", "T");
        cols.clear();
        cols.add(ColumnSpec.plain("ID"));
        assertThat(task.getColumns()).extracting(ColumnSpec::getName).containsExactly("AMOUNT", "APPR_MSG");
        assertThat(task.getPairs()).extracting(TransactionPair::getA).containsExactly("A1", "A2");
        assertThat(task.getEnvA()).isEqualTo("test");
        assertThat(task.getEnvB()).isEqualTo("prod");
        assertThat(task.getComparedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThatThrownBy(() -> task.getPairs().add(new TransactionPair(9, "x", "y"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void environmentsMustBeConfigured() {
        assertThatThrownBy(() -> factory.create("dev", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("ID")), "A,B"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.ENV_NOT_CONFIGURED));
        assertThatThrownBy(() -> factory.create("test", null, "O", "T", "ID", List.of(ColumnSpec.plain("ID")), "A,B"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.ENV_NOT_CONFIGURED));
    }

    @Test
    void columnsMustExistBeUniqueAndDecryptOnlyClob() {
        for (List<ColumnSpec> cols : List.of(
                List.<ColumnSpec>of(),
                List.of(ColumnSpec.plain("NOPE")),
                List.of(ColumnSpec.plain("ID"), ColumnSpec.plain("ID")),
                List.of(ColumnSpec.decrypted("AMOUNT", DecryptPolicy.KDKEY)))) {
            assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "ID", cols, "A,B"))
                    .as(cols.toString())
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "ID", null, "A,B"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void keyColumnMustBeGivenExistAndBeKeyable() {
        for (String key : new String[] {null, "", "NOPE", "APPR_MSG"}) {
            assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", key, List.of(ColumnSpec.plain("AMOUNT")), "A,B"))
                    .as(String.valueOf(key))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        // 비교 키는 PK가 아니어도 문자형·NUMBER면 된다(PRD PA-10). 숫자 검증은 비교 키 타입을 따른다.
        CompareTask byAmount = factory.create("test", "prod", "O", "T", "AMOUNT", List.of(ColumnSpec.plain("ID")), "1,2");
        assertThat(byAmount.getKeyColumn()).isEqualTo("AMOUNT");
        assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "AMOUNT", List.of(ColumnSpec.plain("ID")), "X,2"))
                .satisfies(e -> assertThat(((DataMatchException) e).details())
                        .isEqualTo(List.of(Map.of("line", 1, "reason", "INVALID_NUMBER"))));
        assertThat(factory.create("test", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("AMOUNT")), "X,2").getPairs()).hasSize(1);
    }

    @Test
    void pairInputErrorsAreInvalidInputWithLineDetails() {
        assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("ID")), "A1,B1\nbad\n,B3"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT))
                .satisfies(e -> assertThat(((DataMatchException) e).details()).isEqualTo(List.of(
                        Map.of("line", 2, "reason", "NO_DELIMITER"), Map.of("line", 3, "reason", "EMPTY_A"))));
        assertThatThrownBy(() -> factory.create("test", "prod", "O", "N", "ID", List.of(ColumnSpec.plain("ID")), "1,2\nX,3"))
                .satisfies(e -> assertThat(((DataMatchException) e).details()).isEqualTo(List.of(
                        Map.of("line", 2, "reason", "INVALID_NUMBER"))));
        for (String empty : new String[] {"", "\n  \n", null}) {
            assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("ID")), empty))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    @Test
    void moreThanThousandPairsIsRejectedWithCount() {
        String text = IntStream.rangeClosed(1, 1001).mapToObj(i -> "A" + i + ",B" + i).collect(Collectors.joining("\n"));
        assertThatThrownBy(() -> factory.create("test", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("ID")), text))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.INVALID_INPUT))
                .satisfies(e -> assertThat(((DataMatchException) e).details())
                        .isEqualTo(List.of(Map.of("reason", "PAIR_LIMIT_EXCEEDED", "count", 1001))));
        String ok = IntStream.rangeClosed(1, 1000).mapToObj(i -> "A" + i + ",B" + i).collect(Collectors.joining("\n"));
        assertThat(factory.create("test", "prod", "O", "T", "ID", List.of(ColumnSpec.plain("ID")), ok).getPairs()).hasSize(1000);
    }
}
