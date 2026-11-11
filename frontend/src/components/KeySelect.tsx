// FE-05: 비교 키 선택(SC01-05, 화면 정의서 WA-14). 후보는 문자형·NUMBER 컬럼(keyable)이고 PK에는 "(PK)"를 붙인다.
import type { ColumnInfo } from '../types';

interface Props {
  columns: ColumnInfo[];
  value: string | null;
  onChange: (keyColumn: string | null) => void;
}

export function KeySelect({ columns, value, onChange }: Props) {
  const candidates = columns.filter((c) => c.keyable);
  return (
    <div className="row">
      <span className="lbl">비교 키</span>
      <select
        aria-label="비교 키"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
      >
        <option value="">선택 안 함</option>
        {candidates.map((c) => (
          <option key={c.name} value={c.name}>
            {c.pk ? `${c.name} (PK)` : c.name}
          </option>
        ))}
      </select>
      <span className="hint">
        거래번호로 행을 찾을 컬럼입니다. 문자형·NUMBER 컬럼만 고를 수 있습니다.
      </span>
    </div>
  );
}
