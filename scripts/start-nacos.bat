@echo off
REM Start local Nacos 2.2.3 (standalone + MySQL nacos_config)
REM Requires: JAVA_HOME or uses default JDK path below

setlocal
if "%JAVA_HOME%"=="" set "JAVA_HOME=D:\Coding\JavaWebTools\JDK21"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "NACOS_HOME=D:\tools\nacos"

if not exist "%JAVA%" (
  echo JAVA not found: %JAVA%
  echo Set JAVA_HOME to JDK 17+ and retry.
  exit /b 1
)
if not exist "%NACOS_HOME%\target\nacos-server.jar" (
  echo Nacos not found at %NACOS_HOME%
  exit /b 1
)
if not exist "%NACOS_HOME%\logs" mkdir "%NACOS_HOME%\logs"

echo Starting Nacos standalone at http://localhost:8848/nacos ...
"%JAVA%" -Xms512m -Xmx512m -Xmn256m ^
  -Dnacos.standalone=true ^
  -Dloader.path="%NACOS_HOME%\plugins,%NACOS_HOME%\plugins\health,%NACOS_HOME%\plugins\cmdb,%NACOS_HOME%\plugins\selector" ^
  -Dnacos.home="%NACOS_HOME%" ^
  -jar "%NACOS_HOME%\target\nacos-server.jar" ^
  --spring.config.additional-location="file:%NACOS_HOME%\conf/" ^
  --logging.config="%NACOS_HOME%\conf/nacos-logback.xml" ^
  --server.max-http-header-size=524288
