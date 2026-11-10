// FE-01: fetch 기반 API 클라이언트(기술 설계서 8장, 9.3절).
// 오류 응답은 ApiError, 서버와 통신할 수 없는 경우는 NetworkError로 구분한다(화면 정의서 WA-10).
import type {
  ApiErrorBody,
  CancelResponse,
  ColumnsResponse,
  CompareRequest,
  CompareSummary,
  Environment,
  ErrorCode,
  JobStatusResponse,
  PairDetail,
  StartCompareResponse,
  TableSearchResponse,
} from '../types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly details: unknown;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.details = body.details;
  }
}

export class NetworkError extends Error {
  constructor() {
    super('server unreachable');
    this.name = 'NetworkError';
  }
}

function isAbort(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError';
}

async function send(
  method: 'GET' | 'POST',
  url: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<Response> {
  const init: RequestInit = { method, signal, headers: { Accept: 'application/json' } };
  if (method === 'POST') {
    // 본문이 없는 중지 요청도 JSON 콘텐츠 타입을 붙인다(BE-01 LocalOnlyFilter).
    init.headers = { ...init.headers, 'Content-Type': 'application/json' };
    if (body !== undefined) {
      init.body = JSON.stringify(body);
    }
  }
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    if (isAbort(e)) throw e;
    throw new NetworkError();
  }
  if (!res.ok) {
    throw await toApiError(res);
  }
  return res;
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as Partial<ApiErrorBody>;
    if (body && typeof body.code === 'string') {
      return new ApiError(res.status, {
        code: body.code,
        message: body.message ?? '',
        details: body.details ?? null,
      });
    }
  } catch {
    // JSON이 아닌 오류 본문
  }
  return new ApiError(res.status, { code: 'INTERNAL_ERROR', message: '', details: null });
}

async function json<T>(
  method: 'GET' | 'POST',
  url: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const res = await send(method, url, body, signal);
  try {
    return (await res.json()) as T;
  } catch (e) {
    if (isAbort(e)) throw e;
    throw new NetworkError();
  }
}

const enc = encodeURIComponent;

export const api = {
  environments: () => json<Environment[]>('GET', '/api/environments'),

  searchTables: (env: string, q: string, signal?: AbortSignal) =>
    json<TableSearchResponse>('GET', `/api/tables?env=${enc(env)}&q=${enc(q)}`, undefined, signal),

  columns: (env: string, owner: string, table: string) =>
    json<ColumnsResponse>(
      'GET',
      `/api/tables/columns?env=${enc(env)}&owner=${enc(owner)}&table=${enc(table)}`,
    ),

  startCompare: (request: CompareRequest) =>
    json<StartCompareResponse>('POST', '/api/compare', request),

  jobStatus: (jobId: string) => json<JobStatusResponse>('GET', `/api/compare/${enc(jobId)}`),

  cancel: (jobId: string) => json<CancelResponse>('POST', `/api/compare/${enc(jobId)}/cancel`),

  summary: (jobId: string) => json<CompareSummary>('GET', `/api/compare/${enc(jobId)}/summary`),

  pairDetail: (jobId: string, index: number) =>
    json<PairDetail>('GET', `/api/compare/${enc(jobId)}/pairs/${index}`),

  /** 엑셀 파일을 받는다. 파일명은 Content-Disposition의 UTF-8 이름을 쓴다(TD-16). */
  excel: async (jobId: string): Promise<{ blob: Blob; filename: string }> => {
    const res = await send('GET', `/api/compare/${enc(jobId)}/excel`);
    let blob: Blob;
    try {
      blob = await res.blob();
    } catch {
      throw new NetworkError();
    }
    return { blob, filename: filenameFrom(res.headers.get('Content-Disposition')) };
  },
};

export function filenameFrom(disposition: string | null): string {
  if (disposition) {
    const star = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition);
    if (star) {
      try {
        return decodeURIComponent(star[1].trim());
      } catch {
        // 잘못된 인코딩이면 아래 규칙으로
      }
    }
    const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition);
    if (plain) return plain[1].trim();
  }
  return 'datamatch.xlsx';
}
