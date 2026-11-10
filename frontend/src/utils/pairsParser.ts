// FE-01: 거래번호 짝 입력 파서. 백엔드 PairsParser와 같은 규칙이며(기술 설계서 5장 FR-05)
// 두 구현은 test/fixtures/pairs-cases.json을 함께 쓴다. 화면의 실시간 건수·오류 표시용이고 최종 검증은 서버가 한다.
import type { PairLineError, Side } from '../types';

export const MAX_PAIRS = 1000;

export interface ParsedPair {
  line: number;
  a: string;
  b: string;
}

/** 오류 줄. `side`는 INVALID_NUMBER에서 숫자가 아닌 쪽(둘 다면 A)으로, 화면 문구용이다(WA-12). */
export interface ParsedLineError extends PairLineError {
  side?: Side;
}

export interface PairsParseResult {
  pairs: ParsedPair[];
  errors: ParsedLineError[];
  limitExceeded: boolean;
}

const NUMBER = /^[+-]?(\d+(\.\d*)?|\.\d+)$/;

// Java String.strip()과 같은 공백 집합(Character.isWhitespace). JS trim()은 줄바꿈 없는 공백(U+00A0 등)도 지워 결과가 달라진다.
const WS =
  '[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]';
const STRIP = new RegExp(`^${WS}+|${WS}+$`, 'g');

export function strip(value: string): string {
  return value.replace(STRIP, '');
}

/** PK 타입이 NUMBER이면 숫자 형식을 검사한다(PRD PA-19). */
export function isNumericPkType(type: string | null | undefined): boolean {
  return !!type && /^NUMBER\b/i.test(type);
}

export function parsePairs(text: string, numericPk: boolean): PairsParseResult {
  const pairs: ParsedPair[] = [];
  const errors: ParsedLineError[] = [];
  if (text) {
    text.split(/\r?\n/).forEach((line, i) => {
      const parsed = parseLine(i + 1, line, numericPk);
      if (parsed && 'reason' in parsed) errors.push(parsed);
      else if (parsed) pairs.push(parsed);
    });
  }
  return { pairs, errors, limitExceeded: pairs.length > MAX_PAIRS };
}

/** 공백 줄이면 null. 한 줄에는 대표 오류 하나만 낸다(구분자 없음 > 2개 이상 > A 빔 > B 빔 > 숫자 형식). */
function parseLine(
  line: number,
  text: string,
  numericPk: boolean,
): ParsedPair | ParsedLineError | null {
  if (strip(text) === '') {
    return null;
  }
  let delimiters = 0;
  let position = -1;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === ',' || text[i] === '\t') {
      delimiters++;
      if (position < 0) position = i;
    }
  }
  if (delimiters === 0) return { line, reason: 'NO_DELIMITER' };
  if (delimiters > 1) return { line, reason: 'TOO_MANY_DELIMITERS' };
  const a = strip(text.substring(0, position));
  const b = strip(text.substring(position + 1));
  if (a === '') return { line, reason: 'EMPTY_A' };
  if (b === '') return { line, reason: 'EMPTY_B' };
  if (numericPk && !NUMBER.test(a)) return { line, reason: 'INVALID_NUMBER', side: 'A' };
  if (numericPk && !NUMBER.test(b)) return { line, reason: 'INVALID_NUMBER', side: 'B' };
  return { line, a, b };
}
