package kr.kicc.datamatch.compare;

import lombok.Value;

/** 거래번호 입력의 줄 단위 오류. API 응답의 {@code details} 항목({@code line}, {@code reason})으로 쓴다. */
@Value
public class PairLineError {
    int line;
    PairErrorReason reason;
}
