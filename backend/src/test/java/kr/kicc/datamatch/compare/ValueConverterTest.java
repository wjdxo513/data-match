package kr.kicc.datamatch.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** DB-03: 기술 설계서 6.4 변환 표(타입 분류와 Java 쪽 변환). 날짜·시간 형식은 SQL 식으로 지정되며 Oracle 테스트에서 실제 결과를 확인한다. */
class ValueConverterTest {

    @Test
    void classifiesOracleTypes() {
        for (String t : new String[] {"CHAR", "VARCHAR2", "NCHAR", "NVARCHAR2", "CLOB", "NCLOB", "LONG", "BINARY_FLOAT", "BINARY_DOUBLE"}) {
            assertThat(ColumnKind.of(t)).as(t).isEqualTo(ColumnKind.TEXT);
        }
        assertThat(ColumnKind.of("NUMBER")).isEqualTo(ColumnKind.NUMBER);
        assertThat(ColumnKind.of("FLOAT")).isEqualTo(ColumnKind.NUMBER);
        assertThat(ColumnKind.of("DATE")).isEqualTo(ColumnKind.DATE);
        assertThat(ColumnKind.of("TIMESTAMP(9)")).isEqualTo(ColumnKind.TIMESTAMP);
        assertThat(ColumnKind.of("TIMESTAMP(9) WITH TIME ZONE")).isEqualTo(ColumnKind.TIMESTAMP_TZ);
        assertThat(ColumnKind.of("TIMESTAMP(6) WITH LOCAL TIME ZONE")).isEqualTo(ColumnKind.TIMESTAMP_LTZ);
        assertThat(ColumnKind.of("BLOB")).isEqualTo(ColumnKind.BINARY);
        assertThat(ColumnKind.of("RAW")).isEqualTo(ColumnKind.BINARY);
        assertThat(ColumnKind.of("LONG RAW")).isEqualTo(ColumnKind.BINARY);
        assertThat(ColumnKind.of("INTERVAL DAY(2) TO SECOND(6)")).isEqualTo(ColumnKind.OTHER);
        assertThat(ColumnKind.of(null)).isEqualTo(ColumnKind.OTHER);
        assertThat(ColumnKind.TEXT.selectExpression("\"C\"")).isEqualTo("\"C\"");
        assertThat(ColumnKind.OTHER.selectExpression("\"C\"")).isEqualTo("\"C\"");
    }

    @Test
    void convertsValuesPerTable() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("ab   ");
        when(rs.getBigDecimal(2)).thenReturn(new BigDecimal("1.10"));
        when(rs.getBigDecimal(3)).thenReturn(new BigDecimal("1E+3"));
        when(rs.getBytes(4)).thenReturn(new byte[] {(byte) 0xDE, (byte) 0xAD, 0x0B, 0x00});
        when(rs.getString(5)).thenReturn(null);
        when(rs.getBigDecimal(6)).thenReturn(null);
        when(rs.getBytes(7)).thenReturn(null);
        when(rs.getString(8)).thenReturn("2026-11-10 13:45:30");

        assertThat(ValueConverter.read(rs, 1, ColumnKind.TEXT)).isEqualTo("ab   ");
        assertThat(ValueConverter.read(rs, 2, ColumnKind.NUMBER)).isEqualTo("1.10");
        assertThat(ValueConverter.read(rs, 3, ColumnKind.NUMBER)).isEqualTo("1000");
        assertThat(ValueConverter.read(rs, 4, ColumnKind.BINARY)).isEqualTo("DEAD0B00");
        assertThat(ValueConverter.read(rs, 5, ColumnKind.TEXT)).isNull();
        assertThat(ValueConverter.read(rs, 6, ColumnKind.NUMBER)).isNull();
        assertThat(ValueConverter.read(rs, 7, ColumnKind.BINARY)).isNull();
        assertThat(ValueConverter.read(rs, 8, ColumnKind.DATE)).isEqualTo("2026-11-10 13:45:30");
        assertThat(ValueConverter.hex(new byte[0])).isEmpty();
    }
}
