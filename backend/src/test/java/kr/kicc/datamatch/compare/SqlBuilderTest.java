package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import kr.kicc.datamatch.metadata.ColumnMeta;
import kr.kicc.datamatch.metadata.TableMetadata;
import org.junit.jupiter.api.Test;

/** DB-03: 묶음 조회 SQL이 SELECT 템플릿뿐이고 식별자는 메타데이터 값, 거래번호는 바인드 변수로만 들어가는지 확인한다. */
class SqlBuilderTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE|GRANT|REVOKE|RENAME|COMMIT|ROLLBACK|EXECUTE|BEGIN|CALL)\\b",
            Pattern.CASE_INSENSITIVE);

    private static ColumnMeta col(String name, String type) {
        return new ColumnMeta(name, type, null, null, null, null);
    }

    private static final TableMetadata TABLE = new TableMetadata("DM_IT_OWNER", "DM_IT_TYPES", List.of("ID"), List.of(
            col("ID", "VARCHAR2"), col("C_CHAR", "CHAR"), col("C_NUMBER", "NUMBER"), col("C_FLOAT", "FLOAT"),
            col("C_BDOUBLE", "BINARY_DOUBLE"), col("C_DATE", "DATE"), col("C_TS", "TIMESTAMP(9)"),
            col("C_TSTZ", "TIMESTAMP(9) WITH TIME ZONE"), col("C_TSLTZ", "TIMESTAMP(6) WITH LOCAL TIME ZONE"),
            col("C_CLOB", "CLOB"), col("C_BLOB", "BLOB"), col("C_RAW", "RAW"), col("C_INTERVAL", "INTERVAL DAY(2) TO SECOND(6)"),
            col("UPDATE_DT", "DATE")));

    @Test
    void buildsSelectWithTypeExpressionsAndBindPlaceholdersOnly() {
        RowQuery q = SqlBuilder.build(TABLE, "ID", List.of("C_NUMBER", "C_DATE", "C_TS", "C_TSTZ", "C_TSLTZ", "C_RAW"));
        String sql = q.sql(3);
        assertThat(sql).isEqualTo("SELECT \"ID\", \"C_NUMBER\", TO_CHAR(\"C_DATE\", 'YYYY-MM-DD HH24:MI:SS'), "
                + "TO_CHAR(\"C_TS\", 'YYYY-MM-DD HH24:MI:SS.FF9'), TO_CHAR(\"C_TSTZ\", 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'), "
                + "TO_CHAR(\"C_TSLTZ\", 'YYYY-MM-DD HH24:MI:SS.FF9'), \"C_RAW\" "
                + "FROM \"DM_IT_OWNER\".\"DM_IT_TYPES\" WHERE \"ID\" IN (?, ?, ?)");
        assertThat(q.getColumns()).extracting(ColumnMeta::getName)
                .containsExactly("C_NUMBER", "C_DATE", "C_TS", "C_TSTZ", "C_TSLTZ", "C_RAW");
        assertThat(q.isNumericKey()).isFalse();
    }

    @Test
    void everySqlStartsWithSelectAndHasNoDmlOrDdl() {
        List<String> all = TABLE.getColumns().stream().map(ColumnMeta::getName).collect(java.util.stream.Collectors.toList());
        for (int n : new int[] {1, 2, 100}) {
            for (List<String> cols : List.of(all, List.of("ID"), List.of("UPDATE_DT", "C_CLOB"))) {
                String sql = SqlBuilder.build(TABLE, "ID", cols).sql(n);
                assertThat(sql).startsWith("SELECT ");
                assertThat(FORBIDDEN.matcher(sql).find()).as(sql).isFalse();
                assertThat(sql.chars().filter(ch -> ch == '?').count()).isEqualTo(n);
                assertThat(sql).doesNotContain(";").doesNotContain("--");
            }
        }
        assertThatThrownBy(() -> SqlBuilder.build(TABLE, "ID", List.of("ID")).sql(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownColumnIsRejectedBeforeSqlIsBuilt() {
        for (String bad : new String[] {"NOPE", "c_number", "ID\" FROM DUAL --", "ID, (SELECT 1 FROM DUAL)"}) {
            assertThatThrownBy(() -> SqlBuilder.build(TABLE, "ID", List.of("ID", bad)))
                    .isInstanceOf(DataMatchException.class)
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        assertThatThrownBy(() -> SqlBuilder.quote("A\"B")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlBuilder.quote("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlBuilder.quote(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pkIsAlwaysFetchedFirstAndNumericPkIsFlagged() {
        TableMetadata num = new TableMetadata("O", "T", List.of("TRX_ID"), List.of(col("TRX_ID", "NUMBER"), col("AMOUNT", "NUMBER")));
        RowQuery q = SqlBuilder.build(num, "TRX_ID", List.of("AMOUNT"));
        assertThat(q.sql(1)).isEqualTo("SELECT \"TRX_ID\", \"AMOUNT\" FROM \"O\".\"T\" WHERE \"TRX_ID\" IN (?)");
        assertThat(q.isNumericKey()).isTrue();
        assertThat(SqlBuilder.build(num, "TRX_ID", List.of("TRX_ID", "AMOUNT")).sql(1))
                .startsWith("SELECT \"TRX_ID\", \"TRX_ID\", \"AMOUNT\"");
    }

    @Test
    void anyKeyableColumnCanBeTheKeyAndOthersAreRejected() {
        RowQuery q = SqlBuilder.build(TABLE, "C_CHAR", List.of("ID"));
        assertThat(q.sql(1)).isEqualTo("SELECT \"C_CHAR\", \"ID\" FROM \"DM_IT_OWNER\".\"DM_IT_TYPES\" WHERE \"C_CHAR\" IN (?)");
        assertThat(q.isNumericKey()).isFalse();
        assertThat(SqlBuilder.build(TABLE, "C_NUMBER", List.of("ID")).isNumericKey()).isTrue();
        for (String bad : new String[] {"C_DATE", "C_CLOB", "C_FLOAT", "NOPE", "c_char", null}) {
            assertThatThrownBy(() -> SqlBuilder.build(TABLE, bad, List.of("ID")))
                    .as(String.valueOf(bad))
                    .isInstanceOf(DataMatchException.class)
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }
}
