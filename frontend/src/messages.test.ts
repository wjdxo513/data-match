import { ApiError, NetworkError } from './api/client';
import {
  decryptErrorMessage,
  errorMessage,
  messages,
  missingMessage,
  pairErrorMessage,
  statusLabels,
  tableReasonMessage,
} from './messages';

// FE-01: 화면 정의서(v1.5) 2장·4장의 문구와 글자 단위로 일치하는지 확인한다(문구 스냅샷).
const apiError = (code: ConstructorParameters<typeof ApiError>[1]['code'], status = 400) =>
  new ApiError(status, { code, message: 'x', details: null });

describe('메시지 맵', () => {
  it('SC-01 입력 오류 문구', () => {
    expect(messages.envNotSelected).toBe('A쪽과 B쪽 환경을 모두 선택해 주세요.');
    expect(messages.noColumns).toBe('비교할 컬럼을 하나 이상 선택해 주세요.');
    expect(messages.noPairs).toBe('거래번호를 입력해 주세요.');
    expect(messages.inputHasErrors).toBe(
      '입력 형식 오류가 있어 비교를 시작할 수 없습니다. 표시된 줄을 수정해 주세요.',
    );
    expect(pairErrorMessage(3, 'NO_DELIMITER')).toBe(
      '3번째 줄: 쉼표나 탭으로 거래번호A와 거래번호B를 구분해 주세요.',
    );
    expect(pairErrorMessage(1, 'EMPTY_A')).toBe('1번째 줄: 거래번호A가 비어 있습니다.');
    expect(pairErrorMessage(2, 'EMPTY_B')).toBe('2번째 줄: 거래번호B가 비어 있습니다.');
    expect(pairErrorMessage(4, 'TOO_MANY_DELIMITERS')).toBe(
      '4번째 줄: 한 줄에는 거래번호 두 개만 입력할 수 있습니다.',
    );
    expect(messages.inputStatus(0)).toBe('입력 0쌍 / 최대 1,000쌍');
    expect(messages.errorLineCount(2)).toBe('오류 2줄');
  });

  it('WA-11 후보 초과, WA-12 숫자 PK, WA-13 이전 비교 진행 중, 1,000쌍 초과 문구', () => {
    expect(messages.tooManyCandidates).toBe(
      '검색 결과가 많아 일부만 표시합니다. 검색어를 더 입력해 주세요.',
    );
    expect(pairErrorMessage(5, 'INVALID_NUMBER', 'A')).toBe(
      '5번째 줄: 거래번호A는 숫자여야 합니다.',
    );
    expect(pairErrorMessage(6, 'INVALID_NUMBER', 'B')).toBe(
      '6번째 줄: 거래번호B는 숫자여야 합니다.',
    );
    expect(messages.jobRunning).toBe('이전 비교가 아직 진행 중입니다. 잠시 후 다시 시도해 주세요.');
    expect(messages.pairLimitExceeded(1001)).toBe(
      '입력한 거래 쌍이 1,000건을 초과했습니다(현재 1,001쌍). 1,000쌍 이하로 나누어 입력해 주세요.',
    );
  });

  it('테이블 검색 문구', () => {
    expect(messages.connectionFailed('테스트')).toBe(
      '테스트 DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.',
    );
    expect(messages.noTables).toBe(
      '조회할 수 있는 테이블이 없습니다. 접속 계정의 조회 권한을 확인해 주세요.',
    );
    expect(messages.noMatchingTables).toBe('검색어와 일치하는 테이블이 없습니다.');
    expect(tableReasonMessage('NO_PK')).toBe('PK가 없어 비교할 수 없습니다.');
    expect(tableReasonMessage('COMPOSITE_PK')).toBe('복합 PK 테이블은 지원하지 않습니다.');
    expect(tableReasonMessage('UNSUPPORTED_PK_TYPE')).toBe(
      'PK 컬럼 타입을 지원하지 않아 비교할 수 없습니다.',
    );
  });

  it('SC-02 연결 실패·타임아웃·중지·서버 통신 불가(WA-10) 문구', () => {
    expect(messages.compareConnectionFailed('B', '운영')).toBe(
      'B쪽(운영) DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.',
    );
    expect(messages.queryTimeout).toBe(
      'DB 조회 시간이 제한(30초)을 넘어 비교를 중단했습니다. 거래번호 건수를 줄이거나 잠시 후 다시 시도해 주세요.',
    );
    expect(messages.cancelled).toBe('비교를 중지했습니다. 결과는 저장되지 않았습니다.');
    expect(messages.serverUnreachable).toBe(
      '서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.',
    );
  });

  it('SC-03 누락·복호화 오류·엑셀 실패 문구와 상태 라벨', () => {
    const envName = (side: 'A' | 'B') => (side === 'A' ? '테스트' : '운영');
    expect(missingMessage('B', envName)).toBe('B쪽(운영) DB에 해당 거래번호가 없습니다.');
    expect(missingMessage('A', envName)).toBe('A쪽(테스트) DB에 해당 거래번호가 없습니다.');
    expect(missingMessage('BOTH', envName)).toBe('A쪽과 B쪽 모두 해당 거래번호가 없습니다.');
    expect(decryptErrorMessage('DECRYPT_FAILED')).toBe('복호화 오류: 복호화에 실패했습니다.');
    expect(decryptErrorMessage('JSON_INVALID')).toBe(
      '복호화 오류: 복호화 결과를 JSON으로 읽을 수 없습니다.',
    );
    expect(decryptErrorMessage('JSON_NOT_OBJECT')).toBe(
      '복호화 오류: 복호화 결과를 JSON으로 읽을 수 없습니다.',
    );
    expect(messages.excelFailed).toBe(
      '엑셀 파일을 만들지 못했습니다. 잠시 후 다시 시도해 주세요. 비교 결과는 화면에 그대로 남아 있습니다.',
    );
    expect(statusLabels).toEqual({
      MATCH: '일치',
      MISMATCH: '불일치',
      MISSING: '누락',
      DECRYPT_ERROR: '복호화 오류',
    });
  });

  it('요청 실패를 오류 코드별 문구로 바꾼다', () => {
    expect(errorMessage(new NetworkError())).toBe(messages.serverUnreachable);
    expect(errorMessage(apiError('DB_CONNECTION_FAILED', 503), '개발')).toBe(
      messages.connectionFailed('개발'),
    );
    expect(errorMessage(apiError('QUERY_TIMEOUT', 504))).toBe(messages.queryTimeout);
    expect(errorMessage(apiError('JOB_RUNNING', 409))).toBe(messages.jobRunning);
    expect(errorMessage(apiError('EXCEL_FAILED', 500))).toBe(messages.excelFailed);
    for (const code of [
      'DB_QUERY_FAILED',
      'INTERNAL_ERROR',
      'ENV_NOT_CONFIGURED',
      'JOB_NOT_FOUND',
    ] as const) {
      expect(errorMessage(apiError(code))).toBe(messages.unexpected);
    }
    expect(errorMessage(new Error('boom'))).toBe(messages.unexpected);
  });
});
