// FE-01: useReducer 기반 화면 상태와 액션(기술 설계서 TD-18).
// 입력값은 메모리에만 두며 새로고침하면 초기화된다(화면 정의서 SA-09, 4장 입력값 유지).
import type {
  ColumnInfo,
  CompareRequest,
  CompareSummary,
  DecryptPolicy,
  JobStatus,
  PairDetail,
  TableCandidate,
} from '../types';

export interface SelectedTable {
  owner: string;
  name: string;
  pkColumn: string | null;
}

export interface JobState {
  jobId: string;
  status: JobStatus;
  processed: number;
  total: number;
}

export interface AppState {
  envA: string | null;
  envB: string | null;
  table: SelectedTable | null;
  columns: ColumnInfo[];
  pkColumn: string | null;
  /** 컬럼 재조회가 필요하면 true(A쪽 환경 변경 또는 새 테이블 선택, WA-05). 컬럼을 받으면 false. */
  columnsStale: boolean;
  /** 선택한 컬럼명. 요청은 컬럼 목록 순서로 만든다. */
  selected: string[];
  decrypt: Record<string, DecryptPolicy>;
  pairsText: string;
  job: JobState | null;
  /** 표시 중인 결과. 비교 조건은 서버가 보관한 값을 그대로 쓴다(SA-09). */
  result: { jobId: string; summary: CompareSummary } | null;
  selectedIndex: number | null;
  details: Record<number, PairDetail>;
}

export const initialState: AppState = {
  envA: null,
  envB: null,
  table: null,
  columns: [],
  pkColumn: null,
  columnsStale: false,
  selected: [],
  decrypt: {},
  pairsText: '',
  job: null,
  result: null,
  selectedIndex: null,
  details: {},
};

export type Action =
  | { type: 'SET_ENV_A'; env: string | null }
  | { type: 'SET_ENV_B'; env: string | null }
  | { type: 'SELECT_TABLE'; table: TableCandidate }
  | { type: 'COLUMNS_LOADED'; pkColumn: string; columns: ColumnInfo[] }
  | { type: 'SET_COLUMN_SELECTED'; name: string; selected: boolean }
  | { type: 'SET_COLUMNS_SELECTED'; names: string[]; selected: boolean }
  | { type: 'SET_DECRYPT'; name: string; policy: DecryptPolicy | null }
  | { type: 'SET_PAIRS_TEXT'; text: string }
  | { type: 'JOB_STARTED'; jobId: string; total: number }
  | { type: 'JOB_PROGRESS'; status: JobStatus; processed: number; total: number }
  | { type: 'JOB_ENDED' }
  | { type: 'RESULT_LOADED'; jobId: string; summary: CompareSummary }
  | { type: 'SELECT_PAIR'; index: number }
  | { type: 'DETAIL_LOADED'; detail: PairDetail };

const isClob = (columns: ColumnInfo[], name: string) =>
  columns.some((c) => c.name === name && c.clob);

function withoutKeys(
  map: Record<string, DecryptPolicy>,
  keep: (name: string) => boolean,
): Record<string, DecryptPolicy> {
  return Object.fromEntries(Object.entries(map).filter(([name]) => keep(name)));
}

export function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'SET_ENV_A':
      // 테이블 선택은 유지하고 새 환경에서 컬럼을 다시 조회한다(WA-05).
      return {
        ...state,
        envA: action.env,
        columnsStale: state.table !== null && action.env !== null,
      };
    case 'SET_ENV_B':
      return { ...state, envB: action.env };
    case 'SELECT_TABLE':
      // 새 테이블을 고르면 컬럼 선택과 복호화 지정을 초기화한다(화면 정의서 4장).
      return {
        ...state,
        table: {
          owner: action.table.owner,
          name: action.table.name,
          pkColumn: action.table.pkColumn,
        },
        columns: [],
        pkColumn: null,
        columnsStale: true,
        selected: [],
        decrypt: {},
      };
    case 'COLUMNS_LOADED': {
      // 재조회 결과에 없는 컬럼, CLOB이 아니게 된 컬럼의 선택·복호화 지정은 지운다.
      const names = new Set(action.columns.map((c) => c.name));
      const selected = state.selected.filter((n) => names.has(n));
      return {
        ...state,
        columns: action.columns,
        pkColumn: action.pkColumn,
        columnsStale: false,
        selected,
        decrypt: withoutKeys(
          state.decrypt,
          (n) => selected.includes(n) && isClob(action.columns, n),
        ),
      };
    }
    case 'SET_COLUMN_SELECTED':
      return setSelected(state, [action.name], action.selected);
    case 'SET_COLUMNS_SELECTED':
      // 전체 선택/해제는 현재 목록에 보이는 컬럼(names)에만 적용한다(WA-04).
      return setSelected(state, action.names, action.selected);
    case 'SET_DECRYPT': {
      if (action.policy === null) {
        return { ...state, decrypt: withoutKeys(state.decrypt, (n) => n !== action.name) };
      }
      // 복호화는 비교 컬럼으로 선택된 CLOB 컬럼에서만 지정할 수 있다(SC01-09).
      if (!state.selected.includes(action.name) || !isClob(state.columns, action.name)) {
        return state;
      }
      return { ...state, decrypt: { ...state.decrypt, [action.name]: action.policy } };
    }
    case 'SET_PAIRS_TEXT':
      return { ...state, pairsText: action.text };
    case 'JOB_STARTED':
      return {
        ...state,
        job: { jobId: action.jobId, status: 'RUNNING', processed: 0, total: action.total },
      };
    case 'JOB_PROGRESS':
      if (!state.job) return state;
      return {
        ...state,
        job: {
          ...state.job,
          status: action.status,
          processed: action.processed,
          total: action.total,
        },
      };
    case 'JOB_ENDED':
      // 실패·중지·통신 불가로 끝나면 진행 상태만 지우고 입력값과 이전 결과는 유지한다(SA-07, SA-08).
      return { ...state, job: null };
    case 'RESULT_LOADED':
      // 새 결과로 이전 결과를 대체하고 첫 짝을 선택한다(SA-08, SC03-04).
      return {
        ...state,
        job: null,
        result: { jobId: action.jobId, summary: action.summary },
        selectedIndex: action.summary.items.length > 0 ? action.summary.items[0].index : null,
        details: {},
      };
    case 'SELECT_PAIR':
      return { ...state, selectedIndex: action.index };
    case 'DETAIL_LOADED':
      return { ...state, details: { ...state.details, [action.detail.index]: action.detail } };
  }
}

function setSelected(state: AppState, names: string[], selected: boolean): AppState {
  if (selected) {
    const add = names.filter(
      (n) => !state.selected.includes(n) && state.columns.some((c) => c.name === n),
    );
    return { ...state, selected: [...state.selected, ...add] };
  }
  // 선택 해제한 컬럼은 복호화 지정도 함께 해제한다(WA-04).
  const removed = new Set(names);
  return {
    ...state,
    selected: state.selected.filter((n) => !removed.has(n)),
    decrypt: withoutKeys(state.decrypt, (n) => !removed.has(n)),
  };
}

/** 현재 입력으로 비교 요청을 만든다. 컬럼은 컬럼 목록 순서를 따른다. 필수 값이 없으면 null. */
export function buildCompareRequest(state: AppState): CompareRequest | null {
  if (!state.envA || !state.envB || !state.table) return null;
  return {
    envA: state.envA,
    envB: state.envB,
    owner: state.table.owner,
    table: state.table.name,
    columns: state.columns
      .filter((c) => state.selected.includes(c.name))
      .map((c) =>
        state.decrypt[c.name]
          ? { name: c.name, decrypt: { policy: state.decrypt[c.name] } }
          : { name: c.name },
      ),
    pairsText: state.pairsText,
  };
}

/** PK 컬럼 타입(숫자 PK 검사용, PA-19). */
export function pkType(state: AppState): string | null {
  return state.columns.find((c) => c.name === state.pkColumn)?.type ?? null;
}
