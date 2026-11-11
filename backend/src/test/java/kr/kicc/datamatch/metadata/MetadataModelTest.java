package kr.kicc.datamatch.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** DB-02·DB-04: 비교 키 가능 타입과 기본 비교 키(PRD PA-10, PA-19), 타입 표시 문자열. */
class MetadataModelTest {

    private static ColumnMeta col(String name, String type) {
        return new ColumnMeta(name, type, null, null, null, null);
    }

    @Test
    void keyableTypesAndDefaultKeyColumn() {
        for (String type : new String[] {"CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "NUMBER"}) {
            assertThat(col("K", type).isKeyable()).as(type).isTrue();
            TableMetadata single = new TableMetadata("O", "T", List.of("K"), List.of(col("K", type)));
            assertThat(single.defaultKeyColumn()).as(type).isEqualTo("K");
        }
        for (String type : new String[] {"DATE", "TIMESTAMP(6)", "RAW", "FLOAT", "CLOB", "BLOB"}) {
            assertThat(col("K", type).isKeyable()).as(type).isFalse();
            TableMetadata unsupported = new TableMetadata("O", "T", List.of("K"), List.of(col("K", type)));
            assertThat(unsupported.defaultKeyColumn()).as(type).isNull();
        }
        TableMetadata composite = new TableMetadata("O", "T", List.of("CONTROL_NO", "SEQ"),
                List.of(col("CONTROL_NO", "VARCHAR2"), col("SEQ", "NUMBER")));
        assertThat(composite.defaultKeyColumn()).isNull();
        assertThat(composite.isPk("CONTROL_NO")).isTrue();
        assertThat(composite.isPk("SEQ")).isTrue();
        TableMetadata noPk = new TableMetadata("O", "T", List.of(), List.of(col("ID", "VARCHAR2")));
        assertThat(noPk.defaultKeyColumn()).isNull();
        assertThat(noPk.isPk("ID")).isFalse();
        assertThat(col("N", "NUMBER").isNumber()).isTrue();
        assertThat(col("N", "VARCHAR2").isNumber()).isFalse();
    }

    @Test
    void requireKeyColumnAcceptsOnlyExistingKeyableColumns() {
        TableMetadata t = new TableMetadata("O", "T", List.of("CONTROL_NO", "D"),
                List.of(col("CONTROL_NO", "VARCHAR2"), col("D", "DATE"), col("AMT", "NUMBER")));
        assertThat(t.requireKeyColumn("CONTROL_NO").getName()).isEqualTo("CONTROL_NO");
        assertThat(t.requireKeyColumn("AMT").getName()).isEqualTo("AMT");
        for (String bad : new String[] {null, "", " ", "D", "NOPE", "control_no"}) {
            assertThatThrownBy(() -> t.requireKeyColumn(bad))
                    .as(String.valueOf(bad))
                    .isInstanceOf(DataMatchException.class)
                    .satisfies(e -> assertThat(((DataMatchException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    @Test
    void displayType() {
        assertThat(new ColumnMeta("C", "VARCHAR2", 80, null, null, 20).displayType()).isEqualTo("VARCHAR2(20)");
        assertThat(new ColumnMeta("C", "NCHAR", 20, null, null, 10).displayType()).isEqualTo("NCHAR(10)");
        assertThat(new ColumnMeta("C", "CHAR", 1, null, null, 0).displayType()).isEqualTo("CHAR");
        assertThat(new ColumnMeta("C", "NUMBER", 22, null, null, 0).displayType()).isEqualTo("NUMBER");
        assertThat(new ColumnMeta("C", "NUMBER", 22, 15, 0, 0).displayType()).isEqualTo("NUMBER(15)");
        assertThat(new ColumnMeta("C", "NUMBER", 22, 15, 2, 0).displayType()).isEqualTo("NUMBER(15,2)");
        assertThat(new ColumnMeta("C", "NUMBER", 22, 15, null, 0).displayType()).isEqualTo("NUMBER(15)");
        assertThat(new ColumnMeta("C", "RAW", 16, null, null, 0).displayType()).isEqualTo("RAW(16)");
        assertThat(new ColumnMeta("C", "RAW", null, null, null, 0).displayType()).isEqualTo("RAW");
        assertThat(new ColumnMeta("C", "TIMESTAMP(9) WITH TIME ZONE", 13, null, 9, 0).displayType())
                .isEqualTo("TIMESTAMP(9) WITH TIME ZONE");
        assertThat(new ColumnMeta("C", null, null, null, null, null).displayType()).isEmpty();
        assertThat(new ColumnMeta("C", "CLOB", 4000, null, null, 0).isClob()).isTrue();
        assertThat(new ColumnMeta("C", "NCLOB", 4000, null, null, 0).isClob()).isFalse();
    }
}
