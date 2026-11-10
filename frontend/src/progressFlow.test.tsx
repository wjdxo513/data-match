import { act, fireEvent, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ApiError, NetworkError } from './api/client';
import { check, mockedApi, renderReady, resetApi, typePairs } from './test/appHelpers';

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

// FE-03: SC-02 진행 모달(화면 정의서 SC02-01~06, 기술 설계서 7.2·7.3절).
beforeEach(() => resetApi());
afterEach(() => vi.useRealTimers());

const status = (
  s: string,
  processed: number,
  total = 500,
  extra: Record<string, unknown> = {},
) => ({
  status: s,
  processed,
  total,
  errorCode: null,
  failedSide: null,
  failedEnv: null,
  ...extra,
});

async function flush(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 설정을 마친 뒤 가짜 타이머로 바꾸고 [비교 실행]을 누른다. */
async function startComparison() {
  await renderReady();
  check('AMOUNT 선택');
  typePairs('A1,B1\nA2,B2');
  vi.useFakeTimers();
  fireEvent.click(screen.getByRole('button', { name: '비교 실행' }));
  await flush();
}

describe('SC-02 진행 모달', () => {
  it('비교 시작 후 모달을 띄우고 바깥을 눌러도 닫히지 않는다', async () => {
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 500 });
    mockedApi.jobStatus.mockResolvedValue(status('RUNNING', 0));
    await startComparison();
    expect(mockedApi.startCompare).toHaveBeenCalledWith(
      expect.objectContaining({
        envA: 'test',
        envB: 'prod',
        columns: [{ name: 'AMOUNT' }],
        pairsText: 'A1,B1\nA2,B2',
      }),
    );
    expect(screen.getByRole('dialog', { name: '비교 진행 중' })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('progress-overlay'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('1초마다 상태를 조회하고 서버가 보고한 값으로만 진행률을 갱신한다', async () => {
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 500 });
    mockedApi.jobStatus
      .mockResolvedValueOnce(status('RUNNING', 0))
      .mockResolvedValueOnce(status('RUNNING', 200))
      .mockResolvedValueOnce(status('RUNNING', 200))
      .mockResolvedValueOnce(status('COMPLETED', 500));
    await startComparison();
    expect(screen.getByText(/처리/)).toHaveTextContent('처리 0 / 전체 500쌍');
    await flush(999);
    expect(mockedApi.jobStatus).not.toHaveBeenCalled();
    await flush(1);
    expect(mockedApi.jobStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
    await flush(1000);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
    expect(screen.getByText(/처리/)).toHaveTextContent('처리 200 / 전체 500쌍');
    await flush(999);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
    await flush(1);
    expect(mockedApi.jobStatus).toHaveBeenCalledTimes(3);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
    await flush(1000);
    expect(mockedApi.summary).toHaveBeenCalledWith('j1');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: '비교 결과' })).toBeInTheDocument();
  });

  it('비교 중지는 중지 요청 후 "비교 중지 중"으로 바뀌고 CANCELLED에서 모달을 닫고 입력을 유지한다', async () => {
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 500 });
    mockedApi.jobStatus
      .mockResolvedValueOnce(status('CANCELLING', 100))
      .mockResolvedValueOnce(status('CANCELLED', 100));
    mockedApi.cancel.mockResolvedValue({ status: 'CANCELLING' });
    await startComparison();
    fireEvent.click(screen.getByRole('button', { name: '비교 중지' }));
    await flush();
    expect(mockedApi.cancel).toHaveBeenCalledWith('j1');
    expect(screen.getByRole('dialog', { name: '비교 중지 중' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '비교 중지 중...' })).toBeDisabled();
    await flush(1000);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await flush(1000);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      '비교를 중지했습니다. 결과는 저장되지 않았습니다.',
    );
    expect(screen.getByLabelText('거래번호 입력')).toHaveValue('A1,B1\nA2,B2');
    expect(screen.getByLabelText('AMOUNT 선택')).toBeChecked();
  });

  it('FAILED는 연결 실패(쪽·환경)와 타임아웃 문구를 표시하고 [닫기] 후 입력을 유지한다', async () => {
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 500 });
    mockedApi.jobStatus.mockResolvedValueOnce(
      status('FAILED', 100, 500, {
        errorCode: 'DB_CONNECTION_FAILED',
        failedSide: 'B',
        failedEnv: 'prod',
      }),
    );
    await startComparison();
    await flush(1000);
    expect(screen.getByRole('dialog', { name: '비교 실패' })).toHaveTextContent(
      'B쪽(운영) DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.',
    );
    await flush(3000);
    expect(mockedApi.jobStatus).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByLabelText('거래번호 입력')).toHaveValue('A1,B1\nA2,B2');

    mockedApi.startCompare.mockResolvedValue({ jobId: 'j2', total: 500 });
    mockedApi.jobStatus.mockResolvedValueOnce(
      status('FAILED', 0, 500, { errorCode: 'QUERY_TIMEOUT' }),
    );
    fireEvent.click(screen.getByRole('button', { name: '비교 실행' }));
    await flush();
    await flush(1000);
    expect(screen.getByRole('dialog')).toHaveTextContent(
      'DB 조회 시간이 제한(30초)을 넘어 비교를 중단했습니다. 거래번호 건수를 줄이거나 잠시 후 다시 시도해 주세요.',
    );
  });

  it('폴링이나 요청의 통신 실패는 WA-10 문구를 표시한다', async () => {
    mockedApi.startCompare.mockResolvedValue({ jobId: 'j1', total: 500 });
    mockedApi.jobStatus.mockRejectedValueOnce(new NetworkError());
    await startComparison();
    await flush(1000);
    expect(screen.getByRole('dialog')).toHaveTextContent(
      '서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.',
    );
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    mockedApi.startCompare.mockRejectedValueOnce(new NetworkError());
    fireEvent.click(screen.getByRole('button', { name: '비교 실행' }));
    await flush();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.',
    );
  });

  it('서버의 400 INVALID_INPUT은 모달 없이 SC-01의 오류 줄로 표시한다', async () => {
    mockedApi.startCompare.mockRejectedValue(
      new ApiError(400, {
        code: 'INVALID_INPUT',
        message: 'x',
        details: [{ line: 2, reason: 'NO_DELIMITER' }],
      }),
    );
    await startComparison();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const alert = screen.getByRole('alert', { name: '비교 실행 안내' });
    expect(alert).toHaveTextContent('입력 형식 오류가 있어 비교를 시작할 수 없습니다.');
    expect(alert).toHaveTextContent(
      '2번째 줄: 쉼표나 탭으로 거래번호A와 거래번호B를 구분해 주세요.',
    );
  });

  it('JOB_RUNNING이면 모달을 열지 않고 WA-13 문구를 표시한다', async () => {
    mockedApi.startCompare.mockRejectedValue(
      new ApiError(409, { code: 'JOB_RUNNING', message: 'x', details: null }),
    );
    await startComparison();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('alert', { name: '비교 실행 안내' })).toHaveTextContent(
      '이전 비교가 아직 진행 중입니다. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('진행률을 시간 경과로 증가시키는 코드가 없다', () => {
    for (const file of ['App.tsx', 'components/ProgressModal.tsx']) {
      const src = readFileSync(resolve(__dirname, file), 'utf-8');
      expect(src, file).not.toMatch(/setInterval|requestAnimationFrame|Date\.now|performance\.now/);
      expect(src, file).not.toMatch(/processed\s*\+|processed\s*\+\+|percent\s*\+/);
    }
  });
});
