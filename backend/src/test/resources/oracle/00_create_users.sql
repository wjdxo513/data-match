-- DataMatch 로컬 Oracle 테스트 사용자 생성 (SETUP-05, 실행 계획 D-02)
--
-- 관리자 계정으로 PDB(FREEPDB1)에 접속해 SQL*Plus로 실행한다(ACCEPT·HIDE·& 치환은 SQL*Plus 전용이다).
--   sqlplus system@//localhost:1521/FREEPDB1 @00_create_users.sql
--
-- 다시 실행해도 안전하다.
--   - 이미 있는 사용자는 그대로 둔다(비밀번호·데이터를 바꾸지 않는다). 없는 사용자만 만든다.
--   - 필요한 권한은 매번 다시 부여한다(GRANT는 반복해도 같은 결과).
--   - 오류가 나면 즉시 중단하고, 마지막에 DM_IT_ 사용자 상태를 출력한다.
-- 이 DB는 pgweb-boot 로컬 테스트와 함께 쓰므로 DM_IT_ 로 시작하는 사용자만 다룬다.
-- 비밀번호는 실행할 때 입력받으며(화면에 표시하지 않음) 이 파일, 저장소, 출력에 남기지 않는다.
-- 비밀번호에는 큰따옴표(")를 쓸 수 없다. 이미 있는 사용자의 입력값은 사용하지 않는다.

SET VERIFY OFF
SET FEEDBACK OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

PROMPT DataMatch test users (DM_IT_*). Existing users are kept unchanged.
ACCEPT owner_pw CHAR PROMPT 'Password for DM_IT_OWNER (ignored if it already exists): ' HIDE
ACCEPT ro_a_pw CHAR PROMPT 'Password for DM_IT_RO_A (ignored if it already exists): ' HIDE
ACCEPT ro_b_pw CHAR PROMPT 'Password for DM_IT_RO_B (ignored if it already exists): ' HIDE

DECLARE
  v_users_ts NUMBER;

  PROCEDURE create_if_missing(p_user VARCHAR2, p_password VARCHAR2, p_owner BOOLEAN) IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = p_user;
    IF v_count > 0 THEN
      DBMS_OUTPUT.PUT_LINE(p_user || ': already exists (kept)');
      RETURN;
    END IF;
    IF p_password IS NULL THEN
      RAISE_APPLICATION_ERROR(-20001, p_user || ': password is empty');
    END IF;
    IF INSTR(p_password, '"') > 0 THEN
      RAISE_APPLICATION_ERROR(-20002, p_user || ': password must not contain double quotes');
    END IF;
    EXECUTE IMMEDIATE 'CREATE USER ' || p_user || ' IDENTIFIED BY "' || p_password || '"'
      || CASE WHEN p_owner THEN ' DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS' END;
    DBMS_OUTPUT.PUT_LINE(p_user || ': created');
  END;
BEGIN
  SELECT COUNT(*) INTO v_users_ts FROM dba_tablespaces WHERE tablespace_name = 'USERS';
  IF v_users_ts = 0 THEN
    RAISE_APPLICATION_ERROR(-20003, 'Tablespace USERS not found. Run this script in PDB FREEPDB1 (not in the CDB root).');
  END IF;
  create_if_missing('DM_IT_OWNER', q'[&owner_pw]', TRUE);
  create_if_missing('DM_IT_RO_A', q'[&ro_a_pw]', FALSE);
  create_if_missing('DM_IT_RO_B', q'[&ro_b_pw]', FALSE);
END;
/

UNDEFINE owner_pw
UNDEFINE ro_a_pw
UNDEFINE ro_b_pw

-- 테이블 소유자 권한
GRANT CREATE SESSION, CREATE TABLE, CREATE PROCEDURE TO DM_IT_OWNER;
ALTER USER DM_IT_OWNER QUOTA UNLIMITED ON USERS;

-- 읽기 전용 접속 사용자 권한(환경 간 비교를 모사하는 A 환경, B 환경)
-- 테이블 SELECT 권한은 DM_IT_OWNER 가 스키마 스크립트(10_schema.sql)에서 부여한다.
GRANT CREATE SESSION TO DM_IT_RO_A;
GRANT CREATE SESSION TO DM_IT_RO_B;

PROMPT
PROMPT DM_IT_ user status:
SET FEEDBACK ON
COLUMN username FORMAT A15
COLUMN account_status FORMAT A20
SELECT username, account_status FROM dba_users WHERE username LIKE 'DM\_IT\_%' ESCAPE '\' ORDER BY username;
EXIT
