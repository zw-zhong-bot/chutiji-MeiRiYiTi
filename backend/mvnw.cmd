@REM ---------------------------------------------------------------------------
@REM chutiji Maven Wrapper (Windows)
@REM
@REM 本机环境事实：系统 JAVA_HOME 指向 JDK 8（苍穹在用），不能改。
@REM 因此本脚本在进程内把 JAVA_HOME 指向 JDK 21，不改任何系统/用户环境变量。
@REM 用户级 ~/.m2 在本机不可写，本地仓库改放工程内 .m2repo。
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set "MVNW_DIR=%~dp0"
if "%MVNW_DIR:~-1%"=="\" set "MVNW_DIR=%MVNW_DIR:~0,-1%"

@REM --- 锁定 JDK 21（仅本进程） ---
if not defined CHUTIJI_JDK (
  set "CHUTIJI_JDK=C:\Program Files\Java\jdk-21"
)
if not exist "%CHUTIJI_JDK%\bin\java.exe" (
  echo [mvnw] JDK 21 not found at "%CHUTIJI_JDK%". Set CHUTIJI_JDK to override. 1>&2
  exit /b 1
)
set "JAVA_HOME=%CHUTIJI_JDK%"

@REM --- 本地仓库与 wrapper 缓存都放工程内 ---
set "MAVEN_USER_HOME=%MVNW_DIR%\.mvn\maven-home"
set "MVNW_REPO=%MVNW_DIR%\.m2repo"

@REM --- Maven 发行版：用工程内已解压的那份（离线可用） ---
set "MAVEN_HOME=%MVNW_DIR%\.mvn\maven-home\wrapper\dists\apache-maven-3.9.11\03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo [mvnw] Maven distribution missing at "%MAVEN_HOME%". 1>&2
  exit /b 1
)

call "%MAVEN_HOME%\bin\mvn.cmd" -Dmaven.repo.local="%MVNW_REPO%" -s "%MVNW_DIR%\.mvn\settings.xml" %*
exit /b %ERRORLEVEL%
