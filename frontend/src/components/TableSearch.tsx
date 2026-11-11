// FE-02·FE-05: 테이블 검색 자동완성(SC01-04, 기술 설계서 6.2절). 모든 테이블을 선택할 수 있다(PRD PA-10).
// 2자 이상 입력하고 300ms 동안 추가 입력이 없을 때만 조회하며, 이전 요청은 취소한다.
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { errorMessage, messages } from '../messages';
import type { TableCandidate } from '../types';

export const SEARCH_DELAY_MS = 300;
export const MIN_QUERY_LENGTH = 2;

interface Props {
  env: string | null;
  envName: string;
  selected: { owner: string; name: string } | null;
  onSelect: (table: TableCandidate) => void;
}

export function TableSearch({ env, envName, selected, onSelect }: Props) {
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<TableCandidate[] | null>(null);
  const [truncated, setTruncated] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const controller = useRef<AbortController | null>(null);

  useEffect(() => {
    const q = query.trim();
    controller.current?.abort();
    controller.current = null;
    if (!env || q.length < MIN_QUERY_LENGTH) {
      setItems(null);
      setError(null);
      setLoading(false);
      return;
    }
    const timer = setTimeout(() => {
      const ac = new AbortController();
      controller.current = ac;
      setLoading(true);
      api
        .searchTables(env, q, ac.signal)
        .then((res) => {
          if (ac.signal.aborted) return;
          setItems(res.items);
          setTruncated(res.truncated);
          setError(null);
        })
        .catch((e: unknown) => {
          if (ac.signal.aborted || (e instanceof DOMException && e.name === 'AbortError')) return;
          setItems(null);
          setError(errorMessage(e, envName));
        })
        .finally(() => {
          if (!ac.signal.aborted) setLoading(false);
        });
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [env, query, envName]);

  useEffect(() => () => controller.current?.abort(), []);

  const choose = (item: TableCandidate) => {
    onSelect(item);
    setQuery(`${item.owner}.${item.name}`);
    setOpen(false);
  };

  return (
    <div className="row table-search">
      <span className="lbl">테이블</span>
      <div className="search-box">
        <input
          type="text"
          aria-label="테이블 검색"
          placeholder="스키마.테이블 검색"
          disabled={!env}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
        {open && (loading || items || error) && (
          <div className="candidates" role="listbox" aria-label="테이블 후보">
            {loading && <div className="cand-note">조회 중...</div>}
            {error && (
              <div className="cand-note err" role="alert">
                {error}
              </div>
            )}
            {!loading && items && items.length === 0 && (
              <div className="cand-note">
                {messages.noMatchingTables}
                <div className="hint">{messages.noTables}</div>
              </div>
            )}
            {!loading &&
              items?.map((item) => (
                <div
                  key={`${item.owner}.${item.name}`}
                  role="option"
                  aria-selected={false}
                  className="cand"
                  onClick={() => choose(item)}
                >
                  <span>
                    {item.owner}.{item.name}
                  </span>
                </div>
              ))}
            {!loading && truncated && items && items.length > 0 && (
              <div className="cand-note">{messages.tooManyCandidates}</div>
            )}
          </div>
        )}
      </div>
      {selected && (
        <span className="pk">
          선택: <b>{`${selected.owner}.${selected.name}`}</b>
        </span>
      )}
    </div>
  );
}
