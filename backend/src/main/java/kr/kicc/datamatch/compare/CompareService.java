package kr.kicc.datamatch.compare;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import kr.kicc.datamatch.config.DataMatchProperties;
import kr.kicc.datamatch.error.DataMatchException;
import kr.kicc.datamatch.safedb.DecryptSession;
import kr.kicc.datamatch.safedb.Decryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 비교 실행(기술 설계서 3.3절, 6.3절, 7장, TD-08·14). 쌍을 청크(기본 100쌍)로 나누어 청크마다 A쪽 키는 A 환경, B쪽 키는 B 환경에서
 * 한 번씩 조회하고, 입력 순서대로 매칭해 {@link CompareEngine}으로 비교한다. 복호화는 이 워커 스레드에서 순차로 한다(TA-11).
 *
 * <ul>
 *   <li>처리 건수는 청크가 끝날 때마다 실제로 증가한다.</li>
 *   <li>중지는 청크 시작 전과 복호화 행마다 확인하고, 실행 중인 쿼리는 {@link CancellationToken}이 취소한다.</li>
 *   <li>DB 오류는 실패한 쪽과 환경을 담은 {@link CompareFailedException}이 되며, 결과는 끝까지 성공했을 때만 돌려준다.</li>
 * </ul>
 * 로그에는 환경 ID, 소유자.테이블, 쌍 수, 청크 처리 시간, 오류 코드만 남긴다(TD-20).
 */
@Service
public class CompareService implements CompareRunner {

    private static final Logger log = LoggerFactory.getLogger(CompareService.class);

    private final RowRepository rowRepository;
    private final Decryptor decryptor;
    private final int chunkSize;

    @Autowired
    public CompareService(RowRepository rowRepository, Decryptor decryptor, DataMatchProperties properties) {
        this(rowRepository, decryptor, properties.getCompare().getChunkSize());
    }

    CompareService(RowRepository rowRepository, Decryptor decryptor, int chunkSize) {
        this.rowRepository = rowRepository;
        this.decryptor = decryptor;
        this.chunkSize = chunkSize;
    }

    @Override
    public List<PairResult> run(CompareTask task, IntConsumer progress, CancellationToken token) {
        List<String> names = task.getColumns().stream().map(ColumnSpec::getName).collect(Collectors.toList());
        RowQuery query = SqlBuilder.build(task.getTable(), task.getKeyColumn(), names);
        DecryptSession session = cancellable(decryptor.openSession(), token);
        List<TransactionPair> pairs = task.getPairs();
        List<PairResult> results = new ArrayList<>(pairs.size());
        String table = task.getTable().getOwner() + "." + task.getTable().getName();

        for (int from = 0; from < pairs.size(); from += chunkSize) {
            if (token.isCancelled()) {
                throw new QueryCancelledException();
            }
            long start = System.nanoTime();
            List<TransactionPair> chunk = pairs.subList(from, Math.min(from + chunkSize, pairs.size()));
            Map<String, List<Map<String, String>>> rowsA = fetch(Side.A, task.getEnvA(), query,
                    chunk.stream().map(TransactionPair::getA).collect(Collectors.toList()), token);
            Map<String, List<Map<String, String>>> rowsB = fetch(Side.B, task.getEnvB(), query,
                    chunk.stream().map(TransactionPair::getB).collect(Collectors.toList()), token);
            for (int i = 0; i < chunk.size(); i++) {
                TransactionPair pair = chunk.get(i);
                List<Map<String, String>> a = rowsA.getOrDefault(pair.getA(), List.of());
                List<Map<String, String>> b = rowsB.getOrDefault(pair.getB(), List.of());
                if (a.size() > 1 || b.size() > 1) {
                    // 키 중복은 누락보다 먼저 판정하고 컬럼 비교·복호화를 하지 않는다(PRD 6.1, PA-10).
                    results.add(PairResult.duplicate(from + i + 1, pair, a.size(), b.size()));
                } else {
                    results.add(CompareEngine.compare(from + i + 1, pair, task.getColumns(),
                            a.isEmpty() ? null : a.get(0), b.isEmpty() ? null : b.get(0), session));
                }
            }
            progress.accept(from + chunk.size());
            log.info("chunk done: envA={}, envB={}, table={}, pairs={}, elapsedMs={}", task.getEnvA(), task.getEnvB(), table,
                    chunk.size(), (System.nanoTime() - start) / 1_000_000);
        }
        if (token.isCancelled()) {
            throw new QueryCancelledException();
        }
        return results;
    }

    private Map<String, List<Map<String, String>>> fetch(Side side, String env, RowQuery query, List<String> keys,
                                                         CancellationToken token) {
        try {
            return rowRepository.fetchRows(env, query, new ArrayList<>(new LinkedHashSet<>(keys)), token);
        } catch (DataMatchException e) {
            log.warn("compare failed: side={}, env={}, code={}", side, env, e.code());
            throw new CompareFailedException(e.code(), side, env);
        }
    }

    /** 복호화 행마다 중지 요청을 확인한다. SafeDB 호출 하나는 중간에 끊지 않는다(기술 설계서 7.3절). */
    private static DecryptSession cancellable(DecryptSession session, CancellationToken token) {
        return (cipherText, policy) -> {
            if (token.isCancelled()) {
                throw new QueryCancelledException();
            }
            return session.decrypt(cipherText, policy);
        };
    }
}
