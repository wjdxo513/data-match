# 테스트 보고서: DataMatch

## 버전 이력

| 버전 | 일자       | 작성자 | 상태 | 요약                                  |
| ---- | ---------- | ------ | ---- | ------------------------------------- |
| v0.1 | 2026-11-10 | Claude | 초안 | SETUP-05 로컬 Oracle 테스트 환경 기록 |
| v0.2 | 2026-11-10 | Claude | 초안 | INT-01 백엔드 통합 테스트 결과 추가 |
| v0.3 | 2026-11-10 | Claude | 초안 | INT-02 E2E 결과 추가 |
| v0.4 | 2026-11-10 | Claude | 초안 | INT-03 배포·성능·민감정보 결과 추가 |
| v0.5 | 2026-11-10 | Claude | 초안 | INT-04 수동 검증 기록표 추가(미검증) |
| v0.6 | 2026-11-10 | Claude | 초안 | INT-05 비교 키 변경 회귀 검증 결과 추가 |

## 변경 이력

| 버전 | 일자       | 변경 섹션 | 구분 | 변경 내용                                   | 작성자 |
| ---- | ---------- | --------- | ---- | ------------------------------------------- | ------ |
| v0.1 | 2026-11-10 | 1장       | 신규 | 로컬 Oracle 버전·서비스명·접속 확인 결과 기록 | Claude |
| v0.2 | 2026-11-10 | 2장 | 추가 | INT-01 결과 | Claude |
| v0.3 | 2026-11-10 | 3장 | 추가 | INT-02 E2E 결과와 프로토타입 비교 | Claude |
| v0.4 | 2026-11-10 | 4장 | 추가 | INT-03 결과 | Claude |
| v0.5 | 2026-11-10 | 5장 | 추가 | INT-04 기록표(사내망 PC에서 사용자 수행) | Claude |
| v0.6 | 2026-11-10 | 6장 | 추가 | INT-05 결과(비교 키 선택, 키 중복) | Claude |

> 이 보고서는 실행 계획(`docs/5-plan.md`)의 Task 검증 결과 중 환경 정보와 통합·E2E 테스트 결과를 기록한다. 비밀번호와 접속 계정의 비밀 정보는 기록하지 않는다.

## 1. 로컬 Oracle 테스트 환경 (SETUP-05)

개발 PC의 로컬 Oracle은 Oracle AI Database 26ai Free 23.26.3.0.0(SID `FREE`)이고, 테스트는 PDB 서비스 `FREEPDB1`(`localhost:1521`)에 JDBC 드라이버 ojdbc8 21.5.0.0.0으로 접속한다(PL-12 확인). pgweb-boot 로컬 테스트와 같은 DB이므로 DataMatch는 `DM_IT_OWNER`(테이블 소유자), `DM_IT_RO_A`, `DM_IT_RO_B`(읽기 전용) 사용자만 쓴다. 사용자는 2026-11-10 사용자가 관리자 계정으로 `00_create_users.sql`을 실행해 만들었고, 접속 정보는 사용자 환경 변수 `DATAMATCH_IT_*`로만 전달한다.

2026-11-10 `gradlew oracleTest` 결과 스모크 테스트 5건이 통과했다. 두 읽기 전용 사용자의 접속, `DM_IT_RO_A`의 INSERT·UPDATE·DELETE 거부와 데이터 불변, 합성 데이터 건수와 테스트 객체, `DM_IT_RO_B`의 권한 차이 테이블 조회 불가를 확인했다. 이 DB(23ai 이상)는 객체 권한이 없을 때 ORA-01031 대신 ORA-41900(missing privilege)을 반환한다. 대상 운영 DB의 버전 판정(TA-03)은 INT-04에서 한다.

## 2. 백엔드 통합 테스트 (INT-01)

2026-11-10 `gradlew oracleTest`의 `ApiIntegrationOracleTest`로 전체 애플리케이션 컨텍스트를 띄우고 MockMvc로 API를 호출해 검증했다. 결과는 10건 통과다. 테스트 구성은 다음과 같다.

- 환경: test=`DM_IT_RO_A`, prod=`DM_IT_RO_B`, dev=닫힌 포트(연결 실패 재현)
- 복호화: 가짜 `Decryptor`(`FAKEENC:` 형식)
- 쿼리 타임아웃: 3초로 줄여서 사용(운영 값은 30초)
- SQL 기록: 연결을 프록시로 감싸 실행된 문장을 모두 기록

| 항목 | 결과 |
| --- | --- |
| 판정과 입력 순서 | 서로 다른 환경(test-prod)에서 `DM_IT_TRX_STR` 20쌍을 ORDER_NO로 비교했다. 10·20은 누락(B), 4·8·12·16은 불일치, 나머지는 일치로 규칙과 같고 입력 순서도 유지됐다. A쪽 키는 `datamatch-test` 풀, B쪽 키는 `datamatch-prod` 풀로 조회됐다. 같은 환경(test-test) 5쌍은 모두 일치다. |
| PK 판정·검색 | TRX_STR·TRX_NUM은 선택 가능, COMPOSITE·NOPK·DATEPK는 사유와 함께 선택 불가다. `DM_IT_SEARCH`는 30건과 `truncated=true`, `IT_A_B`는 `DM_IT_A_B`만 찾는다. |
| 타입 변환 | T1-T2는 일치, T1-T4는 불일치, T3-T3은 NULL끼리 일치다. 상세 값은 `ab   `(CHAR 패딩), `1.1`, `2026-11-10 13:45:30`, `…30.123456789`, `… +09:00`, `DEADBEEF`, `0A0B0C`다. |
| 복호화·JSON | 5번은 `approval_no`만 불일치, 8번은 B의 `memo`가 NULL, 13번(깨진 암호문)은 `DECRYPT_ERROR`·`DECRYPT_FAILED`다. |
| 1,000쌍·초과 | 1,000쌍이 완료됐고, 폴링으로 관찰한 처리 건수는 0, 300, 800, 1000으로 모두 100 단위다. 1,001쌍은 400 `PAIR_LIMIT_EXCEEDED`(count 1001)다. |
| 숫자 PK | `X2`는 400 `INVALID_NUMBER`(2번째 줄)다. 1-100001은 일치, 3-100003은 불일치다. |
| 타임아웃 | `DM_IT_SLOW`는 약 4초 만에 `FAILED`·`QUERY_TIMEOUT`(A쪽)이 되고, 요약은 409다. |
| 중지 | 느린 조회 중 중지하면 0.7초 만에 `CANCELLED`가 되고(ORA-01013), 오류 코드는 없으며 요약은 409다. |
| 연결 실패·권한 차이 | B=dev(닫힌 포트)는 `DB_CONNECTION_FAILED`, B쪽, dev다. `DM_IT_NO_B_GRANT`는 B(RO_B)에서 ORA-00942로 `DB_QUERY_FAILED`, B쪽이다. |
| SQL 기록 | 기록된 42개 문장이 모두 `SELECT`로 시작하고, `createStatement` 호출이 없다. |
| 로그 | 출력 캡처에 테스트 계정 비밀번호와 거래번호가 없다. 로그에는 환경 ID, 오류 코드, SQLState, 작업 ID(UUID)만 있다. |

## 3. E2E 테스트 (INT-02)

2026-11-10 E2E 서버와 브라우저로 사용자 시나리오를 실행했다. 결과는 27건 모두 통과다.

**환경 구성**

- 서버: `gradlew e2eServer`로 로컬 Oracle 테스트 스키마와 가짜 복호화를 써서 `127.0.0.1:18080`에 띄웠다. 환경은 dev=`DM_IT_RO_A`, test·prod=`DM_IT_RO_B`다. 연결 실패 시나리오는 `-Pe2eBrokenEnv=prod`로 운영 환경을 닫힌 포트로 바꿔 서버를 다시 띄워서 실행했다.
- 쿼리 타임아웃: E2E 서버에서만 8초로 줄여 타임아웃을 재현했다(운영 값은 30초).
- 브라우저: 로컬 Edge 157을 headless로 띄우고 `playwright-core` 1.65로 CDP에 연결해 실행했다. Playwright MCP는 연결 시간 초과로 쓸 수 없었다. 또 이 PC에서는 Playwright가 브라우저를 직접 실행하는 방식이 실패해서, 디버깅 포트로 띄운 Edge에 연결했다.
- 스크립트: `test/e2e/e2e-run.mjs`
- 원본 결과: `e2e-results-main.json`, `e2e-results-broken.json`

**결과**

| 항목 | 결과 | 내용 | 스크린샷 |
| --- | --- | --- | --- |
| FE-02 수동: 환경 드롭다운 | 통과 | 옵션: 선택 안 함, 개발, 테스트, 운영 | - |
| SN-01 | 통과 | 개발-개발 동일 키 5쌍: 총 5쌍 · 일치 5 · 불일치 0 · 누락 0 | [sn01-same-env.png](screenshots/sn01-same-env.png) |
| FE-03 수동: 비교 한 사이클 | 통과 | e2eServer에서 진행 모달 → 결과 표시까지 완료 | - |
| SN-02 | 통과 | 개발-운영 12쌍: 총 12쌍 · 일치 7 · 불일치 4 · 누락 1, 운영 배지(B만)=true, 조건="A 개발B 운영DM_IT_OWNER.DM_IT_TRX_STR · 비교 컬럼 2개" | [sn02-cross-env-prod-warning.png](screenshots/sn02-cross-env-prod-warning.png) |
| SN-03 | 통과 | 5번 짝: APPR_MSG.approval_no 불일치 빨간 행=true, 필드 행 펼침 확인 | [sn03-decrypt-json-fields.png](screenshots/sn03-decrypt-json-fields.png) |
| SN-E04-3 | 통과 | 13번 짝 복호화 실패 → 컬럼만 복호화 오류, 짝은 "4A000013 - B000013불일치(1)" | [sn-e04-3-decrypt-failed.png](screenshots/sn-e04-3-decrypt-failed.png) |
| SN-E04-4 | 통과 | 17번 짝 JSON 문법 오류 → "복호화 결과를 JSON으로 읽을 수 없습니다." | [sn-e04-4-json-invalid.png](screenshots/sn-e04-4-json-invalid.png) |
| SN-04 | 통과 | 파일 datamatch_dev-prod_DM_IT_TRX_STR_20261110-144932.xlsx: 시트=요약,상세, 머리글=순번/거래번호A/거래번호B/컬럼·필드/A값(개발)/B값(운영)/상태, 불일치 빨강=true, 누락 호박색=true, 요약 환경=개발(dev)/운영(prod), 긴 값 펼침 261→40139자 | [sn04-result-detail.png](screenshots/sn04-result-detail.png) |
| SN-E04-1 | 통과 | B 없음·A 없음 짝이 누락(호박색)과 없는 쪽 안내로 표시되고 나머지 짝은 계속 처리 | [sn-e04-1-missing.png](screenshots/sn-e04-1-missing.png) |
| SN-E04-2 | 통과 | 양쪽 없음: "4NOPE2 - NOPE3누락(A·B 모두 없음)" | - |
| SN-E04-6 | 통과 | 500 EXCEL_FAILED 응답(브라우저 경로 가로채기) → 안내 표시, 결과 유지, 재시도 다운로드 성공 | [sn-e04-6-excel-failed.png](screenshots/sn-e04-6-excel-failed.png) |
| 진행률 100쌍 단위 | 통과 | 1,000쌍 폴링 처리 건수: 1000 | - |
| SC-02 진행 모달 표시 | 통과 | 느린 조회 중 진행 모달(처리 0 / 전체 5쌍) | [sn-progress-modal.png](screenshots/sn-progress-modal.png) |
| SN-E01-4 | 통과 | E2E 서버 타임아웃(8초)으로 재현: 모달 "DB 조회 시간이 제한(30초)을 넘어 비교를 중단했습니다. 거래번호 건수를 줄이거나 잠시 후 다시 시도해 주세요.", 입력 유지 | [sn-e01-4-timeout.png](screenshots/sn-e01-4-timeout.png) |
| SN-E01-5 | 통과 | "비교 중지 중" 표시=true, 중지 안내, 입력 유지 | [sn-e01-5-cancelled.png](screenshots/sn-e01-5-cancelled.png) |
| SN-E01-7 | 통과 | 비교 중 새로고침 후 재실행 → WA-13 안내, 모달 없음=true | [sn-e01-7-job-running.png](screenshots/sn-e01-7-job-running.png) |
| SA-09 새로고침 초기화 | 통과 | 새로고침 직후 환경·거래번호 입력이 비어 있음=true | - |
| SN-E02-1 | 통과 | 사유 3종 표시=true/true/true, 선택되지 않음=true | [sn-e02-1-unsupported-table.png](screenshots/sn-e02-1-unsupported-table.png) |
| SN-E01-3 | 통과 | 검색 결과 0건 → WA-08과 SA-02 안내(보조 문구) 표시(PL-15) | [sn-e01-3-no-tables.png](screenshots/sn-e01-3-no-tables.png) |
| SN-E02-2 | 통과 | 컬럼 0개 실행 → 컬럼 선택 안내, 비교 시작 안 함 | - |
| SN-E03-1 | 통과 | 오류 줄 안내: 2번째 줄: 쉼표나 탭으로 거래번호A와 거래번호B를 구분해 주세요.3번째 줄: 거래번호A가 비어 있습니다.4번째 줄: 한 줄에는 거래번호 두 개만 입력할 수 있습니다. | [sn-e03-1-input-errors.png](screenshots/sn-e03-1-input-errors.png) |
| SN-E03-2 | 통과 | 1,001쌍 → 전체 거절 안내 | - |
| SN-E03-3 | 통과 | 빈 입력 → 입력 안내 | - |
| SN-E03-1(숫자 PK) | 통과 | NUMBER PK에 숫자가 아닌 값 → WA-12 오류 줄 | - |
| SN-E01-6 | 통과 | API 요청 차단 → WA-10 안내, 입력 유지 | [sn-e01-6-server-unreachable.png](screenshots/sn-e01-6-server-unreachable.png) |
| SN-E01-2 | 통과 | 운영 환경(닫힌 포트)으로 테이블 검색 → 연결 실패 안내, 다시 시도 가능 | [sn-e01-2-table-search-connection-failed.png](screenshots/sn-e01-2-table-search-connection-failed.png) |
| SN-E01-1 | 통과 | 비교 중 B=운영(닫힌 포트) → "B쪽(운영) DB에 연결하지 못했습니다. 접속 설정을 확인한 뒤 다시 시도해 주세요.", 부분 결과 없음 | [sn-e01-1-compare-connection-failed.png](screenshots/sn-e01-1-compare-connection-failed.png) |

**결과에 대한 보충 설명**

- SN-E04-5(짝 단위로 계속 처리하는 오류와 전체 실패의 구분)는 따로 실행하지 않았다. 위 결과로 확인된다. 누락(SN-E04-1·2)과 복호화 오류(SN-E04-3·4)는 짝 단위로 계속 처리되고, 연결 실패(SN-E01-1)와 타임아웃(SN-E01-4)은 부분 결과 없는 전체 실패다. 통과로 본다.
- 브라우저에서 재현한 항목이 둘 있다.
  - SN-E01-6(서버 통신 불가): API 요청을 브라우저에서 끊어 재현했다.
  - SN-E04-6(엑셀 실패): 서버의 500 `EXCEL_FAILED` 응답을 경로 가로채기로 재현했다. 서버 쪽의 `EXCEL_FAILED` 응답과 결과 유지는 BE-07 MockMvc 테스트로 확인했다.
- 진행률: E2E의 1,000쌍은 1초 안에 끝나서 폴링으로 관찰한 처리 건수가 1000 하나뿐이다. 중간 값(0, 300, 800)은 INT-01에서 관찰했고 모두 100 단위다.
- 타임아웃 문구: 안내 문구의 "제한(30초)"은 운영 기준 문구다. E2E에서는 8초 타임아웃으로 같은 동작을 확인했다.
- 엑셀 검증: 내려받은 엑셀은 `exceljs`로 읽었다. 확인한 내용은 시트 "요약"·"상세", 머리글의 환경명 `A값(개발)`·`B값(운영)`, 불일치 행의 빨간 배경, 누락 행의 호박색 배경, 요약의 환경 기록 `개발(dev)`/`운영(prod)`이다. 파일은 저장소 밖 임시 폴더에 저장했다.

**프로토타입 이미지 비교**

`docs/prototype/screenshots/` 01·02·04와 `sn02`·`sn03`·`sn-e03-1`·`sn04` 캡처를 비교했다. 같은 구성은 다음과 같다.

- 환경 바: A·B 드롭다운과 운영 배지
- 설정 카드: 테이블 입력과 PK 표시, 컬럼 검색과 전체 선택/해제, 컬럼 표의 복호화 지정, 거래번호 입력창과 오른쪽 입력 현황·[비교 실행]
- 결과 카드: 머리글의 A/B 환경 태그·테이블·컬럼 수·건수, 왼쪽 요약 목록의 판정 배지, 오른쪽 상세 표의 빨간 불일치 행과 환경명 머리글

달라진 점은 둘이다.

- 테이블 입력 옆에 "선택: 소유자.테이블"을 함께 표시한다(SC01-05).
- 전체 페이지 캡처에서는 스크롤 위치에 고정된 환경 바가 중간에 찍힌다. 캡처 방식 때문이며 화면 결함이 아니다.

**발견한 결함:** 없음. 스크립트 수정 2건이 있었고 둘 다 앱 결함은 아니다. 하나는 복호화 체크박스를 찾는 이름이 모호했던 것이고, 다른 하나는 비활성 후보를 클릭하는 방식이었다.

**개인정보·합성 데이터:** 보고서와 스크린샷에 쓴 거래번호와 값은 모두 합성 데이터(`A000001`, `ORD-1`, `합성 메모 5` 등)다.

## 4. 배포 zip·성능·민감정보 (INT-03)

2026-11-10 배포 zip을 실제로 풀어 실행하고 측정했다.

**실행 방법**

- 배포 파일: `gradlew distZip`로 만든 `datamatch-0.1.0.zip`(40.9MB)이다. `verifyDistZip` 검사를 통과했다(SafeDB jar 9개, `static/index.html` 포함, 민감 설정 없음).
- 실행: 새 임시 폴더에 압축을 풀고 `start.bat`으로 실행했다. `JAVA_OPTS`는 기본값 `-Xmx512m`이다.
- 설정: `config/datamatch.properties`는 합성 값(`SYNTH_USER_7731` / `SYNTH-PW-7731`, 닫힌 포트)으로 만들었다. test·prod의 로컬 Oracle 테스트 계정은 실행 시 환경 변수로만 넘겼다.

| 항목 | 결과 |
| --- | --- |
| 기동 | `start.bat`으로 기동하고 `/api/environments`가 개발·테스트·운영을 반환한다. `/`는 프론트 화면(index.html)을 반환한다. 통과. |
| 바인딩 | `netstat`: `TCP 127.0.0.1:18080 LISTENING` 한 줄뿐이고 0.0.0.0 바인딩은 없다. 통과. |
| 민감정보 검색 | 다음 문자열이 `logs/datamatch.log`, 콘솔 표준 출력, 표준 오류에서 모두 0건이다. 통과.<br>· 합성 거래번호 `SYNTHTRX-ZZ9`(50쌍 비교 입력에 포함)<br>· 합성 비밀번호 `SYNTH-PW-7731`, 합성 계정 `SYNTH_USER_7731`<br>· 테스트 계정 비밀번호 2종(값은 출력하지 않고 건수만 셈)<br>· 거래번호 `A000001`, 컬럼 값 `ORD-1`, `jdbc:oracle` |
| 배포 구성 | zip 항목은 `datamatch.jar`, `README.txt`, `start.bat`, `config/datamatch.properties.example`, `lib/`뿐이다. `SafeDBMap_config.xml`, `SafeDBAccess.properties`, 실제 `datamatch.properties`는 없다. 통과. |

**처리 시간과 힙 사용량**

로컬 Oracle 합성 데이터로 측정했다. 비교 컬럼은 ORDER_NO, AMOUNT, STATUS, MEMO, APPR_MSG(CLOB), CARD_ENC(CLOB), CREATED_AT이다. 복호화는 지정하지 않았다(SafeDB 미사용). 힙은 `jstat -gc`를 100ms 간격으로 샘플링한 사용량(S0U+S1U+EU+OU)의 최댓값이다.

| 쌍 수 | 상태 | 비교 처리 시간 | 엑셀 생성 | 최대 힙 | 결과 건수 |
| --- | --- | --- | --- | --- | --- |
| 50 | COMPLETED | 3.3초(첫 연결 풀 생성 포함) | 1.8초 / 16KB | 106MB | 일치 24 · 불일치 21 · 누락 5 |
| 500 | COMPLETED | 1.9초 | 1.4초 / 119KB | 79MB | 일치 263 · 불일치 187 · 누락 50 |
| 1,000 | COMPLETED | 2.1초 | 1.4초 / 231KB | 270MB | 일치 531 · 불일치 369 · 누락 100 |

- `-Xmx512m`에서 `OutOfMemoryError`는 없었다.
- 청크 100쌍과 힙 512MB를 바꿀 근거가 없어 초기값을 유지한다(기술 설계서 TA-09 v1.9에 기록).
- **미검증:** 실제 DB와 SafeDB 복호화를 포함한 NFR-01 판정(50쌍 10초, 500쌍 60초)은 사내망 PC에서 하는 INT-04 M-05의 범위다.

## 5. 실환경 수동 검증 (INT-04)

이 항목은 사내망 담당자 PC에서 사용자가 수행하고 결과를 기록한다(실행 계획 INT-04, PL-13). 개발 PC는 외부망이라 실제 DB와 SafeDB에 연결할 수 없어 아래 항목은 모두 **미검증**이다. 실제 거래번호, 복호화 평문, 접속 정보는 기록하지 않고 결과·일자·수행자만 적는다.

| 번호 | 확인 항목(요약) | 결과 | 일자 | 수행자 | 비고 |
| --- | --- | --- | --- | --- | --- |
| M-01 | 개발 환경 접속, Oracle 버전 기록, ojdbc8 호환(TA-03) | 미검증 | | | |
| M-02 | 개발·테스트·운영 계정 쓰기 권한 없음(NFR-03) | 미검증 | | | |
| M-03 | 환경별 KDKEY·PANEN 복호화, JSON 필드, 한글 정합(TA-02, TA-07) | 미검증 | | | |
| M-04 | 테스트-운영 비교, 운영 경고, 결과 일관성(PA-14, R-05) | 미검증 | | | |
| M-05 | 50쌍·500쌍 처리 시간과 메모리, NFR-01 판정(TA-10, TA-09) | 미검증 | | | |
| M-06 | 순차 복호화 병목 여부(TA-11) | 미검증 | | | |
| M-07 | 테이블 검색의 시스템 스키마 포함 불편 여부(TA-13) | 미검증 | | | 개발 PC에서 SYS 테이블이 검색됨을 확인(DB-02) |
| M-08 | 권한 없는 테이블, 대용량 CLOB, 특수 타입 동작(TA-05, TA-08) | 미검증 | | | 로컬 Oracle에서 ORA-00942→`DB_QUERY_FAILED`, INTERVAL→문자열 확인(INT-01, DB-03) |
| M-09 | 담당자 PC 압축 해제, JDK 11, 설정 전달, 첫 실행(NFR-08) | 미검증 | | | 개발 PC에서 zip 실행 확인(INT-03) |
| M-10 | 사내망 SafeDB `login()`, 설정 파일 탐색 위치, KDKEY·PANEN 왕복, 연결 불가 시 대기 시간(TA-01·02·07·14) | 미검증 | | | |

## 6. 비교 키 변경 회귀 검증 (INT-05)

2026-11-10 사내망 테스트에서 비교 대상 테이블이 복합 PK(사실상 유일하게 쓰는 control_no 포함)로 확인되었다. 이에 따라 PRD v1.4(PA-10 변경)로 비교 키 컬럼 선택과 "키 중복" 판정을 추가하고(DB-04, BE-08, FE-05) 전체를 다시 검증했다. 결과는 모두 통과다.

**테스트 데이터:** 로컬 Oracle 합성 데이터의 `DM_IT_COMPOSITE`(PK K1, K2)에 행 3개를 추가했다. 첫 번째 PK 컬럼 K1을 비교 키로 쓰면 C1은 1건, C2는 2건이다.

| 구분 | 결과 |
| --- | --- |
| 백엔드 단위 | 164건 통과, 라인 커버리지 97.8%(1114/1139) |
| 로컬 Oracle 통합 | 31건 통과 |
| 복합 PK API 통합 | K1로 비교하면 C1 일치, C2 `DUPLICATE`(A 2건·B 2건), C9 누락이다. 비교 키 없음·DATE 키·없는 키는 400이고, PK 없음 테이블은 ID 키로 비교된다. |
| 프론트 | Vitest 85건 통과 |
| E2E | 정상 26건과 연결 실패 2건이 통과했다(원본 결과 `e2e-results-main.json`, `e2e-results-broken.json` 갱신). |
| E2E 신규 SN-E02-1 | `DM_IT_COMPOSITE`를 선택할 수 있고 비교 키 기본값은 "선택 안 함"이다. 후보는 K1 (PK)·K2 (PK)·VAL이고, 비교 키 없이 실행하면 안내만 나온다. [sn-e02-1-key-not-selected.png](screenshots/sn-e02-1-key-not-selected.png) |
| E2E 신규 SN-E04-7 | 비교 키 K1에서 라벨은 "일치", "키 중복(A 2건·B 2건)"(호박색)이다. 상세에는 키 중복 안내가 나오고 비교 표는 없다. 머리글은 "키 중복 1"과 "비교 키 K1"이다. 엑셀 상세 시트에 호박색 "키 중복(A 2건·B 2건)" 행이, 요약 시트에 "비교 키=K1"이 있다. [sn-e04-7-duplicate-key.png](screenshots/sn-e04-7-duplicate-key.png) |
| 배포 zip | 다시 만들었고 `verifyDistZip`이 통과했다(SafeDB jar 9개, 정적 화면 포함). |

3장의 이전 SN-E02-1(선택 불가 테이블 사유 표시)은 PRD v1.4에서 동작이 바뀌어 이 결과로 대체한다. **미검증:** 실제 업무 테이블(control_no)에서 비교 키 조회 성능은 사내망 PC의 INT-04 M-05에서 확인한다(PRD PA-20).
