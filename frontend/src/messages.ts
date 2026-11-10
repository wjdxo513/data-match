// FE-01: 오류 코드와 화면 정의서(v1.5) 2장·4장 문구를 연결하는 메시지 맵.
// 화면 문구는 이 파일에서만 만든다(기술 설계서 5장 FR-05, 8장).
import { ApiError, NetworkError } from './api/client';
import type {
  DecryptErrorCause,
  MissingSide,
  PairErrorReason,
  Side,
  TableUnsupportedReason,
} from './types';
import { MAX_PAIRS } from './utils/pairsParser';

const count = (n: number) => n.toLocaleString('ko-KR');

export const messages = {
  // SC-01 입력 검증
  envNotSelected: 'A쪽과 B쪽 환경을 모두 선택해 주세요.',
  noColumns: '비교할 컬럼을 하나 이상 선택해 주세요.',
  noPairs: '거래번호를 입력해 주세요.',
  inputHasErrors: '입력 형식 오류가 있어 비교를 시작할 수 없습니다. 표시된 줄을 수정해 주세요.',
  pairLimitExceeded: (n: number) =>
    `입력한 거래 쌍이 ${count(MAX_PAIRS)}건을 초과했습니다(현재 ${count(n)}쌍). ${count(MAX_PAIRS)}쌍 이하로 나누어 입력해 주세요.`,
  inputStatus: (pairs: number) => `입력 ${count(pairs)}쌍 / 최대 ${count(MAX_PAIRS)}쌍`,
  errorLineCount: (lines: number) => `오류 ${count(lines)}줄`,
  jobRunning: '이전 비교가 아직 진행 중입니다. 잠시 후 다시 시도해 주세요.',

  // SC-01 테이블 검색
  connectionFailed: (envName: string) =>
    `${envName} DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.`,
  noTables: '조회할 수 있는 테이블이 없습니다. 접속 계정의 조회 권한을 확인해 주세요.',
  noMatchingTables: '검색어와 일치하는 테이블이 없습니다.',
  tooManyCandidates: '검색 결과가 많아 일부만 표시합니다. 검색어를 더 입력해 주세요.',

  // SC-02
  compareConnectionFailed: (side: Side, envName: string) =>
    `${side}쪽(${envName}) DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.`,
  queryTimeout:
    'DB 조회 시간이 제한(30초)을 넘어 비교를 중단했습니다. 거래번호 건수를 줄이거나 잠시 후 다시 시도해 주세요.',
  cancelled: '비교를 중지했습니다. 결과는 저장되지 않았습니다.',
  serverUnreachable: '서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.',

  // SC-03
  excelFailed:
    '엑셀 파일을 만들지 못했습니다. 잠시 후 다시 시도해 주세요. 비교 결과는 화면에 그대로 남아 있습니다.',
  missingBoth: 'A쪽과 B쪽 모두 해당 거래번호가 없습니다.',
  missingSide: (side: Side, envName: string) =>
    `${side}쪽(${envName}) DB에 해당 거래번호가 없습니다.`,

  /** 화면 정의서에 문구가 없는 오류 코드의 공통 안내(실행 계획 PL-14). */
  unexpected: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const;

export function pairErrorMessage(line: number, reason: PairErrorReason, side: Side = 'A'): string {
  switch (reason) {
    case 'NO_DELIMITER':
      return `${line}번째 줄: 쉼표나 탭으로 거래번호A와 거래번호B를 구분해 주세요.`;
    case 'TOO_MANY_DELIMITERS':
      return `${line}번째 줄: 한 줄에는 거래번호 두 개만 입력할 수 있습니다.`;
    case 'EMPTY_A':
      return `${line}번째 줄: 거래번호A가 비어 있습니다.`;
    case 'EMPTY_B':
      return `${line}번째 줄: 거래번호B가 비어 있습니다.`;
    case 'INVALID_NUMBER':
      // side는 프론트 파서가 숫자가 아닌 쪽으로 채운다(PA-19, WA-12).
      return `${line}번째 줄: 거래번호${side}는 숫자여야 합니다.`;
  }
}

export function tableReasonMessage(reason: TableUnsupportedReason): string {
  switch (reason) {
    case 'NO_PK':
      return 'PK가 없어 비교할 수 없습니다.';
    case 'COMPOSITE_PK':
      return '복합 PK 테이블은 지원하지 않습니다.';
    case 'UNSUPPORTED_PK_TYPE':
      return 'PK 컬럼 타입을 지원하지 않아 비교할 수 없습니다.';
  }
}

export function decryptErrorMessage(cause: DecryptErrorCause | null): string {
  return cause === 'JSON_INVALID' || cause === 'JSON_NOT_OBJECT'
    ? '복호화 오류: 복호화 결과를 JSON으로 읽을 수 없습니다.'
    : '복호화 오류: 복호화에 실패했습니다.';
}

export function missingMessage(missing: MissingSide, envNameOf: (side: Side) => string): string {
  return missing === 'BOTH'
    ? messages.missingBoth
    : messages.missingSide(missing, envNameOf(missing));
}

/** 판정·상태 텍스트 라벨(화면 정의서 4장). */
export const statusLabels = {
  MATCH: '일치',
  MISMATCH: '불일치',
  MISSING: '누락',
  DECRYPT_ERROR: '복호화 오류',
} as const;

/** 요청 실패를 화면 문구로 바꾼다. `envName`은 연결 실패 안내에 쓸 환경 이름이다. */
export function errorMessage(error: unknown, envName?: string): string {
  if (error instanceof NetworkError) return messages.serverUnreachable;
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'DB_CONNECTION_FAILED':
        return envName ? messages.connectionFailed(envName) : messages.unexpected;
      case 'QUERY_TIMEOUT':
        return messages.queryTimeout;
      case 'JOB_RUNNING':
        return messages.jobRunning;
      case 'EXCEL_FAILED':
        return messages.excelFailed;
      default:
        return messages.unexpected;
    }
  }
  return messages.unexpected;
}
