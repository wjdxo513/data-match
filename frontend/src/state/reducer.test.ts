import type { ColumnInfo, CompareSummary } from '../types';
import {
  type Action,
  type AppState,
  buildCompareRequest,
  initialState,
  pkType,
  reducer,
} from './reducer';

const COLUMNS: ColumnInfo[] = [
  { name: 'TRX_NO', type: 'VARCHAR2(20)', pk: true, clob: false },
  { name: 'AMOUNT', type: 'NUMBER', pk: false, clob: false },
  { name: 'APPR_MSG', type: 'CLOB', pk: false, clob: true },
  { name: 'EXTRA_MSG', type: 'CLOB', pk: false, clob: true },
];

const TABLE = {
  owner: 'DM_IT_OWNER',
  name: 'DM_IT_TRX_STR',
  selectable: true,
  reason: null,
  pkColumn: 'TRX_NO',
};

const SUMMARY: CompareSummary = {
  condition: {
    envA: 'test',
    envB: 'prod',
    owner: 'DM_IT_OWNER',
    table: 'DM_IT_TRX_STR',
    columns: [{ name: 'AMOUNT' }, { name: 'APPR_MSG', decrypt: 'KDKEY' }],
    comparedAt: '2026-11-10T10:00:00',
  },
  counts: { total: 2, match: 1, mismatch: 1, missing: 0 },
  items: [
    { index: 1, a: 'A1', b: 'B1', status: 'MATCH', mismatchCount: 0, missing: null },
    { index: 2, a: 'A2', b: 'B2', status: 'MISMATCH', mismatchCount: 1, missing: null },
  ],
};

const run = (state: AppState, ...actions: Action[]) => actions.reduce(reducer, state);

function ready(): AppState {
  return run(
    initialState,
    { type: 'SET_ENV_A', env: 'test' },
    { type: 'SET_ENV_B', env: 'prod' },
    { type: 'SELECT_TABLE', table: TABLE },
    { type: 'COLUMNS_LOADED', pkColumn: 'TRX_NO', columns: COLUMNS },
    { type: 'SET_COLUMNS_SELECTED', names: ['AMOUNT', 'APPR_MSG', 'EXTRA_MSG'], selected: true },
    { type: 'SET_DECRYPT', name: 'APPR_MSG', policy: 'KDKEY' },
    { type: 'SET_DECRYPT', name: 'EXTRA_MSG', policy: 'PANEN' },
  );
}

describe('reducer', () => {
  it('A쪽 환경을 바꾸면 테이블 선택을 유지하고 컬럼 재조회 신호를 세운다(WA-05)', () => {
    const before = ready();
    expect(before.columnsStale).toBe(false);
    const after = reducer(before, { type: 'SET_ENV_A', env: 'dev' });
    expect(after.envA).toBe('dev');
    expect(after.table).toEqual(before.table);
    expect(after.columnsStale).toBe(true);
    expect(after.selected).toEqual(before.selected);
  });

  it('테이블이 없을 때 A쪽 환경을 바꾸면 재조회 신호를 세우지 않고, B쪽 환경은 컬럼에 영향이 없다', () => {
    expect(reducer(initialState, { type: 'SET_ENV_A', env: 'test' }).columnsStale).toBe(false);
    const after = reducer(ready(), { type: 'SET_ENV_B', env: 'dev' });
    expect(after.columnsStale).toBe(false);
    expect(after.columns).toBe(COLUMNS);
  });

  it('새 테이블을 선택하면 컬럼 선택과 복호화 지정을 초기화한다', () => {
    const after = reducer(ready(), {
      type: 'SELECT_TABLE',
      table: { ...TABLE, name: 'DM_IT_TRX_NUM' },
    });
    expect(after.table?.name).toBe('DM_IT_TRX_NUM');
    expect(after.selected).toEqual([]);
    expect(after.decrypt).toEqual({});
    expect(after.columns).toEqual([]);
    expect(after.columnsStale).toBe(true);
  });

  it('컬럼 선택을 해제하면 그 컬럼의 복호화 지정도 해제한다(WA-04)', () => {
    const one = reducer(ready(), {
      type: 'SET_COLUMN_SELECTED',
      name: 'APPR_MSG',
      selected: false,
    });
    expect(one.selected).toEqual(['AMOUNT', 'EXTRA_MSG']);
    expect(one.decrypt).toEqual({ EXTRA_MSG: 'PANEN' });
    const bulk = reducer(ready(), {
      type: 'SET_COLUMNS_SELECTED',
      names: ['EXTRA_MSG', 'AMOUNT'],
      selected: false,
    });
    expect(bulk.selected).toEqual(['APPR_MSG']);
    expect(bulk.decrypt).toEqual({ APPR_MSG: 'KDKEY' });
  });

  it('복호화는 선택된 CLOB 컬럼에만 지정되고 null로 해제된다', () => {
    const state = ready();
    expect(reducer(state, { type: 'SET_DECRYPT', name: 'AMOUNT', policy: 'KDKEY' })).toBe(state);
    const unselected = reducer(state, {
      type: 'SET_COLUMN_SELECTED',
      name: 'APPR_MSG',
      selected: false,
    });
    expect(
      reducer(unselected, { type: 'SET_DECRYPT', name: 'APPR_MSG', policy: 'KDKEY' }).decrypt,
    ).toEqual({
      EXTRA_MSG: 'PANEN',
    });
    expect(reducer(state, { type: 'SET_DECRYPT', name: 'APPR_MSG', policy: null }).decrypt).toEqual(
      {
        EXTRA_MSG: 'PANEN',
      },
    );
  });

  it('컬럼 재조회 결과에 없는 컬럼의 선택과 복호화 지정은 지운다', () => {
    const after = reducer(ready(), {
      type: 'COLUMNS_LOADED',
      pkColumn: 'TRX_NO',
      columns: COLUMNS.filter((c) => c.name !== 'APPR_MSG'),
    });
    expect(after.selected).toEqual(['AMOUNT', 'EXTRA_MSG']);
    expect(after.decrypt).toEqual({ EXTRA_MSG: 'PANEN' });
  });

  it('입력을 바꿔도 표시된 결과의 비교 조건은 바뀌지 않는다(SA-09)', () => {
    const withResult = run(
      ready(),
      { type: 'JOB_STARTED', jobId: 'j1', total: 2 },
      { type: 'RESULT_LOADED', jobId: 'j1', summary: SUMMARY },
    );
    const snapshot = JSON.parse(JSON.stringify(withResult.result));
    const changed = run(
      withResult,
      { type: 'SET_ENV_A', env: 'dev' },
      { type: 'SET_ENV_B', env: 'dev' },
      { type: 'SELECT_TABLE', table: { ...TABLE, name: 'OTHER' } },
      { type: 'SET_PAIRS_TEXT', text: 'X,Y' },
    );
    expect(changed.result).toEqual(snapshot);
    expect(changed.result).toBe(withResult.result);
  });

  it('결과를 받으면 진행 상태를 지우고 첫 짝을 선택하며 상세 캐시를 비운다(SA-08)', () => {
    let state = run(
      ready(),
      { type: 'JOB_STARTED', jobId: 'j1', total: 2 },
      { type: 'JOB_PROGRESS', status: 'RUNNING', processed: 2, total: 2 },
    );
    expect(state.job).toEqual({ jobId: 'j1', status: 'RUNNING', processed: 2, total: 2 });
    state = run(
      state,
      { type: 'RESULT_LOADED', jobId: 'j1', summary: SUMMARY },
      { type: 'SELECT_PAIR', index: 2 },
      {
        type: 'DETAIL_LOADED',
        detail: { index: 2, a: 'A2', b: 'B2', status: 'MISMATCH', missing: null, rows: [] },
      },
    );
    expect(state.job).toBeNull();
    expect(state.selectedIndex).toBe(2);
    expect(Object.keys(state.details)).toEqual(['2']);
    const next = reducer(state, {
      type: 'RESULT_LOADED',
      jobId: 'j2',
      summary: { ...SUMMARY, items: [] },
    });
    expect(next.details).toEqual({});
    expect(next.selectedIndex).toBeNull();
  });

  it('작업이 실패·중지로 끝나면 입력값과 이전 결과를 유지한다(SA-07)', () => {
    const withResult = run(ready(), { type: 'RESULT_LOADED', jobId: 'j1', summary: SUMMARY });
    const ended = run(
      withResult,
      { type: 'SET_PAIRS_TEXT', text: 'A1,B1' },
      { type: 'JOB_STARTED', jobId: 'j2', total: 1 },
      { type: 'JOB_ENDED' },
    );
    expect(ended.job).toBeNull();
    expect(ended.pairsText).toBe('A1,B1');
    expect(ended.result).toBe(withResult.result);
    expect(
      reducer(initialState, { type: 'JOB_PROGRESS', status: 'RUNNING', processed: 1, total: 1 }),
    ).toBe(initialState);
  });

  it('비교 요청을 컬럼 목록 순서와 복호화 지정으로 만든다', () => {
    const state = run(
      ready(),
      { type: 'SET_COLUMN_SELECTED', name: 'TRX_NO', selected: true },
      { type: 'SET_PAIRS_TEXT', text: 'A1,B1' },
    );
    expect(buildCompareRequest(state)).toEqual({
      envA: 'test',
      envB: 'prod',
      owner: 'DM_IT_OWNER',
      table: 'DM_IT_TRX_STR',
      columns: [
        { name: 'TRX_NO' },
        { name: 'AMOUNT' },
        { name: 'APPR_MSG', decrypt: { policy: 'KDKEY' } },
        { name: 'EXTRA_MSG', decrypt: { policy: 'PANEN' } },
      ],
      pairsText: 'A1,B1',
    });
    expect(buildCompareRequest(initialState)).toBeNull();
    expect(pkType(state)).toBe('VARCHAR2(20)');
  });
});
