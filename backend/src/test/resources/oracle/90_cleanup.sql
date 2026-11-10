-- DataMatch 로컬 Oracle 테스트 사용자 정리 (SETUP-05)
--
-- 관리자 계정으로 PDB(FREEPDB1)에 접속해 실행한다. DM_IT_ 사용자와 그 객체만 삭제한다.
--   sqlplus system@//localhost:1521/FREEPDB1 @90_cleanup.sql
-- 다시 만들 때는 00_create_users.sql 을 실행한 뒤 스키마·데이터를 적재한다.

DROP USER DM_IT_RO_A CASCADE;
DROP USER DM_IT_RO_B CASCADE;
DROP USER DM_IT_OWNER CASCADE;
PROMPT DataMatch 테스트 사용자(DM_IT_)를 삭제했습니다.
EXIT
