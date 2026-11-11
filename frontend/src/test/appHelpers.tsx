// FE-02~04 테스트 공용 도우미. 각 테스트 파일은 vi.mock('./api/client', ...)로 api를 모의한 뒤 이 도우미를 쓴다.
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '../App';
import { api } from '../api/client';
import type { ColumnInfo, CompareSummary, Environment, PairDetail, TableCandidate } from '../types';

type ApiMock = { [K in keyof typeof api]: ReturnType<typeof vi.fn> };
export const mockedApi = api as unknown as ApiMock;

export const ENVS: Environment[] = [
  { id: 'dev', name: '개발', production: false },
  { id: 'test', name: '테스트', production: false },
  { id: 'prod', name: '운영', production: true },
];

export const TABLE: TableCandidate = { owner: 'DM_IT_OWNER', name: 'DM_IT_TRX_STR' };

export const COLUMNS: ColumnInfo[] = [
  { name: 'TRX_NO', type: 'VARCHAR2(20)', pk: true, clob: false, keyable: true },
  { name: 'ORDER_NO', type: 'VARCHAR2(40)', pk: false, clob: false, keyable: true },
  { name: 'AMOUNT', type: 'NUMBER(12)', pk: false, clob: false, keyable: true },
  { name: 'APPR_MSG', type: 'CLOB', pk: false, clob: true, keyable: false },
];

export const NUM_COLUMNS: ColumnInfo[] = [
  { name: 'TRX_ID', type: 'NUMBER(15)', pk: true, clob: false, keyable: true },
  { name: 'AMOUNT', type: 'NUMBER(12,2)', pk: false, clob: false, keyable: true },
];

export const SUMMARY: CompareSummary = {
  condition: {
    envA: 'test',
    envB: 'prod',
    owner: 'DM_IT_OWNER',
    table: 'DM_IT_TRX_STR',
    keyColumn: 'TRX_NO',
    columns: [{ name: 'AMOUNT' }, { name: 'APPR_MSG', decrypt: 'KDKEY' }],
    comparedAt: '2026-11-10T10:00:00',
  },
  counts: { total: 5, match: 1, mismatch: 1, missing: 3, duplicate: 0 },
  items: [
    {
      index: 1,
      a: 'A1',
      b: 'B1',
      status: 'MATCH',
      mismatchCount: 0,
      missing: null,
      duplicate: null,
    },
    {
      index: 2,
      a: 'A2',
      b: 'B2',
      status: 'MISMATCH',
      mismatchCount: 3,
      missing: null,
      duplicate: null,
    },
    {
      index: 3,
      a: 'A3',
      b: 'B3',
      status: 'MISSING',
      mismatchCount: 0,
      missing: 'B',
      duplicate: null,
    },
    {
      index: 4,
      a: 'A4',
      b: 'B4',
      status: 'MISSING',
      mismatchCount: 0,
      missing: 'A',
      duplicate: null,
    },
    {
      index: 5,
      a: 'A5',
      b: 'B5',
      status: 'MISSING',
      mismatchCount: 0,
      missing: 'BOTH',
      duplicate: null,
    },
  ],
};

export const LONG_VALUE = 'L'.repeat(300);

export const DETAILS: Record<number, PairDetail> = {
  1: {
    index: 1,
    a: 'A1',
    b: 'B1',
    status: 'MATCH',
    missing: null,
    duplicate: null,
    rows: [{ name: 'AMOUNT', a: '100', b: '100', status: 'MATCH', message: null }],
  },
  2: {
    index: 2,
    a: 'A2',
    b: 'B2',
    status: 'MISMATCH',
    missing: null,
    duplicate: null,
    rows: [
      { name: 'AMOUNT', a: '100', b: '200', status: 'MISMATCH', message: null },
      { name: 'APPR_MSG.memo', a: null, b: LONG_VALUE, status: 'MISMATCH', message: null },
      { name: 'APPR_MSG', a: null, b: null, status: 'DECRYPT_ERROR', message: 'JSON_INVALID' },
      { name: 'CARD_ENC', a: null, b: null, status: 'DECRYPT_ERROR', message: 'DECRYPT_FAILED' },
    ],
  },
  3: { index: 3, a: 'A3', b: 'B3', status: 'MISSING', missing: 'B', duplicate: null, rows: [] },
  4: { index: 4, a: 'A4', b: 'B4', status: 'MISSING', missing: 'A', duplicate: null, rows: [] },
  5: { index: 5, a: 'A5', b: 'B5', status: 'MISSING', missing: 'BOTH', duplicate: null, rows: [] },
};

export function resetApi() {
  Object.values(mockedApi).forEach((fn) => fn.mockReset());
  mockedApi.environments.mockResolvedValue(ENVS);
  mockedApi.searchTables.mockResolvedValue({ items: [TABLE], truncated: false });
  mockedApi.columns.mockResolvedValue({ keyColumn: 'TRX_NO', columns: COLUMNS });
  mockedApi.pairDetail.mockImplementation((_job: string, index: number) =>
    Promise.resolve(DETAILS[index]),
  );
  mockedApi.summary.mockResolvedValue(SUMMARY);
}

export async function renderApp() {
  render(<App />);
  await waitFor(() =>
    expect(screen.getByLabelText('A쪽 환경').querySelectorAll('option')).toHaveLength(
      ENVS.length + 1,
    ),
  );
}

export function selectEnv(label: 'A쪽 환경' | 'B쪽 환경', env: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value: env } });
}

/** 환경 A=test, B=prod을 고르고 테이블을 검색·선택한 뒤 컬럼이 표시될 때까지 기다린다. */
export async function renderReady(table: TableCandidate = TABLE) {
  mockedApi.searchTables.mockResolvedValue({ items: [table], truncated: false });
  await renderApp();
  selectEnv('A쪽 환경', 'test');
  selectEnv('B쪽 환경', 'prod');
  fireEvent.change(screen.getByLabelText('테이블 검색'), { target: { value: table.name } });
  const option = await screen.findByRole(
    'option',
    { name: new RegExp(table.name) },
    { timeout: 2000 },
  );
  await act(async () => {
    fireEvent.click(option);
  });
  await screen.findByRole('table', { name: '컬럼 목록' });
}

export function typePairs(text: string) {
  fireEvent.change(screen.getByLabelText('거래번호 입력'), { target: { value: text } });
}

export function check(label: string) {
  fireEvent.click(screen.getByLabelText(label));
}
