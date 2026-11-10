// INT-02 E2E 시나리오 실행 스크립트(실행 계획 INT-02).
// - 서버: backend `gradlew e2eServer`(로컬 Oracle 테스트 스키마, 가짜 복호화, dev=RO_A, test·prod=RO_B)
// - 브라우저: playwright-core + 로컬 Edge(headless). playwright-core·exceljs는 저장소 밖(PW_HOME)에 설치한다.
// 사용법: PW_HOME=<node_modules가 있는 폴더> node test/e2e/e2e-run.mjs main|broken
// 결과: test/e2e/e2e-results-<mode>.json, 스크린샷: test/e2e/screenshots/
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(join(process.env.PW_HOME ?? '.', 'package.json'));
const { chromium } = require('playwright-core');
const ExcelJS = require('exceljs');

const MODE = process.argv[2] ?? 'main';
const BASE = 'http://127.0.0.1:18080';
const HERE = dirname(fileURLToPath(import.meta.url));
const SHOTS = join(HERE, 'screenshots');
const DOWNLOADS = join(process.env.PW_HOME ?? HERE, 'downloads');
mkdirSync(SHOTS, { recursive: true });
mkdirSync(DOWNLOADS, { recursive: true });

const results = [];
function record(id, ok, note, shot) {
  results.push({ id, result: ok ? '통과' : '실패', note, screenshot: shot ?? null });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${id} - ${note}`);
}

async function shot(page, name) {
  const file = `${name}.png`;
  await page.screenshot({ path: join(SHOTS, file), fullPage: true });
  return `screenshots/${file}`;
}

const pad = (i) => String(i).padStart(6, '0');
const pairs = (list, fa = (i) => `A${pad(i)}`, fb = (i) => `B${pad(i)}`) => list.map((i) => `${fa(i)},${fb(i)}`).join('\n');
const range = (a, b) => Array.from({ length: b - a + 1 }, (_, k) => a + k);

async function setEnv(page, a, b) {
  if (a !== undefined) await page.getByLabel('A쪽 환경').selectOption(a);
  if (b !== undefined) await page.getByLabel('B쪽 환경').selectOption(b);
}

async function pickTable(page, name) {
  const input = page.getByLabel('테이블 검색');
  await input.fill(`DM_IT_OWNER.${name}`);
  await page.getByRole('option', { name: new RegExp(`DM_IT_OWNER\\.${name}$`) }).click();
  await page.getByRole('table', { name: '컬럼 목록' }).waitFor();
}

async function checkColumns(page, names) {
  for (const n of names) {
    const box = page.getByLabel(`${n} 선택`);
    if (!(await box.isChecked())) await box.check();
  }
}

async function uncheckAll(page) {
  await page.getByLabel('컬럼 검색').fill('');
  await page.getByRole('button', { name: '전체 해제' }).click();
}

async function setPairs(page, text) {
  await page.getByLabel('거래번호 입력').fill(text);
}

async function runAndWaitResult(page, expectedTotal) {
  await page.getByRole('button', { name: '비교 실행' }).click();
  await page.getByTestId('result-counts').filter({ hasText: `총 ${expectedTotal}쌍` }).waitFor({ timeout: 60_000 });
  await page.getByRole('dialog').waitFor({ state: 'detached', timeout: 10_000 });
}

async function countsText(page) {
  return (await page.getByTestId('result-counts').textContent()).replace(/\s+/g, ' ').trim();
}

async function selectPair(page, index) {
  await page.getByRole('listbox', { name: '요약 목록' }).getByRole('option').nth(index - 1).click();
}

async function safe(id, fn) {
  try {
    await fn();
  } catch (e) {
    record(id, false, `예외: ${String(e.message ?? e).split('\n')[0]}`);
  }
}

async function main(page) {
  // 서버 진행률 관찰(100쌍 단위 확인용)
  const statuses = [];
  page.on('response', async (res) => {
    if (/\/api\/compare\/[0-9a-f-]{36}$/.test(res.url()) && res.request().method() === 'GET') {
      try {
        statuses.push(await res.json());
      } catch {
        /* 무시 */
      }
    }
  });

  await page.goto(BASE);
  await safe('FE-02 수동: 환경 드롭다운', async () => {
    await page.getByLabel('A쪽 환경').locator('option').nth(3).waitFor({ state: 'attached' });
    const options = await page.getByLabel('A쪽 환경').locator('option').allTextContents();
    record('FE-02 수동: 환경 드롭다운', options.join(',') === '선택 안 함,개발,테스트,운영', `옵션: ${options.join(', ')}`);
  });

  await safe('SN-01', async () => {
    await setEnv(page, 'dev', 'dev');
    await pickTable(page, 'DM_IT_TRX_STR');
    await checkColumns(page, ['ORDER_NO', 'AMOUNT']);
    await setPairs(page, pairs(range(1, 5), (i) => `A${pad(i)}`, (i) => `A${pad(i)}`));
    await runAndWaitResult(page, 5);
    const c = await countsText(page);
    const s = await shot(page, 'sn01-same-env');
    record('SN-01', c.includes('일치 5') && c.includes('불일치 0'), `개발-개발 동일 키 5쌍: ${c}`, s);
    record('FE-03 수동: 비교 한 사이클', true, 'e2eServer에서 진행 모달 → 결과 표시까지 완료');
  });

  await safe('SN-02', async () => {
    await setEnv(page, 'dev', 'prod');
    const badge = await page.getByTestId('env-b-prod-badge').isVisible();
    const noBadgeA = !(await page.getByTestId('env-a-prod-badge').isVisible().catch(() => false));
    await setPairs(page, pairs(range(1, 12)));
    await runAndWaitResult(page, 12);
    const c = await countsText(page);
    const cond = await page.getByTestId('result-condition').textContent();
    const s = await shot(page, 'sn02-cross-env-prod-warning');
    record(
      'SN-02',
      badge && noBadgeA && c.includes('일치 7') && c.includes('불일치 4') && c.includes('누락 1') && cond.includes('B 운영'),
      `개발-운영 12쌍: ${c}, 운영 배지(B만)=${badge && noBadgeA}, 조건="${cond.trim()}"`,
      s,
    );
  });

  await safe('SN-03', async () => {
    await checkColumns(page, ['APPR_MSG']);
    await page.getByLabel('APPR_MSG 복호화', { exact: true }).check();
    await setPairs(page, pairs([1, 5, 8, 13, 17, 19]));
    await runAndWaitResult(page, 6);
    await selectPair(page, 2);
    const detail = page.getByRole('table', { name: '상세 비교' });
    await detail.getByText('APPR_MSG.approval_no').waitFor();
    const mismatchRow = detail.getByRole('row').filter({ hasText: 'APPR_MSG.approval_no' });
    const red = (await mismatchRow.getAttribute('class'))?.includes('row-mismatch');
    const s = await shot(page, 'sn03-decrypt-json-fields');
    record('SN-03', !!red, `5번 짝: APPR_MSG.approval_no 불일치 빨간 행=${red}, 필드 행 펼침 확인`, s);

    await selectPair(page, 4);
    await detail.getByText('복호화 오류: 복호화에 실패했습니다.').waitFor();
    const s2 = await shot(page, 'sn-e04-3-decrypt-failed');
    const pill4 = await page.getByRole('listbox', { name: '요약 목록' }).getByRole('option').nth(3).textContent();
    record('SN-E04-3', pill4.includes('불일치'), `13번 짝 복호화 실패 → 컬럼만 복호화 오류, 짝은 "${pill4.trim()}"`, s2);
    await selectPair(page, 5);
    await detail.getByText('복호화 오류: 복호화 결과를 JSON으로 읽을 수 없습니다.').waitFor();
    const s3 = await shot(page, 'sn-e04-4-json-invalid');
    record('SN-E04-4', true, '17번 짝 JSON 문법 오류 → "복호화 결과를 JSON으로 읽을 수 없습니다."', s3);
  });

  await safe('SN-04', async () => {
    await uncheckAll(page);
    await checkColumns(page, ['ORDER_NO', 'LONG_TEXT']);
    await setPairs(page, ['A000001,B000001', 'A000010,B000010', 'NOPE1,B000001', 'NOPE2,NOPE3', 'A000004,B000004'].join('\n'));
    await runAndWaitResult(page, 5);
    const detail = page.getByRole('table', { name: '상세 비교' });
    await detail.getByRole('button', { name: '펼치기' }).first().waitFor();
    const collapsedLen = (await detail.getByRole('row').filter({ hasText: 'LONG_TEXT' }).textContent()).length;
    await detail.getByRole('button', { name: '펼치기' }).first().click();
    const expandedLen = (await detail.getByRole('row').filter({ hasText: 'LONG_TEXT' }).textContent()).length;

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: '엑셀 다운로드' }).click(),
    ]);
    const file = join(DOWNLOADS, download.suggestedFilename());
    await download.saveAs(file);
    const wb = new ExcelJS.Workbook();
    await wb.xlsx.readFile(file);
    const names = wb.worksheets.map((w) => w.name);
    const detailSheet = wb.getWorksheet('상세');
    const header = detailSheet.getRow(1).values.slice(1).join('|');
    let redRow = false;
    let amberRow = false;
    detailSheet.eachRow((row) => {
      const status = String(row.getCell(7).value ?? '');
      const fill = row.getCell(7).fill?.fgColor?.argb ?? '';
      if (status === '불일치' && fill.endsWith('FFE0E0')) redRow = true;
      if (status.startsWith('누락') && fill.endsWith('FFF0C8')) amberRow = true;
    });
    const summarySheet = wb.getWorksheet('요약');
    const envA = summarySheet.getRow(1).getCell(2).value;
    const envB = summarySheet.getRow(2).getCell(2).value;
    const s = await shot(page, 'sn04-result-detail');
    record(
      'SN-04',
      names.join(',') === '요약,상세' && header.includes('A값(개발)') && header.includes('B값(운영)') && redRow && amberRow &&
        expandedLen > collapsedLen,
      `파일 ${download.suggestedFilename()}: 시트=${names.join(',')}, 머리글=${header}, 불일치 빨강=${redRow}, 누락 호박색=${amberRow}, 요약 환경=${envA}/${envB}, 긴 값 펼침 ${collapsedLen}→${expandedLen}자`,
      s,
    );

    await selectPair(page, 2);
    await page.getByText('B쪽(운영) DB에 해당 거래번호가 없습니다.').waitFor();
    await selectPair(page, 3);
    await page.getByText('A쪽(개발) DB에 해당 거래번호가 없습니다.').waitFor();
    const s1 = await shot(page, 'sn-e04-1-missing');
    record('SN-E04-1', true, 'B 없음·A 없음 짝이 누락(호박색)과 없는 쪽 안내로 표시되고 나머지 짝은 계속 처리', s1);
    await selectPair(page, 4);
    await page.getByText('A쪽과 B쪽 모두 해당 거래번호가 없습니다.').waitFor();
    const pill = await page.getByRole('listbox', { name: '요약 목록' }).getByRole('option').nth(3).textContent();
    record('SN-E04-2', pill.includes('누락(A·B 모두 없음)'), `양쪽 없음: "${pill.trim()}"`);

    // SN-E04-6: 서버의 엑셀 실패 응답(500 EXCEL_FAILED)을 브라우저에서 재현한다.
    await page.route('**/excel', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'EXCEL_FAILED', message: 'excel export failed', details: null }),
      }),
    );
    await page.getByRole('button', { name: '엑셀 다운로드' }).click();
    await page.getByText('엑셀 파일을 만들지 못했습니다. 잠시 후 다시 시도해 주세요. 비교 결과는 화면에 그대로 남아 있습니다.').waitFor();
    const s6 = await shot(page, 'sn-e04-6-excel-failed');
    await page.unroute('**/excel');
    const [retry] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '엑셀 다운로드' }).click()]);
    record('SN-E04-6', !!retry, '500 EXCEL_FAILED 응답(브라우저 경로 가로채기) → 안내 표시, 결과 유지, 재시도 다운로드 성공', s6);
  });

  await safe('진행률 100쌍 단위', async () => {
    await uncheckAll(page);
    await checkColumns(page, ['ORDER_NO']);
    await setPairs(page, pairs(range(1, 1000)));
    statuses.length = 0;
    await runAndWaitResult(page, 1000);
    const processed = statuses.map((s) => s.processed);
    const ok = processed.length > 0 && processed.every((p) => p % 100 === 0);
    record('진행률 100쌍 단위', ok, `1,000쌍 폴링 처리 건수: ${[...new Set(processed)].join(', ')}`);
  });

  await safe('SN-E01-4', async () => {
    await pickTable(page, 'DM_IT_SLOW');
    await checkColumns(page, ['SLOW_COL']);
    await setPairs(page, pairs(range(1, 5), (i) => `S${i}`, (i) => `S${i}`));
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByRole('dialog', { name: '비교 진행 중' }).waitFor();
    const sp = await shot(page, 'sn-progress-modal');
    record('SC-02 진행 모달 표시', true, '느린 조회 중 진행 모달(처리 0 / 전체 5쌍)', sp);
    await page.getByRole('dialog', { name: '비교 실패' }).waitFor({ timeout: 30_000 });
    const text = await page.getByRole('dialog').textContent();
    const s = await shot(page, 'sn-e01-4-timeout');
    await page.getByRole('button', { name: '닫기' }).click();
    const kept = await page.getByLabel('거래번호 입력').inputValue();
    record(
      'SN-E01-4',
      text.includes('DB 조회 시간이 제한(30초)을 넘어 비교를 중단했습니다.') && kept.startsWith('S1,S1'),
      `E2E 서버 타임아웃(8초)으로 재현: 모달 "${text.replace('비교 실패', '').replace('닫기', '').trim()}", 입력 유지`,
      s,
    );
  });

  await safe('SN-E01-5', async () => {
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByRole('dialog', { name: '비교 진행 중' }).waitFor();
    await page.waitForTimeout(1200);
    await page.getByRole('button', { name: '비교 중지' }).click();
    const cancelling = await page.getByRole('dialog', { name: '비교 중지 중' }).isVisible();
    await page.getByRole('status').filter({ hasText: '비교를 중지했습니다. 결과는 저장되지 않았습니다.' }).waitFor({ timeout: 15_000 });
    const s = await shot(page, 'sn-e01-5-cancelled');
    const kept = await page.getByLabel('거래번호 입력').inputValue();
    record('SN-E01-5', cancelling && kept.startsWith('S1,S1'), `"비교 중지 중" 표시=${cancelling}, 중지 안내, 입력 유지`, s);
  });

  await safe('SN-E01-7', async () => {
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByRole('dialog', { name: '비교 진행 중' }).waitFor();
    await page.reload();
    const emptyAfterReload = (await page.getByLabel('A쪽 환경').inputValue()) === '' && (await page.getByLabel('거래번호 입력').inputValue()) === '';
    await setEnv(page, 'dev', 'prod');
    await pickTable(page, 'DM_IT_SLOW');
    await checkColumns(page, ['SLOW_COL']);
    await setPairs(page, 'S1,S1');
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByText('이전 비교가 아직 진행 중입니다. 잠시 후 다시 시도해 주세요.').waitFor();
    const noModal = (await page.getByRole('dialog').count()) === 0;
    const s = await shot(page, 'sn-e01-7-job-running');
    record('SN-E01-7', noModal, `비교 중 새로고침 후 재실행 → WA-13 안내, 모달 없음=${noModal}`, s);
    record('SA-09 새로고침 초기화', emptyAfterReload, `새로고침 직후 환경·거래번호 입력이 비어 있음=${emptyAfterReload}`);
    await page.waitForTimeout(9000); // 이전 작업 종료 대기
  });

  await safe('SN-E02-1', async () => {
    await page.reload();
    await setEnv(page, 'dev', 'prod');
    const input = page.getByLabel('테이블 검색');
    const reasons = [];
    for (const [name, text] of [
      ['DM_IT_NOPK', 'PK가 없어 비교할 수 없습니다.'],
      ['DM_IT_COMPOSITE', '복합 PK 테이블은 지원하지 않습니다.'],
      ['DM_IT_DATEPK', 'PK 컬럼 타입을 지원하지 않아 비교할 수 없습니다.'],
    ]) {
      await input.fill(`DM_IT_OWNER.${name}`);
      const opt = page.getByRole('option', { name: new RegExp(name) });
      await opt.waitFor();
      reasons.push((await opt.textContent()).includes(text));
      await opt.dispatchEvent('click'); // aria-disabled 항목은 Playwright가 클릭을 막으므로 이벤트를 직접 보낸다
    }
    const s = await shot(page, 'sn-e02-1-unsupported-table');
    const noColumns = (await page.getByRole('table', { name: '컬럼 목록' }).count()) === 0;
    record('SN-E02-1', reasons.every(Boolean) && noColumns, `사유 3종 표시=${reasons.join('/')}, 선택되지 않음=${noColumns}`, s);
  });

  await safe('SN-E01-3', async () => {
    await page.getByLabel('테이블 검색').fill('ZZZ_NO_SUCH');
    await page.getByText('검색어와 일치하는 테이블이 없습니다.').waitFor();
    const both = await page.getByText('조회할 수 있는 테이블이 없습니다. 접속 계정의 조회 권한을 확인해 주세요.').isVisible();
    const s = await shot(page, 'sn-e01-3-no-tables');
    record('SN-E01-3', both, '검색 결과 0건 → WA-08과 SA-02 안내(보조 문구) 표시(PL-15)', s);
  });

  await safe('SN-E02-2·E03', async () => {
    await pickTable(page, 'DM_IT_TRX_STR');
    await setPairs(page, 'A000001,B000001');
    await page.getByRole('button', { name: '비교 실행' }).click();
    const e022 = await page.getByText('비교할 컬럼을 하나 이상 선택해 주세요.').isVisible();
    record('SN-E02-2', e022, '컬럼 0개 실행 → 컬럼 선택 안내, 비교 시작 안 함');

    await checkColumns(page, ['ORDER_NO']);
    await setPairs(page, 'A000001,B000001\nA000002 B000002\n,B000003\nA4,B4,C4');
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByText('입력 형식 오류가 있어 비교를 시작할 수 없습니다.', { exact: false }).first().waitFor();
    const s1 = await shot(page, 'sn-e03-1-input-errors');
    const lines = await page.getByRole('alert', { name: '입력 오류' }).textContent();
    record(
      'SN-E03-1',
      lines.includes('2번째 줄') && lines.includes('3번째 줄: 거래번호A가 비어 있습니다.') && lines.includes('4번째 줄: 한 줄에는'),
      `오류 줄 안내: ${lines}`,
      s1,
    );

    await setPairs(page, pairs(range(1, 1001)));
    await page.getByRole('button', { name: '비교 실행' }).click();
    const e032 = await page.getByText('입력한 거래 쌍이 1,000건을 초과했습니다(현재 1,001쌍).', { exact: false }).isVisible();
    record('SN-E03-2', e032, '1,001쌍 → 전체 거절 안내');

    await setPairs(page, '');
    await page.getByRole('button', { name: '비교 실행' }).click();
    const e033 = await page.getByText('거래번호를 입력해 주세요.').isVisible();
    record('SN-E03-3', e033, '빈 입력 → 입력 안내');

    // 숫자 PK(WA-12)
    await pickTable(page, 'DM_IT_TRX_NUM');
    await checkColumns(page, ['AMOUNT']);
    await setPairs(page, '1,100001\nX2,100002');
    const wa12 = await page.getByText('2번째 줄: 거래번호A는 숫자여야 합니다.').isVisible();
    record('SN-E03-1(숫자 PK)', wa12, 'NUMBER PK에 숫자가 아닌 값 → WA-12 오류 줄');
  });

  await safe('SN-E01-6', async () => {
    // 서버 통신 불가: 브라우저에서 API 요청을 끊어 재현한다.
    await setPairs(page, '1,100001');
    await page.route('**/api/**', (route) => route.abort('connectionrefused'));
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByText('서버와 연결할 수 없습니다. 프로그램이 실행 중인지 확인해 주세요.').first().waitFor();
    const kept = await page.getByLabel('거래번호 입력').inputValue();
    const s = await shot(page, 'sn-e01-6-server-unreachable');
    await page.unroute('**/api/**');
    record('SN-E01-6', kept === '1,100001', 'API 요청 차단 → WA-10 안내, 입력 유지', s);
  });
}

async function broken(page) {
  await page.goto(BASE);
  await safe('SN-E01-2', async () => {
    await setEnv(page, 'prod', 'dev');
    await page.getByLabel('테이블 검색').fill('DM_IT_TRX');
    await page.getByText('운영 DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.').waitFor({ timeout: 30_000 });
    const s = await shot(page, 'sn-e01-2-table-search-connection-failed');
    record('SN-E01-2', true, '운영 환경(닫힌 포트)으로 테이블 검색 → 연결 실패 안내, 다시 시도 가능', s);
  });
  await safe('SN-E01-1', async () => {
    await setEnv(page, 'dev', 'prod');
    await pickTable(page, 'DM_IT_TRX_STR');
    await checkColumns(page, ['ORDER_NO']);
    await setPairs(page, 'A000001,B000001');
    await page.getByRole('button', { name: '비교 실행' }).click();
    await page.getByRole('dialog', { name: '비교 실패' }).waitFor({ timeout: 30_000 });
    const text = await page.getByRole('dialog').textContent();
    const s = await shot(page, 'sn-e01-1-compare-connection-failed');
    await page.getByRole('button', { name: '닫기' }).click();
    record(
      'SN-E01-1',
      text.includes('B쪽(운영) DB에 연결하지 못했습니다.') && (await page.locator('section[aria-label="비교 결과"]').count()) === 0,
      `비교 중 B=운영(닫힌 포트) → "${text.replace('비교 실패', '').replace('닫기', '').trim()}", 부분 결과 없음`,
      s,
    );
  });
}

// 이 PC에서는 Playwright의 파이프 방식 실행이 실패해, CDP_URL이 있으면 디버깅 포트로 띄운 Edge(headless=new)에 연결한다.
//   msedge --headless=new --remote-debugging-port=9333 --user-data-dir=<임시 폴더>
const browser = process.env.CDP_URL
  ? await chromium.connectOverCDP(process.env.CDP_URL)
  : await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true, locale: 'ko-KR' });
const page = await context.newPage();
page.setDefaultTimeout(15_000);
try {
  if (MODE === 'broken') await broken(page);
  else await main(page);
} finally {
  await context.close();
  await browser.close();
  writeFileSync(join(HERE, `e2e-results-${MODE}.json`), JSON.stringify(results, null, 2), 'utf-8');
}
const failed = results.filter((r) => r.result !== '통과');
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
