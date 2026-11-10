// DataMatch 단일 페이지(SC-01~SC-03). 상태는 useReducer로 메모리에만 두고 새로고침하면 초기화된다(SA-09, TD-18).
import { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { ApiError, NetworkError, api } from './api/client';
import { ColumnTable } from './components/ColumnTable';
import { EnvBar } from './components/EnvBar';
import { PairsInput, PairsStatus } from './components/PairsInput';
import { ProgressModal } from './components/ProgressModal';
import { ResultView } from './components/ResultView';
import { TableSearch } from './components/TableSearch';
import { errorMessage, messages, pairErrorMessage } from './messages';
import { buildCompareRequest, initialState, pkType, reducer } from './state/reducer';
import type { Environment, JobStatusResponse, PairErrorReason } from './types';
import { MAX_PAIRS, isNumericPkType, parsePairs } from './utils/pairsParser';

/** 진행 상태 조회 간격(기술 설계서 7.2절). */
export const POLL_INTERVAL_MS = 1000;

interface ModalState {
  cancelling: boolean;
  error: string | null;
}

/** 서버 입력 검증 오류(400 INVALID_INPUT)의 details를 화면 문구로 바꾼다. */
function serverInputErrors(details: unknown): { lines: string[]; limitCount: number | null } {
  const lines: string[] = [];
  let limitCount: number | null = null;
  if (Array.isArray(details)) {
    for (const d of details as { line?: number; reason?: string; count?: number }[]) {
      if (d.reason === 'PAIR_LIMIT_EXCEEDED') limitCount = d.count ?? null;
      else if (typeof d.line === 'number' && d.reason)
        lines.push(pairErrorMessage(d.line, d.reason as PairErrorReason));
    }
  }
  return { lines, limitCount };
}

export default function App() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [envError, setEnvError] = useState<string | null>(null);
  const [columnsLoading, setColumnsLoading] = useState(false);
  const [columnsError, setColumnsError] = useState<string | null>(null);
  const [runMessage, setRunMessage] = useState<string | null>(null);
  const [serverLines, setServerLines] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const [modal, setModal] = useState<ModalState | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const requestedDetails = useRef(new Set<string>());
  const currentJobId = useRef<string | null>(null);
  currentJobId.current = state.result?.jobId ?? null;

  const envName = (id: string | null) => environments.find((e) => e.id === id)?.name ?? id ?? '';

  useEffect(() => {
    api
      .environments()
      .then((list) => setEnvironments(list))
      .catch((e: unknown) => setEnvError(errorMessage(e)));
  }, []);

  // 테이블 선택 또는 A쪽 환경 변경 시 A쪽 환경에서 컬럼을 조회한다(PA-15, WA-05).
  useEffect(() => {
    if (!state.columnsStale || !state.table || !state.envA) return;
    let cancelled = false;
    const env = state.envA;
    setColumnsLoading(true);
    setColumnsError(null);
    api
      .columns(env, state.table.owner, state.table.name)
      .then((res) => {
        if (!cancelled)
          dispatch({ type: 'COLUMNS_LOADED', pkColumn: res.pkColumn, columns: res.columns });
      })
      .catch((e: unknown) => {
        if (!cancelled) setColumnsError(errorMessage(e, envName(env)));
      })
      .finally(() => {
        if (!cancelled) setColumnsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.columnsStale, state.table, state.envA]);

  const numericPk = isNumericPkType(pkType(state));
  const parsed = useMemo(
    () => parsePairs(state.pairsText, numericPk),
    [state.pairsText, numericPk],
  );

  const failureMessage = (s: JobStatusResponse): string => {
    if (s.errorCode === 'DB_CONNECTION_FAILED' && s.failedSide && s.failedEnv) {
      return messages.compareConnectionFailed(s.failedSide, envName(s.failedEnv));
    }
    if (s.errorCode === 'QUERY_TIMEOUT') return messages.queryTimeout;
    return messages.unexpected;
  };

  // 1초마다 상태를 조회한다. 진행률은 서버 값만 쓴다(TD-13).
  const job = state.job;
  const modalError = modal?.error ?? null;
  useEffect(() => {
    if (!job || modalError) return;
    let stopped = false;
    const timer = setTimeout(async () => {
      try {
        const s = await api.jobStatus(job.jobId);
        if (stopped) return;
        if (s.status === 'COMPLETED') {
          const summary = await api.summary(job.jobId);
          if (stopped) return;
          dispatch({ type: 'RESULT_LOADED', jobId: job.jobId, summary });
          setModal(null);
        } else if (s.status === 'CANCELLED') {
          dispatch({ type: 'JOB_ENDED' });
          setModal(null);
          setNotice(messages.cancelled);
        } else if (s.status === 'FAILED') {
          setModal({ cancelling: false, error: failureMessage(s) });
        } else {
          dispatch({
            type: 'JOB_PROGRESS',
            status: s.status,
            processed: s.processed,
            total: s.total,
          });
        }
      } catch (e) {
        if (!stopped) setModal({ cancelling: false, error: errorMessage(e) });
      }
    }, POLL_INTERVAL_MS);
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [job, modalError]);

  // 선택한 짝의 상세를 처음 선택할 때만 조회한다(TD-15).
  useEffect(() => {
    const result = state.result;
    const index = state.selectedIndex;
    if (!result || index === null || state.details[index]) return;
    const key = `${result.jobId}:${index}`;
    if (requestedDetails.current.has(key)) return;
    requestedDetails.current.add(key);
    setDetailError(null);
    api
      .pairDetail(result.jobId, index)
      .then((detail) => {
        if (currentJobId.current === result.jobId) dispatch({ type: 'DETAIL_LOADED', detail });
      })
      .catch((e: unknown) => {
        requestedDetails.current.delete(key);
        if (currentJobId.current === result.jobId) setDetailError(errorMessage(e));
      });
  }, [state.result, state.selectedIndex, state.details]);

  const canRun = !!state.envA && !!state.envB && !!state.table && !state.job && !starting;

  const run = async () => {
    setRunMessage(null);
    setServerLines([]);
    setNotice(null);
    const request = buildCompareRequest(state);
    if (!request) {
      setRunMessage(messages.envNotSelected);
      return;
    }
    if (request.columns.length === 0) return setRunMessage(messages.noColumns);
    if (parsed.pairs.length === 0 && parsed.errors.length === 0)
      return setRunMessage(messages.noPairs);
    if (parsed.errors.length > 0) return setRunMessage(messages.inputHasErrors);
    if (parsed.limitExceeded) return setRunMessage(messages.pairLimitExceeded(parsed.pairs.length));
    setStarting(true);
    try {
      const res = await api.startCompare(request);
      dispatch({ type: 'JOB_STARTED', jobId: res.jobId, total: res.total });
      setModal({ cancelling: false, error: null });
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_INPUT') {
        const { lines, limitCount } = serverInputErrors(e.details);
        if (limitCount !== null) setRunMessage(messages.pairLimitExceeded(limitCount));
        else if (lines.length > 0) setRunMessage(messages.inputHasErrors);
        else setRunMessage(messages.unexpected);
        setServerLines(lines);
      } else {
        setRunMessage(errorMessage(e, envName(state.envA)));
      }
    } finally {
      setStarting(false);
    }
  };

  const cancel = async () => {
    if (!state.job) return;
    setModal({ cancelling: true, error: null });
    try {
      await api.cancel(state.job.jobId);
    } catch (e) {
      setModal({ cancelling: false, error: errorMessage(e) });
    }
  };

  const closeModal = () => {
    dispatch({ type: 'JOB_ENDED' });
    setModal(null);
  };

  const download = async () => {
    if (!state.result) return;
    setDownloading(true);
    setDownloadError(null);
    try {
      const { blob, filename } = await api.excel(state.result.jobId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setDownloadError(
        e instanceof NetworkError ? messages.serverUnreachable : messages.excelFailed,
      );
    } finally {
      setDownloading(false);
    }
  };

  return (
    <>
      <EnvBar
        environments={environments}
        envA={state.envA}
        envB={state.envB}
        onChangeA={(env) => dispatch({ type: 'SET_ENV_A', env })}
        onChangeB={(env) => dispatch({ type: 'SET_ENV_B', env })}
        error={envError}
      />
      <main>
        <section className="card" aria-label="거래 비교 설정">
          <h2>
            거래 비교 설정 <span className="sc">SC-01</span>
          </h2>
          {notice && (
            <div className="notice" role="status">
              {notice}
            </div>
          )}
          <TableSearch
            env={state.envA}
            envName={envName(state.envA)}
            selected={state.table}
            onSelect={(table) => dispatch({ type: 'SELECT_TABLE', table })}
          />
          <ColumnTable
            columns={state.columns}
            selected={state.selected}
            decrypt={state.decrypt}
            loading={columnsLoading}
            error={columnsError}
            enabled={!!state.table}
            dispatch={dispatch}
          />
          <div className="split">
            <PairsInput
              text={state.pairsText}
              parsed={parsed}
              disabled={!state.table}
              onChange={(text) => dispatch({ type: 'SET_PAIRS_TEXT', text })}
            />
            <div className="right">
              <PairsStatus parsed={parsed} />
              <div className="hint">
                최대 {MAX_PAIRS.toLocaleString('ko-KR')}쌍까지 입력할 수 있습니다.
              </div>
              {(!state.envA || !state.envB) && (
                <div className="hint">{messages.envNotSelected}</div>
              )}
              {runMessage && (
                <div className="errbox" role="alert" aria-label="비교 실행 안내">
                  {runMessage}
                  {serverLines.length > 0 && (
                    <ul>
                      {serverLines.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
              <div className="actions">
                <button type="button" className="btn primary" disabled={!canRun} onClick={run}>
                  비교 실행
                </button>
              </div>
            </div>
          </div>
        </section>
        {state.result && (
          <ResultView
            summary={state.result.summary}
            selectedIndex={state.selectedIndex}
            detail={state.selectedIndex !== null ? state.details[state.selectedIndex] : undefined}
            detailError={detailError}
            envName={envName}
            downloading={downloading}
            downloadError={downloadError}
            onSelect={(index) => dispatch({ type: 'SELECT_PAIR', index })}
            onDownload={download}
          />
        )}
      </main>
      {state.job && modal && (
        <ProgressModal
          processed={state.job.processed}
          total={state.job.total}
          cancelling={modal.cancelling}
          error={modal.error}
          onCancel={cancel}
          onClose={closeModal}
        />
      )}
    </>
  );
}
