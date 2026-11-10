import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { MAX_PAIRS, isNumericPkType, parsePairs, strip } from './pairsParser';

// FE-01: 백엔드 PairsParserTest와 같은 공유 케이스 표로 검증한다(실행 계획 BE-02, FE-01).
interface FixtureCase {
  name: string;
  numericPk: boolean;
  input?: string;
  repeat?: { count: number; template: string };
  expected: {
    pairs?: [number, string, string][];
    errors: [number, string][];
    pairCount: number;
    limitExceeded: boolean;
  };
}

const fixture = JSON.parse(
  readFileSync(resolve(__dirname, '../../../test/fixtures/pairs-cases.json'), 'utf-8'),
) as { maxPairs: number; cases: FixtureCase[] };

function inputOf(c: FixtureCase): string {
  if (!c.repeat) return c.input ?? '';
  const { count, template } = c.repeat;
  return Array.from({ length: count }, (_, i) => template.split('{n}').join(String(i + 1))).join(
    '\n',
  );
}

describe('pairsParser 공유 케이스(test/fixtures/pairs-cases.json)', () => {
  it('최대 쌍 수가 fixture와 같다', () => {
    expect(MAX_PAIRS).toBe(fixture.maxPairs);
    expect(fixture.cases.length).toBeGreaterThan(0);
  });

  it.each(fixture.cases.map((c) => [c.name, c] as const))('%s', (_, c) => {
    const result = parsePairs(inputOf(c), c.numericPk);
    expect(result.errors.map((e) => [e.line, e.reason])).toEqual(c.expected.errors);
    expect(result.pairs.length).toBe(c.expected.pairCount);
    expect(result.limitExceeded).toBe(c.expected.limitExceeded);
    if (c.expected.pairs) {
      expect(result.pairs.map((p) => [p.line, p.a, p.b])).toEqual(c.expected.pairs);
    }
  });
});

describe('pairsParser 프론트 전용 동작', () => {
  it('숫자 PK 오류에 숫자가 아닌 쪽을 기록한다(둘 다면 A)', () => {
    const { errors } = parsePairs('1,ABC\nX1,2\nX,Y', true);
    expect(errors).toEqual([
      { line: 1, reason: 'INVALID_NUMBER', side: 'B' },
      { line: 2, reason: 'INVALID_NUMBER', side: 'A' },
      { line: 3, reason: 'INVALID_NUMBER', side: 'A' },
    ]);
  });

  it('Java String.strip()과 같은 공백만 제거한다', () => {
    expect(strip(' \t　A1  ')).toBe('A1');
    expect(strip(' A1 ')).toBe(' A1 ');
  });

  it('NUMBER 타입만 숫자 PK로 본다', () => {
    expect(isNumericPkType('NUMBER')).toBe(true);
    expect(isNumericPkType('NUMBER(12)')).toBe(true);
    expect(isNumericPkType('VARCHAR2(20)')).toBe(false);
    expect(isNumericPkType(null)).toBe(false);
  });
});
