DataMatch 설치·실행 안내
=========================

1. 준비
   - JDK 11이 필요합니다. JAVA_HOME 환경 변수를 JDK 11 경로로 설정하거나 PATH에 java를 추가해 주세요.
   - 사내망에서 개발·테스트·운영 DB와 SafeDB 서버에 접근할 수 있어야 합니다.

2. 접속 정보 설정
   - config\datamatch.properties.example 을 같은 폴더에 datamatch.properties 로 복사합니다.
   - 환경별(개발·테스트·운영) JDBC URL, 읽기 전용 계정, 비밀번호를 입력합니다.
   - 사용하지 않는 환경은 해당 항목을 지우면 화면의 환경 목록에서 빠집니다.
   - datamatch.properties 는 비밀번호를 담고 있으므로 본인만 읽을 수 있게 보관하고 다른 사람과 공유하지 마세요.

3. SafeDB 설정
   - SafeDB 담당자에게 SafeDBAccess.properties, SafeDBMap_config.xml, inilog4j.properties 를 받아 config 폴더에 둡니다.
   - 이 파일들은 배포 zip에 포함되어 있지 않습니다.

4. 실행
   - start.bat 을 실행합니다.
   - 브라우저에서 http://localhost:18080 에 접속합니다(포트를 바꿨다면 그 포트).
   - 종료하려면 실행 창에서 Ctrl+C 를 누릅니다.

5. 로그
   - logs 폴더에 실행 로그가 남습니다. 로그에는 거래번호, 컬럼 값, 복호화된 값, 비밀번호를 기록하지 않습니다.
