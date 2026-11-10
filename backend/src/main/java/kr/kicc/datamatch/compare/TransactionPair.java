package kr.kicc.datamatch.compare;

import lombok.Value;

/** 비교할 거래번호 짝. {@code line}은 입력 원문의 줄 번호(1부터). */
@Value
public class TransactionPair {
    int line;
    String a;
    String b;
}
