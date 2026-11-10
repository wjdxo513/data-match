@echo off
rem DataMatch 실행 스크립트 (기술 설계서 TD-03, 3.2절)
setlocal
chcp 65001 > nul

set "APP_HOME=%~dp0"
if "%JAVA_OPTS%"=="" set "JAVA_OPTS=-Xmx512m"

if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

"%JAVA_EXE%" -version > nul 2>&1
if errorlevel 1 (
    echo [DataMatch] Java를 찾을 수 없습니다. JDK 11을 설치하고 JAVA_HOME을 설정해 주세요.
    exit /b 1
)

if not exist "%APP_HOME%config\datamatch.properties" (
    echo [DataMatch] config\datamatch.properties 파일이 없습니다.
    echo [DataMatch] config\datamatch.properties.example 을 복사해 접속 정보를 입력한 뒤 다시 실행해 주세요.
    exit /b 2
)

if not exist "%APP_HOME%logs" mkdir "%APP_HOME%logs"

rem SafeDB는 SafeDBAccess.properties 를 classpath에서, SafeDBMap_config.xml 등은 작업 폴더 기준으로 찾으므로
rem 작업 폴더와 classpath(.)를 모두 config 로 둔다(기술 설계서 TA-01).
cd /d "%APP_HOME%config"

echo [DataMatch] 시작합니다. 종료하려면 이 창에서 Ctrl+C 를 누르세요.
"%JAVA_EXE%" %JAVA_OPTS% -Dfile.encoding=UTF-8 "-Dlogging.file.path=%APP_HOME%logs" -cp "%APP_HOME%datamatch.jar;%APP_HOME%lib\*;." kr.kicc.datamatch.DataMatchApplication --spring.config.additional-location=file:./datamatch.properties
exit /b %errorlevel%
