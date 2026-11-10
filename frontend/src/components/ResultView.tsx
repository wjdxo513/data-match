// FE-04: 비교 결과(SC-03). 비교 당시 조건을 서버 결과 그대로 표시하고(SA-09), 상세는 선택 시 조회한 값을 쓴다(TD-15).
import { useState } from 'react';
import { decryptErrorMessage, missingMessage, statusLabels } from '../messages';
import type {
  CompareSummary,
  DetailRow,
  MissingSide,
  PairDetail,
  Side,
  SummaryItem,
} from '../types';

/** 이 길이를 넘는 값은 접어서 보여준다(화면 정의서 4장 긴 데이터). */
export const COLLAPSE_LENGTH = 120;

interface Props {
  summary: CompareSummary;
  selectedIndex: number | null;
  detail: PairDetail | undefined;
  detailError: string | null;
  envName: (id: string) => string;
  downloading: boolean;
  downloadError: string | null;
  onSelect: (index: number) => void;
  onDownload: () => void;
}

function missingLabel(missing: MissingSide | null): string {
  if (missing === 'BOTH') return '누락(A·B 모두 없음)';
  return `누락(${missing} 없음)`;
}

export function pairLabel(item: Pick<SummaryItem, 'status' | 'mismatchCount' | 'missing'>): string {
  if (item.status === 'MATCH') return statusLabels.MATCH;
  if (item.status === 'MISMATCH') return `${statusLabels.MISMATCH}(${item.mismatchCount})`;
  return missingLabel(item.missing);
}

const pillClass = {
  MATCH: 'pill match',
  MISMATCH: 'pill mismatch',
  MISSING: 'pill missing',
} as const;

function Value({ value }: { value: string | null }) {
  const [expanded, setExpanded] = useState(false);
  if (value === null) return <span className="null-value">(NULL)</span>;
  if (value.length <= COLLAPSE_LENGTH) return <span className="val-text">{value}</span>;
  return (
    <span className="val-text">
      {expanded ? value : `${value.slice(0, COLLAPSE_LENGTH)}…`}{' '}
      <button type="button" className="link" onClick={() => setExpanded(!expanded)}>
        {expanded ? '접기' : '펼치기'}
      </button>
    </span>
  );
}

function DetailTable({ detail, envName }: { detail: PairDetail; envName: (side: Side) => string }) {
  return (
    <table aria-label="상세 비교">
      <thead>
        <tr>
          <th>컬럼·필드</th>
          <th>A값({envName('A')})</th>
          <th>B값({envName('B')})</th>
          <th>상태</th>
        </tr>
      </thead>
      <tbody>
        {detail.rows.map((row: DetailRow, i) => (
          <tr
            key={`${row.name}-${i}`}
            className={row.status === 'MATCH' ? undefined : 'row-mismatch'}
          >
            <td className="fld">{row.name}</td>
            {row.status === 'DECRYPT_ERROR' ? (
              <td className="val" colSpan={2}>
                {decryptErrorMessage(row.message)}
              </td>
            ) : (
              <>
                <td className="val">
                  <Value value={row.a} />
                </td>
                <td className="val">
                  <Value value={row.b} />
                </td>
              </>
            )}
            <td>
              <span className={row.status === 'MATCH' ? 'st match' : 'st mismatch'}>
                {statusLabels[row.status]}
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function ResultView(props: Props) {
  const { summary, selectedIndex, detail } = props;
  const { condition, counts } = summary;
  const sideName = (side: Side) => props.envName(side === 'A' ? condition.envA : condition.envB);
  const selectedItem = summary.items.find((i) => i.index === selectedIndex);

  return (
    <section className="card" aria-label="비교 결과">
      <div className="resulthead">
        <h2>
          비교 결과 <span className="sc">SC-03</span>
        </h2>
        <button
          type="button"
          className="btn"
          disabled={props.downloading}
          onClick={props.onDownload}
        >
          {props.downloading ? '엑셀 생성 중...' : '엑셀 다운로드'}
        </button>
      </div>
      {props.downloadError && (
        <div className="errbox" role="alert">
          {props.downloadError}
        </div>
      )}
      <div className="cond" data-testid="result-condition">
        <span className="a">A {sideName('A')}</span>
        <span className="b">B {sideName('B')}</span>
        {condition.owner}.{condition.table} · 비교 컬럼 {condition.columns.length}개
      </div>
      <div className="summary" data-testid="result-counts">
        총 <b>{counts.total}</b>쌍 · 일치 <b>{counts.match}</b> · 불일치 <b>{counts.mismatch}</b> ·
        누락 <b>{counts.missing}</b>
      </div>
      <div className="result-split">
        <div className="list" role="listbox" aria-label="요약 목록">
          {summary.items.map((item) => (
            <div
              key={item.index}
              role="option"
              aria-selected={item.index === selectedIndex}
              className={item.index === selectedIndex ? 'item sel' : 'item'}
              onClick={() => props.onSelect(item.index)}
            >
              <span className="no">{item.index}</span>
              <span className="pair">
                {item.a} - {item.b}
              </span>
              <span className={pillClass[item.status]}>{pairLabel(item)}</span>
            </div>
          ))}
        </div>
        <div className="detail">
          {selectedItem && (
            <h3>
              상세 · {selectedItem.a} (A {sideName('A')}) - {selectedItem.b} (B {sideName('B')})
            </h3>
          )}
          {props.detailError && (
            <div className="errbox" role="alert">
              {props.detailError}
            </div>
          )}
          {detail && detail.status === 'MISSING' && detail.missing && (
            <div className="missing-note" role="status">
              {missingMessage(detail.missing, sideName)}
            </div>
          )}
          {detail && detail.status !== 'MISSING' && (
            <DetailTable detail={detail} envName={sideName} />
          )}
        </div>
      </div>
    </section>
  );
}
