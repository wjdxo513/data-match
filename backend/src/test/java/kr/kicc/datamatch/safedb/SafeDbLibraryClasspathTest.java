package kr.kicc.datamatch.safedb;

import static org.assertj.core.api.Assertions.assertThat;

import com.initech.safedb.SimpleSafeDB;
import java.io.File;
import org.junit.jupiter.api.Test;

/** SETUP-01: SafeDB 벤더 jar 9개가 포함되고 SimpleSafeDB가 classpath에서 해석되는지 확인한다(TD-04). */
class SafeDbLibraryClasspathTest {

    @Test
    void simpleSafeDbClassIsResolvable() {
        assertThat(SimpleSafeDB.class.getName()).isEqualTo("com.initech.safedb.SimpleSafeDB");
    }

    @Test
    void nineVendorJarsArePresent() {
        File[] jars = new File("libs/safedb-lib").listFiles((dir, name) -> name.endsWith(".jar"));
        assertThat(jars).isNotNull().hasSize(9);
    }
}
