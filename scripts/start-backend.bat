@echo off
setlocal
cd /d "%~dp0\..\backend"

echo [start-backend] package + launch gateway/auth/system/ai
call mvn -pl youlai-gateway,youlai-auth,youlai-system/system-boot,mall-ai/ai-boot,dev-launcher -am package -DskipTests
if errorlevel 1 (
  echo Maven package failed
  exit /b 1
)

call mvn -pl dev-launcher exec:java -Dexec.classpathScope=runtime
endlocal
