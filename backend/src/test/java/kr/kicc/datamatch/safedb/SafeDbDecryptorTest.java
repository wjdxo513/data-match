package kr.kicc.datamatch.safedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-04: SafeDB 복호화 어댑터(TD-10, TA-07, TA-11)를 래퍼 대체로 검증한다. 실제 서버 연동은 INT-04. */
class SafeDbDecryptorTest {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private SafeDbClient client;
    private SafeDbDecryptor decryptor;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(SafeDbClient.class);
        when(client.login()).thenReturn(true);
        decryptor = new SafeDbDecryptor(client);
    }

    @Test
    void callsLibraryWithPolicyUserTableAndColumn() throws Exception {
        when(client.decrypt(anyString(), anyString(), anyString(), any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        DecryptSession session = decryptor.openSession();
        session.decrypt("CIPHER1", DecryptPolicy.KDKEY);
        session.decrypt("CIPHER2", DecryptPolicy.PANEN);
        verify(client).decrypt(eq("SAFEDB"), eq("SAFEDB.POLICY"), eq("KDKEY"), eq("CIPHER1".getBytes(StandardCharsets.UTF_8)));
        verify(client).decrypt(eq("SAFEDB"), eq("SAFEDB.POLICY"), eq("PANEN"), eq("CIPHER2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void decodesKdkeyAsEucKrAndPanenAsUtf8() throws Exception {
        String korean = "{\"memo\":\"한글 합성값\"}";
        when(client.decrypt(anyString(), anyString(), eq("KDKEY"), any())).thenReturn(korean.getBytes(EUC_KR));
        when(client.decrypt(anyString(), anyString(), eq("PANEN"), any())).thenReturn(korean.getBytes(StandardCharsets.UTF_8));
        DecryptSession session = decryptor.openSession();
        assertThat(session.decrypt("C", DecryptPolicy.KDKEY)).isEqualTo(korean);
        assertThat(session.decrypt("C", DecryptPolicy.PANEN)).isEqualTo(korean);
        assertThat(SafeDbDecryptor.charsetOf(DecryptPolicy.KDKEY).name()).isEqualTo("EUC-KR");
        assertThat(SafeDbDecryptor.charsetOf(DecryptPolicy.PANEN)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void loginFailureByFalseOrExceptionIsLoginDecryptException() throws Exception {
        when(client.login()).thenReturn(false);
        assertLoginFailure(decryptor.openSession());
        when(client.login()).thenThrow(new IllegalStateException("connect timed out to 10.0.0.1"));
        assertLoginFailure(decryptor.openSession());
        verify(client, never()).decrypt(anyString(), anyString(), anyString(), any());
    }

    private static void assertLoginFailure(DecryptSession session) {
        assertThatThrownBy(() -> session.decrypt("SECRET-CIPHER", DecryptPolicy.KDKEY))
                .isInstanceOf(DecryptException.class)
                .satisfies(e -> assertThat(((DecryptException) e).isLoginFailure()).isTrue())
                .hasMessageNotContaining("SECRET-CIPHER")
                .hasMessageNotContaining("10.0.0.1");
    }

    @Test
    void decryptErrorsDoNotLeakCipherOrPlain() throws Exception {
        when(client.decrypt(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("bad block for SECRET-CIPHER plain=PLAIN-TEXT"))
                .thenReturn(null);
        DecryptSession session = decryptor.openSession();
        assertThatThrownBy(() -> session.decrypt("SECRET-CIPHER", DecryptPolicy.KDKEY))
                .isInstanceOf(DecryptException.class)
                .satisfies(e -> assertThat(((DecryptException) e).isLoginFailure()).isFalse())
                .satisfies(e -> assertThat(e.getCause()).isNull())
                .hasMessageNotContaining("SECRET-CIPHER")
                .hasMessageNotContaining("PLAIN-TEXT");
        assertThatThrownBy(() -> session.decrypt("SECRET-CIPHER", DecryptPolicy.KDKEY))
                .isInstanceOf(DecryptException.class)
                .hasMessageNotContaining("SECRET-CIPHER");
    }

    @Test
    void loginIsAttemptedOncePerSessionAndNotRetriedAfterFailureWithinSession() throws Exception {
        when(client.decrypt(anyString(), anyString(), anyString(), any())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        DecryptSession ok = decryptor.openSession();
        ok.decrypt("C1", DecryptPolicy.KDKEY);
        ok.decrypt("C2", DecryptPolicy.KDKEY);
        ok.decrypt("C3", DecryptPolicy.PANEN);
        verify(client, times(1)).login();

        when(client.login()).thenReturn(false);
        DecryptSession failing = decryptor.openSession();
        assertLoginFailure(failing);
        assertLoginFailure(failing);
        assertLoginFailure(failing);
        verify(client, times(2)).login();

        when(client.login()).thenReturn(true);
        decryptor.openSession().decrypt("C4", DecryptPolicy.KDKEY);
        verify(client, times(3)).login();
    }

    @Test
    void libraryCallsAreSerializedAcrossThreads() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        when(client.decrypt(anyString(), anyString(), anyString(), any())).thenAnswer(inv -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            Thread.sleep(5);
            active.decrementAndGet();
            return "{}".getBytes(StandardCharsets.UTF_8);
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                DecryptSession session = decryptor.openSession();
                tasks.add(() -> session.decrypt("C", DecryptPolicy.KDKEY));
            }
            for (Future<String> f : pool.invokeAll(tasks)) {
                assertThat(f.get()).isEqualTo("{}");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxActive.get()).isEqualTo(1);
    }

    @Test
    void noEncryptionApiIsExposed() {
        for (Class<?> type : new Class<?>[] {SafeDbClient.class, SafeDbDecryptor.class, Decryptor.class, DecryptSession.class}) {
            assertThat(Arrays.stream(type.getDeclaredMethods()).map(Method::getName))
                    .as(type.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().startsWith("encrypt"));
        }
    }
}
