// FE-01: API 요청·응답 타입(기술 설계서 8장).

export type DecryptPolicy = 'KDKEY' | 'PANEN';

export interface Environment {
  id: string;
  name: string;
  production: boolean;
}

/** 테이블 검색 후보. PRD v1.4부터 모든 테이블을 선택할 수 있다(PA-10). */
export interface TableCandidate {
  owner: string;
  name: string;
}

export interface TableSearchResponse {
  items: TableCandidate[];
  truncated: boolean;
}

export interface ColumnInfo {
  name: string;
  type: string;
  pk: boolean;
  clob: boolean;
  /** 비교 키로 고를 수 있는지(문자형·NUMBER, PA-19). */
  keyable: boolean;
}

export interface ColumnsResponse {
  /** 기본 비교 키(단일 PK이고 keyable일 때). 없으면 null. */
  keyColumn: string | null;
  columns: ColumnInfo[];
}

export interface CompareColumnRequest {
  name: string;
  decrypt?: { policy: DecryptPolicy };
}

export interface CompareRequest {
  envA: string;
  envB: string;
  owner: string;
  table: string;
  keyColumn: string;
  columns: CompareColumnRequest[];
  pairsText: string;
}

export interface StartCompareResponse {
  jobId: string;
  total: number;
}

export type JobStatus = 'RUNNING' | 'CANCELLING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type Side = 'A' | 'B';

export interface JobStatusResponse {
  status: JobStatus;
  processed: number;
  total: number;
  errorCode: ErrorCode | null;
  failedSide: Side | null;
  failedEnv: string | null;
}

export interface CancelResponse {
  status: JobStatus;
}

export type PairStatus = 'MATCH' | 'MISMATCH' | 'MISSING' | 'DUPLICATE';

/** 키 중복 짝의 양쪽 조회 건수(PRD v1.4). */
export interface DuplicateCount {
  a: number;
  b: number;
}
export type MissingSide = 'A' | 'B' | 'BOTH';
export type RowStatus = 'MATCH' | 'MISMATCH' | 'DECRYPT_ERROR';
export type DecryptErrorCause = 'DECRYPT_FAILED' | 'JSON_INVALID' | 'JSON_NOT_OBJECT';

export interface CompareCondition {
  envA: string;
  envB: string;
  owner: string;
  table: string;
  keyColumn: string;
  columns: { name: string; decrypt?: DecryptPolicy }[];
  comparedAt: string;
}

export interface SummaryItem {
  index: number;
  a: string;
  b: string;
  status: PairStatus;
  mismatchCount: number;
  missing: MissingSide | null;
  duplicate: DuplicateCount | null;
}

export interface CompareSummary {
  condition: CompareCondition;
  counts: { total: number; match: number; mismatch: number; missing: number; duplicate: number };
  items: SummaryItem[];
}

/** 상세 행. `message`는 복호화 오류 원인 코드이며 화면 문구는 messages.ts가 만든다(실행 계획 PL-14). */
export interface DetailRow {
  name: string;
  a: string | null;
  b: string | null;
  status: RowStatus;
  message: DecryptErrorCause | null;
}

export interface PairDetail {
  index: number;
  a: string;
  b: string;
  status: PairStatus;
  missing: MissingSide | null;
  duplicate: DuplicateCount | null;
  rows: DetailRow[];
}

export type PairErrorReason =
  'NO_DELIMITER' | 'TOO_MANY_DELIMITERS' | 'EMPTY_A' | 'EMPTY_B' | 'INVALID_NUMBER';

export interface PairLineError {
  line: number;
  reason: PairErrorReason;
}

export type ErrorCode =
  | 'INVALID_INPUT'
  | 'ENV_NOT_CONFIGURED'
  | 'FORBIDDEN_REQUEST'
  | 'JOB_NOT_FOUND'
  | 'PAIR_NOT_FOUND'
  | 'JOB_RUNNING'
  | 'JOB_NOT_COMPLETED'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'EXCEL_FAILED'
  | 'INTERNAL_ERROR'
  | 'DB_QUERY_FAILED'
  | 'DB_CONNECTION_FAILED'
  | 'QUERY_TIMEOUT';

/** 오류 응답 본문 `{code, message, details}`(기술 설계서 8장). */
export interface ApiErrorBody {
  code: ErrorCode;
  message: string;
  details: unknown;
}
