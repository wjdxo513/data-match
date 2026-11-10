// FE-02: 거래번호 입력(SC01-10~12). 입력 중 유효한 쌍 수와 오류 줄을 즉시 표시한다. 최종 검증은 서버가 한다.
import { messages, pairErrorMessage } from '../messages';
import type { PairsParseResult } from '../utils/pairsParser';

interface Props {
  text: string;
  parsed: PairsParseResult;
  disabled: boolean;
  onChange: (text: string) => void;
}

export function PairsInput({ text, parsed, disabled, onChange }: Props) {
  const hasErrors = parsed.errors.length > 0;
  return (
    <div className="left">
      <div className="row" style={{ marginBottom: 6 }}>
        <span className="lbl">거래번호</span>
        <span className="hint">한 줄에 거래번호A,거래번호B (쉼표 또는 탭 구분)</span>
      </div>
      <textarea
        aria-label="거래번호 입력"
        className={hasErrors ? 'err' : undefined}
        value={text}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
      {hasErrors && (
        <div className="errbox" role="alert" aria-label="입력 오류">
          <ul>
            {parsed.errors.map((e) => (
              <li key={e.line}>{pairErrorMessage(e.line, e.reason, e.side)}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function PairsStatus({ parsed }: { parsed: PairsParseResult }) {
  return (
    <>
      <div className={parsed.limitExceeded ? 'stat over' : 'stat'} data-testid="pairs-status">
        {messages.inputStatus(parsed.pairs.length)}
      </div>
      <div className={parsed.errors.length > 0 ? 'stat over' : 'stat'} data-testid="pairs-errors">
        {messages.errorLineCount(parsed.errors.length)}
      </div>
    </>
  );
}
