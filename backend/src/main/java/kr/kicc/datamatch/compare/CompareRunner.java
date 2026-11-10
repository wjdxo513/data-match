package kr.kicc.datamatch.compare;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * 비교 실행(BE-06의 CompareService가 구현). {@code JobManager}의 워커 스레드에서 호출된다.
 *
 * <ul>
 *   <li>처리 건수는 청크가 끝날 때마다 {@code progress}로 누적 값을 보고한다(TD-13).</li>
 *   <li>중지되면 {@link QueryCancelledException}을 던지거나 토큰을 확인하고 돌아온다.</li>
 *   <li>전체 실패는 {@link CompareFailedException}으로 알린다(PA-17).</li>
 * </ul>
 */
public interface CompareRunner {

    List<PairResult> run(CompareTask task, IntConsumer progress, CancellationToken token);
}
