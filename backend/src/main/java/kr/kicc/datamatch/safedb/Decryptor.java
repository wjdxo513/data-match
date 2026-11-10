package kr.kicc.datamatch.safedb;

/** SafeDB 복호화기. 비교 작업마다 {@link #openSession()}으로 세션을 연다(BE-03 인터페이스, BE-04 구현). */
public interface Decryptor {

    DecryptSession openSession();
}
