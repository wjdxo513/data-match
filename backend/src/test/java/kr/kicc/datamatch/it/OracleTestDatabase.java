package kr.kicc.datamatch.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 로컬 Oracle 테스트 DB 접속 도우미(SETUP-05, 실행 계획 D-02).
 *
 * <p>접속 정보는 저장소에 두지 않고 환경 변수로만 받는다.
 * <ul>
 *   <li>{@code DATAMATCH_IT_URL} 예: {@code jdbc:oracle:thin:@//localhost:1521/FREEPDB1}</li>
 *   <li>{@code DATAMATCH_IT_OWNER_USER}, {@code DATAMATCH_IT_OWNER_PASSWORD}</li>
 *   <li>{@code DATAMATCH_IT_RO_A_USER}, {@code DATAMATCH_IT_RO_A_PASSWORD}</li>
 *   <li>{@code DATAMATCH_IT_RO_B_USER}, {@code DATAMATCH_IT_RO_B_PASSWORD}</li>
 * </ul>
 */
public final class OracleTestDatabase {

    public static final String URL = "DATAMATCH_IT_URL";

    private OracleTestDatabase() {
    }

    public static String url() {
        return required(URL);
    }

    public static Connection owner() throws SQLException {
        return connect("OWNER");
    }

    public static Connection roA() throws SQLException {
        return connect("RO_A");
    }

    public static Connection roB() throws SQLException {
        return connect("RO_B");
    }

    public static String user(String role) {
        return required("DATAMATCH_IT_" + role + "_USER");
    }

    public static String password(String role) {
        return required("DATAMATCH_IT_" + role + "_PASSWORD");
    }

    private static Connection connect(String role) throws SQLException {
        return DriverManager.getConnection(url(), user(role), password(role));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("환경 변수 " + name + " 가 설정되지 않았습니다.");
        }
        return value;
    }

    /** "/" 한 줄로 구분된 스크립트를 읽어 문장 목록으로 나눈다. 빈 문장과 주석 줄은 건너뛴다. */
    public static List<String> splitScript(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\r?\\n")) {
            if (line.trim().equals("/")) {
                addIfPresent(statements, current);
                current.setLength(0);
            } else if (!line.trim().startsWith("--")) {
                current.append(line).append('\n');
            }
        }
        addIfPresent(statements, current);
        return statements;
    }

    private static void addIfPresent(List<String> statements, StringBuilder sql) {
        String text = sql.toString().trim();
        if (!text.isEmpty()) {
            statements.add(text);
        }
    }

    public static void runScript(Connection connection, String resource) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : splitScript(readResource(resource))) {
                statement.execute(sql);
            }
        }
    }

    public static String readResource(String resource) throws IOException {
        try (InputStream in = OracleTestDatabase.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
