package kr.kicc.datamatch.compare;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * 비교 작업의 중지 요청(기술 설계서 7.3절, TD-14). 실행 중인 {@link Statement}를 등록해 두면 중지 요청 시 {@code cancel()}을 호출한다.
 * 스레드 인터럽트는 쓰지 않는다.
 */
public final class CancellationToken {

    private boolean cancelled;
    private Statement current;

    public synchronized void cancel() {
        cancelled = true;
        if (current != null) {
            try {
                current.cancel();
            } catch (SQLException e) {
                // 이미 끝난 Statement 등. 중지 플래그는 서 있으므로 워커가 다음 확인 지점에서 멈춘다.
            }
        }
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** 실행할 Statement를 등록한다. 이미 중지 요청이 있으면 false를 돌려주고 등록하지 않는다. */
    synchronized boolean register(Statement statement) {
        if (cancelled) {
            return false;
        }
        current = statement;
        return true;
    }

    synchronized void unregister(Statement statement) {
        if (current == statement) {
            current = null;
        }
    }
}
