import { ApiError, NetworkError, api, filenameFrom } from './client';

// FE-01: fetch를 대체해 요청 형식과 오류 구분을 확인한다.
type FetchMock = ReturnType<typeof vi.fn>;

function mockFetch(impl: (url: string, init: RequestInit) => Promise<Response>): FetchMock {
  const fn = vi.fn(impl);
  vi.stubGlobal('fetch', fn);
  return fn;
}

const jsonResponse = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('API 클라이언트', () => {
  it('모든 POST 요청에 JSON 콘텐츠 타입을 붙이고, 본문 없는 중지 요청도 같다', async () => {
    const fetch = mockFetch(async (url) =>
      url.endsWith('/cancel')
        ? jsonResponse(202, { status: 'CANCELLING' })
        : jsonResponse(202, { jobId: 'j1', total: 1 }),
    );
    const request = {
      envA: 'test',
      envB: 'prod',
      owner: 'O',
      table: 'T',
      keyColumn: 'ID',
      columns: [{ name: 'C' }],
      pairsText: 'A1,B1',
    };
    await expect(api.startCompare(request)).resolves.toEqual({ jobId: 'j1', total: 1 });
    await expect(api.cancel('j1')).resolves.toEqual({ status: 'CANCELLING' });

    const [startUrl, startInit] = fetch.mock.calls[0] as [string, RequestInit];
    expect(startUrl).toBe('/api/compare');
    expect(startInit.method).toBe('POST');
    expect((startInit.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(JSON.parse(startInit.body as string)).toEqual(request);

    const [cancelUrl, cancelInit] = fetch.mock.calls[1] as [string, RequestInit];
    expect(cancelUrl).toBe('/api/compare/j1/cancel');
    expect(cancelInit.method).toBe('POST');
    expect((cancelInit.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(cancelInit.body).toBeUndefined();
  });

  it('GET 요청의 쿼리 값을 인코딩한다', async () => {
    const fetch = mockFetch(async () => jsonResponse(200, { items: [], truncated: false }));
    await api.searchTables('test', 'TB_%한글');
    await api.columns('test', 'O W', 'T&1');
    expect(fetch.mock.calls[0][0]).toBe('/api/tables?env=test&q=TB_%25%ED%95%9C%EA%B8%80');
    expect(fetch.mock.calls[1][0]).toBe('/api/tables/columns?env=test&owner=O%20W&table=T%261');
    expect((fetch.mock.calls[0][1] as RequestInit).method).toBe('GET');
  });

  it('400 오류 본문의 details를 ApiError로 전달한다', async () => {
    const details = [{ line: 2, reason: 'NO_DELIMITER' }];
    mockFetch(async () =>
      jsonResponse(400, { code: 'INVALID_INPUT', message: 'invalid pairs', details }),
    );
    const error = await api
      .startCompare({
        envA: 'a',
        envB: 'b',
        owner: 'o',
        table: 't',
        keyColumn: 'ID',
        columns: [],
        pairsText: 'bad',
      })
      .catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(NetworkError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('INVALID_INPUT');
    expect(error.details).toEqual(details);
  });

  it('JSON이 아닌 오류 본문은 INTERNAL_ERROR인 ApiError가 된다', async () => {
    mockFetch(async () => new Response('<html>', { status: 500 }));
    const error = await api.environments().catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('INTERNAL_ERROR');
  });

  it('통신 실패는 NetworkError로 구분한다', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });
    const error = await api.jobStatus('j1').catch((e) => e);
    expect(error).toBeInstanceOf(NetworkError);
    expect(error).not.toBeInstanceOf(ApiError);
  });

  it('요청 취소(AbortError)는 통신 실패로 바꾸지 않는다', async () => {
    mockFetch(async () => {
      throw new DOMException('aborted', 'AbortError');
    });
    const controller = new AbortController();
    const error = await api.searchTables('test', 'TB', controller.signal).catch((e) => e);
    expect(error).toBeInstanceOf(DOMException);
    expect(error.name).toBe('AbortError');
  });

  it('엑셀 응답의 UTF-8 파일명을 읽는다', async () => {
    const name = 'datamatch_test-prod_거래_20261110-101010.xlsx';
    mockFetch(
      async () =>
        new Response('xlsx', {
          status: 200,
          headers: {
            'Content-Disposition': `attachment; filename="x.xlsx"; filename*=UTF-8''${encodeURIComponent(name)}`,
          },
        }),
    );
    const { blob, filename } = await api.excel('j1');
    expect(filename).toBe(name);
    expect(blob.size).toBe(4);
    expect(filenameFrom('attachment; filename="plain.xlsx"')).toBe('plain.xlsx');
    expect(filenameFrom(null)).toBe('datamatch.xlsx');
  });

  it('엑셀 생성 실패는 EXCEL_FAILED ApiError다', async () => {
    mockFetch(async () =>
      jsonResponse(500, { code: 'EXCEL_FAILED', message: 'excel', details: null }),
    );
    const error = await api.excel('j1').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('EXCEL_FAILED');
  });
});
