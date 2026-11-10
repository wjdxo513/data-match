package kr.kicc.datamatch.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.kicc.datamatch.metadata.PkSupport.UnsupportedReason;
import org.junit.jupiter.api.Test;

/** DB-02: PK 판정(PA-10, PA-19)과 타입 표시 문자열. */
class MetadataModelTest {

    @Test
    void pkJudgement() {
        assertThat(PkSupport.judge(null)).isEqualTo(new PkSupport(false, UnsupportedReason.NO_PK, null, null));
        assertThat(PkSupport.judge(List.of())).isEqualTo(new PkSupport(false, UnsupportedReason.NO_PK, null, null));
        assertThat(PkSupport.judge(List.of(new PkColumn("A", "VARCHAR2"), new PkColumn("B", "VARCHAR2"))).getReason())
                .isEqualTo(UnsupportedReason.COMPOSITE_PK);
        for (String type : new String[] {"DATE", "TIMESTAMP(6)", "RAW", "FLOAT", "CLOB"}) {
            PkSupport pk = PkSupport.judge(List.of(new PkColumn("K", type)));
            assertThat(pk.isSelectable()).as(type).isFalse();
            assertThat(pk.getReason()).as(type).isEqualTo(UnsupportedReason.UNSUPPORTED_PK_TYPE);
        }
        for (String type : new String[] {"CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "NUMBER"}) {
            PkSupport pk = PkSupport.judge(List.of(new PkColumn("K", type)));
            assertThat(pk.isSelectable()).as(type).isTrue();
            assertThat(pk.getPkColumn()).isEqualTo("K");
            assertThat(pk.getReason()).isNull();
            assertThat(pk.isNumeric()).isEqualTo("NUMBER".equals(type));
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
