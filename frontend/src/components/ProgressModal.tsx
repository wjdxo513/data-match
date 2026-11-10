// FE-03: 비교 진행 모달(SC-02). 진행률은 서버가 보고한 처리 건수만 표시한다(TD-13). 바깥 클릭으로 닫히지 않는다.
interface Props {
  processed: number;
  total: number;
  cancelling: boolean;
  error: string | null;
  onCancel: () => void;
  onClose: () => void;
}

export function ProgressModal({ processed, total, cancelling, error, onCancel, onClose }: Props) {
  const percent = total > 0 ? Math.floor((processed / total) * 100) : 0;
  const title = error ? '비교 실패' : cancelling ? '비교 중지 중' : '비교 진행 중';
  return (
    <div className="overlay" data-testid="progress-overlay">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h4>{title}</h4>
        {error ? (
          <div className="errbox" role="alert">
            {error}
          </div>
        ) : (
          <>
            <div
              className="bar"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
            >
              <i style={{ width: `${percent}%` }} />
            </div>
            <div className="meta">
              <span>
                처리 <b>{processed.toLocaleString('ko-KR')}</b> / 전체{' '}
                {total.toLocaleString('ko-KR')}쌍
              </span>
              <b>{percent}%</b>
            </div>
          </>
        )}
        <div className="foot">
          {error ? (
            <button type="button" className="btn" onClick={onClose}>
              닫기
            </button>
          ) : (
            <button type="button" className="btn danger" disabled={cancelling} onClick={onCancel}>
              {cancelling ? '비교 중지 중...' : '비교 중지'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
