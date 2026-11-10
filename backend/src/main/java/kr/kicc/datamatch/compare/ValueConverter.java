package kr.kicc.datamatch.compare;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 조회한 값을 비교용 문자열로 바꾼다(기술 설계서 6.4절). NULL은 Java {@code null}이다(Oracle은 빈 문자열도 NULL). */
public final class ValueConverter {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private ValueConverter() {
    }

    public static String read(ResultSet rs, int index, ColumnKind kind) throws SQLException {
        switch (kind) {
            case NUMBER: {
                BigDecimal v = rs.getBigDecimal(index);
                return v == null ? null : v.toPlainString();
            }
            case BINARY: {
                byte[] bytes = rs.getBytes(index);
                return bytes == null ? null : hex(bytes);
            }
            default:
                // 문자형은 공백·대소문자·CHAR 패딩을 그대로 둔다. 날짜·시간은 SQL의 TO_CHAR 결과다.
                return rs.getString(index);
        }
    }

    static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }
}
