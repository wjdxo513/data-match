// FE-02: 컬럼 표(SC01-06~09). 검색은 이미 받은 목록에서만 하고, 전체 선택/해제는 보이는 컬럼에만 적용한다(WA-04).
// 복호화는 비교 컬럼으로 선택된 CLOB 컬럼에서만 지정할 수 있다(FR-04).
import { useState } from 'react';
import type { Action } from '../state/reducer';
import type { ColumnInfo, DecryptPolicy } from '../types';

interface Props {
  columns: ColumnInfo[];
  selected: string[];
  decrypt: Record<string, DecryptPolicy>;
  loading: boolean;
  error: string | null;
  enabled: boolean;
  dispatch: (action: Action) => void;
}

export function ColumnTable({
  columns,
  selected,
  decrypt,
  loading,
  error,
  enabled,
  dispatch,
}: Props) {
  const [filter, setFilter] = useState('');
  const visible = columns.filter((c) => c.name.toUpperCase().includes(filter.trim().toUpperCase()));
  const names = visible.map((c) => c.name);

  return (
    <>
      <div className="row">
        <span className="lbl">컬럼</span>
        <input
          type="text"
          aria-label="컬럼 검색"
          placeholder="컬럼 검색"
          value={filter}
          disabled={!enabled}
          onChange={(e) => setFilter(e.target.value)}
        />
        <button
          type="button"
          className="btn"
          disabled={!enabled || columns.length === 0}
          onClick={() => dispatch({ type: 'SET_COLUMNS_SELECTED', names, selected: true })}
        >
          전체 선택
        </button>
        <button
          type="button"
          className="btn"
          disabled={!enabled || columns.length === 0}
          onClick={() => dispatch({ type: 'SET_COLUMNS_SELECTED', names, selected: false })}
        >
          전체 해제
        </button>
      </div>
      {loading && <div className="hint">컬럼 정보를 조회하는 중입니다...</div>}
      {error && (
        <div className="errbox" role="alert">
          {error}
        </div>
      )}
      {columns.length > 0 && (
        <table className="cols" aria-label="컬럼 목록">
          <thead>
            <tr>
              <th className="chk" />
              <th>컬럼명</th>
              <th>타입</th>
              <th>SafeDB 복호화</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((c) => {
              const isSelected = selected.includes(c.name);
              const policy = decrypt[c.name];
              return (
                <tr key={c.name}>
                  <td className="chk">
                    <input
                      type="checkbox"
                      aria-label={`${c.name} 선택`}
                      checked={isSelected}
                      onChange={(e) =>
                        dispatch({
                          type: 'SET_COLUMN_SELECTED',
                          name: c.name,
                          selected: e.target.checked,
                        })
                      }
                    />
                  </td>
                  <td>
                    {c.name}
                    {c.pk && <span className="tag">PK</span>}
                  </td>
                  <td>{c.type}</td>
                  <td>
                    {c.clob ? (
                      <span className="decrypt">
                        <label>
                          <input
                            type="checkbox"
                            aria-label={`${c.name} 복호화`}
                            disabled={!isSelected}
                            checked={!!policy}
                            onChange={(e) =>
                              dispatch({
                                type: 'SET_DECRYPT',
                                name: c.name,
                                policy: e.target.checked ? 'KDKEY' : null,
                              })
                            }
                          />{' '}
                          복호화 대상
                        </label>{' '}
                        정책{' '}
                        <select
                          aria-label={`${c.name} 복호화 정책`}
                          disabled={!policy}
                          value={policy ?? 'KDKEY'}
                          onChange={(e) =>
                            dispatch({
                              type: 'SET_DECRYPT',
                              name: c.name,
                              policy: e.target.value as DecryptPolicy,
                            })
                          }
                        >
                          <option value="KDKEY">KDKEY</option>
                          <option value="PANEN">PANEN</option>
                        </select>
                      </span>
                    ) : (
                      '-'
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </>
  );
}
