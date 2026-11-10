import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { COLLAPSE_LENGTH } from './components/ResultView';
import {
  LONG_VALUE,
  SUMMARY,
  check,
  mockedApi,
  renderReady,
  resetApi,
  selectEnv,
  typePairs,
} from './test/appHelpers';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  const names = [
    'environments',
    'searchTables',
    'columns',
    'startCompare',
    'jobStatus',
    'cancel',
    'summary',
    'pairDetail',
    'excel',
  ];
  return { ...actual, api: Object.fromEntries(names.map((n) => [n, vi.fn()])) };
});

// FE-04: SC-03 결과 화면(화면 정의서 SC03-01~07, 4장).
beforeEach(() => {
  resetApi();
  mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 5 });
  mockedApi.jobStatus.mockResolvedValue({
    status: 'COMPLETED',
    processed: 5,
    total: 5,
    errorCode: null,
    failedSide: null,
    failedEnv: null,
  });
});

async function renderWithResult() {
  await renderReady();
  check('AMOUNT 선택');
  typePairs('A1,B1\nA2,B2\nA3,B3\nA4,B4\nA5,B5');
  fireEvent.click(screen.getByRole('button', { name: '비교 실행' }));
  return screen.findByRole('region', { name: '비교 결과' }, { timeout: 3000 });
}

const items = () =>
  within(screen.getByRole('listbox', { name: '요약 목록' })).getAllByRole('option');

function selectPair(index: number) {
  fireEvent.click(items()[index - 1]);
}

describe('SC-03 결과 화면', () => {
  it('요약 목록이 입력 순서와 판정 라벨·색상으로 표시되고 건수가 counts와 같다', async () => {
    await renderWithResult();
    const rows = items();
    expect(rows.map((r) => r.querySelector('.pair')?.textContent)).toEqual([
      'A1 - B1',
      'A2 - B2',
      'A3 - B3',
      'A4 - B4',
      'A5 - B5',
    ]);
    const pills = rows.map((r) => r.querySelector('.pill')!);
    expect(pills.map((p) => p.textContent)).toEqual([
      '일치',
      '불일치(3)',
      '누락(B 없음)',
      '누락(A 없음)',
      '누락(A·B 모두 없음)',
    ]);
    expect(pills[0]).toHaveClass('match');
    expect(pills[1]).toHaveClass('mismatch');
    expect(pills[2]).toHaveClass('missing');
    expect(screen.getByTestId('result-counts')).toHaveTextContent(
      '총 5쌍 · 일치 1 · 불일치 1 · 누락 3',
    );
    expect(screen.getByTestId('result-condition')).toHaveTextContent(
      'A 테스트B 운영DM_IT_OWNER.DM_IT_TRX_STR · 비교 컬럼 2개',
    );
  });

  it('완료 시 첫 짝을 선택해 상세를 보여주고 상세는 짝마다 한 번만 조회한다', async () => {
    await renderWithResult();
    expect(items()[0]).toHaveAttribute('aria-selected', 'true');
    await screen.findByRole('table', { name: '상세 비교' });
    expect(mockedApi.pairDetail).toHaveBeenCalledWith('j1', 1);
    selectPair(2);
    await waitFor(() => expect(mockedApi.pairDetail).toHaveBeenCalledWith('j1', 2));
    selectPair(1);
    selectPair(2);
    await screen.findByText('APPR_MSG.memo');
    expect(mockedApi.pairDetail).toHaveBeenCalledTimes(2);
  });

  it('상세에서 불일치·복호화 오류는 빨간색과 라벨, 복호화 컬럼은 컬럼명.필드명, 머리글에 환경명이 표시된다', async () => {
    await renderWithResult();
    selectPair(2);
    const table = await screen.findByRole('table', { name: '상세 비교' });
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((h) => h.textContent);
    expect(headers).toEqual(['컬럼·필드', 'A값(테스트)', 'B값(운영)', '상태']);
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows[0]).toHaveClass('row-mismatch');
    expect(rows[0]).toHaveTextContent('불일치');
    expect(rows[1]).toHaveTextContent('APPR_MSG.memo');
    expect(rows[2]).toHaveClass('row-mismatch');
    expect(rows[2]).toHaveTextContent('복호화 오류: 복호화 결과를 JSON으로 읽을 수 없습니다.');
    expect(rows[2]).toHaveTextContent('복호화 오류');
    expect(rows[3]).toHaveTextContent('복호화 오류: 복호화에 실패했습니다.');
    selectPair(1);
    const matchRow = within(await screen.findByRole('table', { name: '상세 비교' })).getAllByRole(
      'row',
    )[1];
    expect(matchRow).not.toHaveClass('row-mismatch');
    expect(matchRow).toHaveTextContent('일치');
  });

  it('NULL은 회색 "(NULL)", 긴 값은 접힌 상태가 기본이고 토글로 펼친다', async () => {
    await renderWithResult();
    selectPair(2);
    const table = await screen.findByRole('table', { name: '상세 비교' });
    const nullValue = within(table).getByText('(NULL)');
    expect(nullValue).toHaveClass('null-value');
    expect(within(table).queryByText(LONG_VALUE)).not.toBeInTheDocument();
    expect(
      within(table).getByText(`${'L'.repeat(COLLAPSE_LENGTH)}…`, { exact: false }),
    ).toBeInTheDocument();
    fireEvent.click(within(table).getByRole('button', { name: '펼치기' }));
    expect(within(table).getByText(LONG_VALUE, { exact: false })).toBeInTheDocument();
    fireEvent.click(within(table).getByRole('button', { name: '접기' }));
    expect(within(table).queryByText(LONG_VALUE, { exact: false })).not.toBeInTheDocument();
  });

  it('누락 짝은 없는 쪽 안내만 표시하고 비교 표를 표시하지 않는다', async () => {
    await renderWithResult();
    selectPair(3);
    expect(await screen.findByText('B쪽(운영) DB에 해당 거래번호가 없습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '상세 비교' })).not.toBeInTheDocument();
    selectPair(4);
    expect(
      await screen.findByText('A쪽(테스트) DB에 해당 거래번호가 없습니다.'),
    ).toBeInTheDocument();
    selectPair(5);
    expect(await screen.findByText('A쪽과 B쪽 모두 해당 거래번호가 없습니다.')).toBeInTheDocument();
  });

  it('엑셀 다운로드는 진행 중 비활성·로딩이고 성공 시 저장, 실패 시 안내 후 재시도할 수 있다', async () => {
    const createUrl = vi.fn(() => 'blob:x');
    const revokeUrl = vi.fn();
    Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await renderWithResult();

    let resolveExcel: (v: { blob: Blob; filename: string }) => void = () => {};
    mockedApi.excel.mockReturnValueOnce(new Promise((r) => (resolveExcel = r)));
    fireEvent.click(screen.getByRole('button', { name: '엑셀 다운로드' }));
    expect(await screen.findByRole('button', { name: '엑셀 생성 중...' })).toBeDisabled();
    await act(async () =>
      resolveExcel({
        blob: new Blob(['x']),
        filename: 'datamatch_test-prod_T_20261110-100000.xlsx',
      }),
    );
    expect(screen.getByRole('button', { name: '엑셀 다운로드' })).toBeEnabled();
    expect(mockedApi.excel).toHaveBeenCalledWith('j1');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(createUrl).toHaveBeenCalled();

    mockedApi.excel.mockRejectedValueOnce(new Error('500'));
    fireEvent.click(screen.getByRole('button', { name: '엑셀 다운로드' }));
    expect(
      await screen.findByText(
        '엑셀 파일을 만들지 못했습니다. 잠시 후 다시 시도해 주세요. 비교 결과는 화면에 그대로 남아 있습니다.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '비교 결과' })).toBeInTheDocument();
    mockedApi.excel.mockResolvedValueOnce({ blob: new Blob(['x']), filename: 'f.xlsx' });
    fireEvent.click(screen.getByRole('button', { name: '엑셀 다운로드' }));
    await waitFor(() => expect(mockedApi.excel).toHaveBeenCalledTimes(3));
    clickSpy.mockRestore();
  });

  it('입력을 바꿔도 결과 조건은 그대로이고 새 비교가 시작되면 새 결과로 대체된다', async () => {
    await renderWithResult();
    const condition = screen.getByTestId('result-condition').textContent;
    selectEnv('A쪽 환경', 'dev');
    selectEnv('B쪽 환경', 'test');
    typePairs('X1,Y1');
    expect(screen.getByTestId('result-condition').textContent).toBe(condition);

    await waitFor(() => expect(screen.getByLabelText('AMOUNT 선택')).toBeInTheDocument());
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j2', total: 1 });
    mockedApi.summary.mockResolvedValue({
      ...SUMMARY,
      condition: { ...SUMMARY.condition, envA: 'dev', envB: 'test' },
      counts: { total: 1, match: 1, mismatch: 0, missing: 0 },
      items: [{ index: 1, a: 'X1', b: 'Y1', status: 'MATCH', mismatchCount: 0, missing: null }],
    });
    fireEvent.click(screen.getByRole('button', { name: '비교 실행' }));
    await waitFor(() => expect(screen.getByTestId('result-counts')).toHaveTextContent('총 1쌍'), {
      timeout: 3000,
    });
    expect(screen.getByTestId('result-condition')).toHaveTextContent('A 개발B 테스트');
    expect(items()).toHaveLength(1);
    await waitFor(() => expect(mockedApi.pairDetail).toHaveBeenLastCalledWith('j2', 1));
  });

  it('불일치만 보기 필터와 설정 저장 UI가 없다', async () => {
    await renderWithResult();
    expect(screen.queryByText(/불일치만/)).not.toBeInTheDocument();
    expect(screen.queryByText(/설정 저장/)).not.toBeInTheDocument();
    expect(screen.queryByText(/업로드/)).not.toBeInTheDocument();
  });
});
