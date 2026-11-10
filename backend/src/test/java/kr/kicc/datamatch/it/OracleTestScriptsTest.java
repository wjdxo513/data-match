package kr.kicc.datamatch.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * SETUP-05: 로컬 Oracle 테스트 스크립트가 DM_IT_ 이외의 스키마를 건드리지 않는지 정적으로 검사한다.
 * 이 DB는 pgweb-boot 로컬 테스트와 함께 쓰므로 PAYZONE 등 다른 스키마를 참조하면 안 된다.
 */
class OracleTestScriptsTest {

    private static final String[] SCRIPTS = {
        "/oracle/00_create_users.sql", "/oracle/10_schema.sql", "/oracle/20_seed.sql", "/oracle/90_cleanup.sql"};

    /** 사용자·테이블·권한 대상이 되는 이름 뒤에 오는 식별자. */
    private static final Pattern TARGETS = Pattern.compile(
            "(?i)\\b(?:CREATE\\s+USER|DROP\\s+USER|ALTER\\s+USER|CREATE\\s+TABLE|CREATE\\s+OR\\s+REPLACE\\s+FUNCTION"
                    + "|INSERT\\s+INTO|(?<!DAY )(?<!YEAR )\\bTO)\\s+\"?([A-Z_][A-Z0-9_$#]*)");

    @Test
    void scriptsOnlyTargetDmItObjects() throws IOException {
        for (String script : SCRIPTS) {
            String text = stripComments(OracleTestDatabase.readResource(script));
            Matcher m = TARGETS.matcher(text);
            while (m.find()) {
                String name = m.group(1).toUpperCase(Locale.ROOT);
                assertThat(name).as(script + " 의 대상 이름").startsWith("DM_IT_");
            }
        }
    }

    @Test
    void scriptsDoNotReferenceOtherSchemas() throws IOException {
        for (String script : SCRIPTS) {
            String text = stripComments(OracleTestDatabase.readResource(script)).toUpperCase(Locale.ROOT);
            assertThat(text).as(script).doesNotContain("PAYZONE").doesNotContain("SYS.").doesNotContain("SYSTEM.");
        }
    }

    @Test
    void splitScriptSeparatesStatementsBySlashLines() {
        List<String> statements = OracleTestDatabase.splitScript(
                "-- 주석\nCREATE TABLE DM_IT_X (ID NUMBER)\n/\nBEGIN\n  NULL;\nEND;\n/\n\n");
        assertThat(statements).containsExactly("CREATE TABLE DM_IT_X (ID NUMBER)", "BEGIN\n  NULL;\nEND;");
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?m)--.*$", "");
    }
}
