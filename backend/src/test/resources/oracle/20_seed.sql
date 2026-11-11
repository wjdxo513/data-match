-- DataMatch 로컬 Oracle 테스트 합성 데이터 (SETUP-05)
-- DM_IT_OWNER 로 10_schema.sql 다음에 실행한다. 모든 값은 합성 데이터이며 실제 거래 정보가 아니다.
--
-- 가짜 암호문 형식(테스트용 FakeDecryptor와 약속): 'FAKEENC:' 뒤에 평문을 그대로 둔다.
--   - 'FAKEENC:' 로 시작하지 않으면 복호화 실패(DECRYPT_FAILED)
--   - 평문이 JSON 문법 오류면 JSON_INVALID, JSON 객체가 아니면 JSON_NOT_OBJECT
--
-- DM_IT_TRX_STR 짝 규칙(i = 1..1100, A 키 'A' || LPAD(i,6,'0'), B 키 'B' || LPAD(i,6,'0')):
--   - MOD(i,10)=0 : B 행 없음(누락)
--   - MOD(i,4)=0  : ORDER_NO 불일치
--   - MOD(i,6)=0  : AMOUNT 불일치(B = A + 1)
--   - MOD(i,9)=0  : MEMO 양쪽 NULL(일치)
--   - MOD(i,5)=0  : APPR_MSG.approval_no 불일치
--   - MOD(i,8)=0  : B 쪽 APPR_MSG 에 memo 키 없음(없는 쪽 NULL로 비교)
--   - i=13 : A 쪽 APPR_MSG 복호화 실패, i=17 : JSON 문법 오류, i=19 : JSON 객체 아님
--   - i=1  : LONG_TEXT 에 40,000자 값(긴 값 표시·엑셀 셀 한도 확인)

DECLARE
  v_json_a VARCHAR2(400);
  v_json_b VARCHAR2(400);
  v_long   CLOB;
BEGIN
  DBMS_LOB.CREATETEMPORARY(v_long, TRUE);
  FOR k IN 1 .. 40 LOOP
    DBMS_LOB.WRITEAPPEND(v_long, 1000, RPAD('L', 1000, 'L'));
  END LOOP;

  FOR i IN 1 .. 1100 LOOP
    v_json_a := 'FAKEENC:{"res_cd":"0000","amount":' || (i * 100) || ',"approval_no":"' || (30000000 + i)
                || '","memo":"합성 메모 ' || i || '"}';
    v_json_b := 'FAKEENC:{"res_cd":"0000","amount":' || (i * 100) || ',"approval_no":"'
                || CASE WHEN MOD(i, 5) = 0 THEN 39000000 + i ELSE 30000000 + i END || '"'
                || CASE WHEN MOD(i, 8) = 0 THEN '' ELSE ',"memo":"합성 메모 ' || i || '"' END || '}';
    IF i = 13 THEN v_json_a := 'BROKEN-CIPHER'; END IF;
    IF i = 17 THEN v_json_a := 'FAKEENC:{"res_cd":'; END IF;
    IF i = 19 THEN v_json_a := 'FAKEENC:[1,2,3]'; END IF;

    INSERT INTO DM_IT_TRX_STR (TRX_NO, ORDER_NO, AMOUNT, STATUS, MEMO, APPR_MSG, CARD_ENC, LONG_TEXT, CREATED_AT)
    VALUES ('A' || LPAD(i, 6, '0'), 'ORD-' || i, i * 100, '00',
            CASE WHEN MOD(i, 9) = 0 THEN NULL ELSE 'memo ' || i END,
            v_json_a, 'FAKEENC:{"card_no":"4000-0000-0000-' || LPAD(i, 4, '0') || '"}',
            CASE WHEN i = 1 THEN v_long ELSE NULL END,
            DATE '2026-11-01' + MOD(i, 30));

    IF MOD(i, 10) <> 0 THEN
      INSERT INTO DM_IT_TRX_STR (TRX_NO, ORDER_NO, AMOUNT, STATUS, MEMO, APPR_MSG, CARD_ENC, LONG_TEXT, CREATED_AT)
      VALUES ('B' || LPAD(i, 6, '0'),
              CASE WHEN MOD(i, 4) = 0 THEN 'ORD-B-' || i ELSE 'ORD-' || i END,
              CASE WHEN MOD(i, 6) = 0 THEN i * 100 + 1 ELSE i * 100 END, '00',
              CASE WHEN MOD(i, 9) = 0 THEN NULL ELSE 'memo ' || i END,
              v_json_b, 'FAKEENC:{"card_no":"4000-0000-0000-' || LPAD(i, 4, '0') || '"}',
              CASE WHEN i = 1 THEN v_long ELSE NULL END,
              DATE '2026-11-01' + MOD(i, 30));
    END IF;
  END LOOP;

  -- NUMBER PK: A 키 i, B 키 100000+i, MOD(i,3)=0 이면 AMOUNT 불일치
  FOR i IN 1 .. 1100 LOOP
    INSERT INTO DM_IT_TRX_NUM (TRX_ID, ORDER_NO, AMOUNT) VALUES (i, 'ORD-' || i, i + 0.5);
    INSERT INTO DM_IT_TRX_NUM (TRX_ID, ORDER_NO, AMOUNT)
    VALUES (100000 + i, 'ORD-' || i, CASE WHEN MOD(i, 3) = 0 THEN i + 0.75 ELSE i + 0.5 END);
  END LOOP;

  DBMS_LOB.FREETEMPORARY(v_long);
END;
/

INSERT INTO DM_IT_COMPOSITE (K1, K2, VAL) VALUES ('K1', 'K2', 'v')
/
-- 비교 키 선택(PRD v1.4 PA-10): 첫 번째 PK 컬럼 K1을 비교 키로 쓰면 C1은 유일, C2는 2건(키 중복)이다
INSERT INTO DM_IT_COMPOSITE (K1, K2, VAL) VALUES ('C1', '1', 'x1')
/
INSERT INTO DM_IT_COMPOSITE (K1, K2, VAL) VALUES ('C2', '1', 'x2')
/
INSERT INTO DM_IT_COMPOSITE (K1, K2, VAL) VALUES ('C2', '2', 'x3')
/
INSERT INTO DM_IT_NOPK (ID, VAL) VALUES ('N1', 'v')
/
INSERT INTO DM_IT_DATEPK (D, VAL) VALUES (DATE '2026-11-01', 'v')
/

-- 타입 테이블: T1과 T2는 같은 값, T3은 대부분 NULL, T4는 T1과 표기만 다른 값
INSERT INTO DM_IT_TYPES VALUES ('T1', 'ab', 'Hello World', N'가나', N'한글 텍스트', 1.10, 1234.5, 3.25,
  1.5, TO_DATE('2026-11-10 13:45:30', 'YYYY-MM-DD HH24:MI:SS'),
  TO_TIMESTAMP('2026-11-10 13:45:30.123456789', 'YYYY-MM-DD HH24:MI:SS.FF9'),
  TO_TIMESTAMP_TZ('2026-11-10 13:45:30.123456789 +09:00', 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),
  TO_TIMESTAMP('2026-11-10 13:45:30', 'YYYY-MM-DD HH24:MI:SS'),
  TO_CLOB('긴 텍스트 값'), TO_NCLOB(N'한글 NCLOB'), HEXTORAW('DEADBEEF'), HEXTORAW('0A0B0C'),
  INTERVAL '1 02:03:04' DAY TO SECOND)
/
INSERT INTO DM_IT_TYPES VALUES ('T2', 'ab', 'Hello World', N'가나', N'한글 텍스트', 1.1, 1234.50, 3.25,
  1.5, TO_DATE('2026-11-10 13:45:30', 'YYYY-MM-DD HH24:MI:SS'),
  TO_TIMESTAMP('2026-11-10 13:45:30.123456789', 'YYYY-MM-DD HH24:MI:SS.FF9'),
  TO_TIMESTAMP_TZ('2026-11-10 13:45:30.123456789 +09:00', 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),
  TO_TIMESTAMP('2026-11-10 13:45:30', 'YYYY-MM-DD HH24:MI:SS'),
  TO_CLOB('긴 텍스트 값'), TO_NCLOB(N'한글 NCLOB'), HEXTORAW('DEADBEEF'), HEXTORAW('0A0B0C'),
  INTERVAL '1 02:03:04' DAY TO SECOND)
/
INSERT INTO DM_IT_TYPES (ID) VALUES ('T3')
/
INSERT INTO DM_IT_TYPES VALUES ('T4', 'AB', 'hello world', N'가나', N'한글 텍스트', 1.2, 1234.5, 3.25,
  2.5, TO_DATE('2026-11-10 13:45:31', 'YYYY-MM-DD HH24:MI:SS'),
  TO_TIMESTAMP('2026-11-10 13:45:30.123456780', 'YYYY-MM-DD HH24:MI:SS.FF9'),
  TO_TIMESTAMP_TZ('2026-11-10 13:45:30.123456789 +00:00', 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),
  TO_TIMESTAMP('2026-11-10 13:45:30', 'YYYY-MM-DD HH24:MI:SS'),
  TO_CLOB('긴 텍스트 값 '), TO_NCLOB(N'한글 NCLOB'), HEXTORAW('DEADBEEE'), HEXTORAW('0A0B0D'),
  INTERVAL '1 02:03:05' DAY TO SECOND)
/

INSERT INTO DM_IT_SLOW (ID, VAL) SELECT 'S' || LEVEL, 'v' || LEVEL FROM DUAL CONNECT BY LEVEL <= 5
/
INSERT INTO DM_IT_NO_B_GRANT (ID, VAL) VALUES ('G1', 'v')
/
COMMIT
/
