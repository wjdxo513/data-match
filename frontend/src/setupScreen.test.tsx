import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { ApiError, NetworkError } from './api/client';
import {
  NUM_COLUMNS,
  TABLE,
  check,
  mockedApi,
  renderApp,
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

// FE-02: SC-01 설정 화면(화면 정의서 SC01-01~13, 4장 버튼 정책).
beforeEach(() => resetApi());
afterEach(() => vi.useRealTimers());

const runButton = () => screen.getByRole('button', { name: '비교 실행' });

describe('SC-01 설정 화면', () => {
  it('초기에는 환경이 "선택 안 함"이고 비교 실행이 비활성이다', async () => {
    await renderApp();
    expect(screen.getByLabelText('A쪽 환경')).toHaveValue('');
    expect(screen.getByLabelText('B쪽 환경')).toHaveValue('');
    expect(screen.getByLabelText('A쪽 환경').querySelector('option')).toHaveTextContent(
      '선택 안 함',
    );
    expect(runButton()).toBeDisabled();
    expect(screen.getByLabelText('테이블 검색')).toBeDisabled();
    expect(screen.getByText('A쪽과 B쪽 환경을 모두 선택해 주세요.')).toBeInTheDocument();
  });

  it('운영을 고른 쪽에만 "운영 DB" 배지가 표시된다', async () => {
    await renderApp();
    selectEnv('A쪽 환경', 'test');
    selectEnv('B쪽 환경', 'prod');
    expect(screen.queryByTestId('env-a-prod-badge')).not.toBeInTheDocument();
    expect(screen.getByTestId('env-b-prod-badge')).toHaveTextContent('운영 DB');
    selectEnv('A쪽 환경', 'prod');
    selectEnv('B쪽 환경', 'dev');
    expect(screen.getByTestId('env-a-prod-badge')).toBeInTheDocument();
    expect(screen.queryByTestId('env-b-prod-badge')).not.toBeInTheDocument();
  });

  it('테이블 검색은 2자 이상, 300ms 뒤 1회 호출하고 새 입력은 이전 요청을 취소한다', async () => {
    await renderApp();
    vi.useFakeTimers();
    selectEnv('A쪽 환경', 'test');
    const input = screen.getByLabelText('테이블 검색');
    fireEvent.change(input, { target: { value: 'D' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(mockedApi.searchTables).not.toHaveBeenCalled();

    fireEvent.change(input, { target: { value: 'DM' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(299);
    });
    expect(mockedApi.searchTables).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { value: 'DM_' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(mockedApi.searchTables).toHaveBeenCalledTimes(1);
    expect(mockedApi.searchTables).toHaveBeenLastCalledWith('test', 'DM_', expect.any(AbortSignal));

    // 응답이 오기 전에 다시 입력하면 진행 중인 요청을 취소한다.
    mockedApi.searchTables.mockImplementation(() => new Promise(() => {}));
    fireEvent.change(input, { target: { value: 'DM_IT' } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    const pendingSignal = mockedApi.searchTables.mock.calls[1][2] as AbortSignal;
    expect(pendingSignal.aborted).toBe(false);
    fireEvent.change(input, { target: { value: 'DM_IT_T' } });
    expect(pendingSignal.aborted).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(mockedApi.searchTables).toHaveBeenCalledTimes(3);
  });

  it('복합 PK 테이블도 선택되고 비교 키를 고르지 않으면 안내만 표시한다(SN-E02-1)', async () => {
    const COMP_COLUMNS = [
      { name: 'CONTROL_NO', type: 'VARCHAR2(20)', pk: true, clob: false, keyable: true },
      { name: 'SEQ', type: 'NUMBER(5)', pk: true, clob: false, keyable: true },
      { name: 'REG_DT', type: 'DATE', pk: false, clob: false, keyable: false },
      { name: 'AMOUNT', type: 'NUMBER(12)', pk: false, clob: false, keyable: true },
    ];
    mockedApi.columns.mockResolvedValue({ keyColumn: null, columns: COMP_COLUMNS });
    await renderReady({ owner: 'O', name: 'TB_COMP' });
    expect(screen.queryByText(/지원하지 않습니다|비교할 수 없습니다/)).not.toBeInTheDocument();
    const key = screen.getByLabelText('비교 키');
    expect(key).toHaveValue('');
    const options = Array.from(key.querySelectorAll('option')).map((o) => o.textContent);
    expect(options).toEqual(['선택 안 함', 'CONTROL_NO (PK)', 'SEQ (PK)', 'AMOUNT']);

    check('AMOUNT 선택');
    typePairs('C1,C1');
    fireEvent.click(runButton());
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '비교 키 컬럼을 선택해 주세요.',
    );
    expect(mockedApi.startCompare).not.toHaveBeenCalled();

    fireEvent.change(key, { target: { value: 'CONTROL_NO' } });
    expect(screen.getByLabelText('AMOUNT 선택')).toBeChecked();
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 1 });
    mockedApi.jobStatus.mockReturnValue(new Promise(() => {}));
    fireEvent.click(runButton());
    await waitFor(() => expect(mockedApi.startCompare).toHaveBeenCalledTimes(1));
    expect(mockedApi.startCompare).toHaveBeenCalledWith(
      expect.objectContaining({ owner: 'O', table: 'TB_COMP', keyColumn: 'CONTROL_NO' }),
    );
  });

  it('단일 PK 테이블은 그 PK가 비교 키 기본값이고 숫자 검증은 비교 키 타입을 따른다', async () => {
    await renderReady();
    expect(screen.getByLabelText('비교 키')).toHaveValue('TRX_NO');
    // 문자형 비교 키에서는 숫자 검증을 하지 않는다.
    typePairs('X1,Y1');
    expect(screen.queryByRole('alert', { name: '입력 오류' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('비교 키'), { target: { value: 'AMOUNT' } });
    expect(screen.getByRole('alert', { name: '입력 오류' })).toHaveTextContent(
      '1번째 줄: 거래번호A는 숫자여야 합니다.',
    );
  });

  it('컬럼 검색과 보이는 컬럼에만 적용되는 전체 선택/해제, 해제 시 복호화 해제', async () => {
    await renderReady();
    fireEvent.change(screen.getByLabelText('컬럼 검색'), { target: { value: 'no' } });
    const table = screen.getByRole('table', { name: '컬럼 목록' });
    expect(within(table).queryByText('AMOUNT')).not.toBeInTheDocument();
    expect(within(table).getByText('ORDER_NO')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '전체 선택' }));
    fireEvent.change(screen.getByLabelText('컬럼 검색'), { target: { value: '' } });
    expect(screen.getByLabelText('TRX_NO 선택')).toBeChecked();
    expect(screen.getByLabelText('ORDER_NO 선택')).toBeChecked();
    expect(screen.getByLabelText('AMOUNT 선택')).not.toBeChecked();
    expect(screen.getByLabelText('APPR_MSG 선택')).not.toBeChecked();

    check('APPR_MSG 선택');
    check('APPR_MSG 복호화');
    expect(screen.getByLabelText('APPR_MSG 복호화')).toBeChecked();
    fireEvent.change(screen.getByLabelText('컬럼 검색'), { target: { value: 'msg' } });
    fireEvent.click(screen.getByRole('button', { name: '전체 해제' }));
    fireEvent.change(screen.getByLabelText('컬럼 검색'), { target: { value: '' } });
    expect(screen.getByLabelText('APPR_MSG 선택')).not.toBeChecked();
    expect(screen.getByLabelText('APPR_MSG 복호화')).not.toBeChecked();
    expect(screen.getByLabelText('ORDER_NO 선택')).toBeChecked();
  });

  it('CLOB 컬럼은 선택된 경우에만 복호화를 지정할 수 있고 정책은 KDKEY 기본, PANEN 선택 가능', async () => {
    await renderReady();
    expect(screen.queryByLabelText('AMOUNT 복호화')).not.toBeInTheDocument();
    expect(screen.getByLabelText('APPR_MSG 복호화')).toBeDisabled();
    expect(screen.getByLabelText('APPR_MSG 복호화 정책')).toBeDisabled();
    check('APPR_MSG 선택');
    expect(screen.getByLabelText('APPR_MSG 복호화')).toBeEnabled();
    expect(screen.getByLabelText('APPR_MSG 복호화 정책')).toBeDisabled();
    check('APPR_MSG 복호화');
    const policy = screen.getByLabelText('APPR_MSG 복호화 정책');
    expect(policy).toBeEnabled();
    expect(policy).toHaveValue('KDKEY');
    fireEvent.change(policy, { target: { value: 'PANEN' } });
    expect(policy).toHaveValue('PANEN');
  });

  it('거래번호 입력 시 쌍 수와 오류 줄 수가 즉시 갱신되고 오류 문구가 표시된다', async () => {
    await renderReady();
    expect(screen.getByTestId('pairs-status')).toHaveTextContent('입력 0쌍 / 최대 1,000쌍');
    typePairs('A1,B1\nA2\tB2\nbad\n,B4\nA5,B5,C5');
    expect(screen.getByTestId('pairs-status')).toHaveTextContent('입력 2쌍 / 최대 1,000쌍');
    expect(screen.getByTestId('pairs-errors')).toHaveTextContent('오류 3줄');
    const errors = screen.getByRole('alert', { name: '입력 오류' });
    expect(errors).toHaveTextContent(
      '3번째 줄: 쉼표나 탭으로 거래번호A와 거래번호B를 구분해 주세요.',
    );
    expect(errors).toHaveTextContent('4번째 줄: 거래번호A가 비어 있습니다.');
    expect(errors).toHaveTextContent('5번째 줄: 한 줄에는 거래번호 두 개만 입력할 수 있습니다.');
  });

  it('NUMBER PK에 숫자가 아닌 거래번호는 WA-12 오류 줄이 되고 비교를 시작하지 않는다', async () => {
    mockedApi.columns.mockResolvedValue({ keyColumn: 'TRX_ID', columns: NUM_COLUMNS });
    await renderReady({ ...TABLE, name: 'DM_IT_TRX_NUM' });
    check('AMOUNT 선택');
    typePairs('1,2\n3,X9');
    expect(screen.getByRole('alert', { name: '입력 오류' })).toHaveTextContent(
      '2번째 줄: 거래번호B는 숫자여야 합니다.',
    );
    fireEvent.click(runButton());
    expect(await screen.findByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '입력 형식 오류가 있어 비교를 시작할 수 없습니다.',
    );
    expect(mockedApi.startCompare).not.toHaveBeenCalled();
  });

  it('컬럼 0개, 입력 0건, 오류 줄, 1,000쌍 초과는 비교를 시작하지 않고 안내한다', async () => {
    await renderReady();
    typePairs('A1,B1');
    fireEvent.click(runButton());
    expect(await screen.findByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '비교할 컬럼을 하나 이상 선택해 주세요.',
    );
    check('AMOUNT 선택');
    typePairs('  \n');
    fireEvent.click(runButton());
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '거래번호를 입력해 주세요.',
    );
    typePairs('A1,B1\nbad');
    fireEvent.click(runButton());
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '입력 형식 오류가 있어 비교를 시작할 수 없습니다. 표시된 줄을 수정해 주세요.',
    );
    typePairs(Array.from({ length: 1001 }, (_, i) => `A${i},B${i}`).join('\n'));
    expect(screen.getByTestId('pairs-status')).toHaveClass('over');
    fireEvent.click(runButton());
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '입력한 거래 쌍이 1,000건을 초과했습니다(현재 1,001쌍). 1,000쌍 이하로 나누어 입력해 주세요.',
    );
    expect(mockedApi.startCompare).not.toHaveBeenCalled();
  });

  it('연결 실패, 조회 가능한 테이블 없음·검색 결과 없음, 후보 초과 문구가 표시된다', async () => {
    await renderApp();
    selectEnv('A쪽 환경', 'test');
    const input = screen.getByLabelText('테이블 검색');
    mockedApi.searchTables.mockRejectedValueOnce(
      new ApiError(503, { code: 'DB_CONNECTION_FAILED', message: 'x', details: { env: 'test' } }),
    );
    fireEvent.change(input, { target: { value: 'TB' } });
    expect(
      await screen.findByText(
        '테스트 DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.',
      ),
    ).toBeInTheDocument();

    mockedApi.searchTables.mockResolvedValueOnce({ items: [], truncated: false });
    fireEvent.change(input, { target: { value: 'TBX' } });
    expect(await screen.findByText('검색어와 일치하는 테이블이 없습니다.')).toBeInTheDocument();
    expect(
      screen.getByText('조회할 수 있는 테이블이 없습니다. 접속 계정의 조회 권한을 확인해 주세요.'),
    ).toBeInTheDocument();

    mockedApi.searchTables.mockResolvedValueOnce({ items: [TABLE], truncated: true });
    fireEvent.change(input, { target: { value: 'DM_IT' } });
    expect(
      await screen.findByText('검색 결과가 많아 일부만 표시합니다. 검색어를 더 입력해 주세요.'),
    ).toBeInTheDocument();

    mockedApi.searchTables.mockRejectedValueOnce(new NetworkError());
    fireEvent.change(input, { target: { value: 'DM_ITX' } });
    expect(
      await screen.findByText('서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.'),
    ).toBeInTheDocument();
  });

  it('A쪽 환경을 바꾸면 테이블 선택을 유지하고 컬럼을 다시 조회한다(WA-05)', async () => {
    await renderReady();
    check('AMOUNT 선택');
    expect(mockedApi.columns).toHaveBeenCalledTimes(1);
    selectEnv('A쪽 환경', 'dev');
    await waitFor(() => expect(mockedApi.columns).toHaveBeenCalledTimes(2));
    expect(mockedApi.columns).toHaveBeenLastCalledWith('dev', 'DM_IT_OWNER', 'DM_IT_TRX_STR');
    expect(screen.getByText('DM_IT_OWNER.DM_IT_TRX_STR')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('AMOUNT 선택')).toBeChecked());
  });

  it('필수 입력이 충족되면 비교 실행이 활성화된다', async () => {
    await renderReady();
    expect(runButton()).toBeEnabled();
  });
});
