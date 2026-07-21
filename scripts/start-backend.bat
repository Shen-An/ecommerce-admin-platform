@echo off
setlocal
cd /d "%~dp0\..\backend"

echo [start-backend] package + launch full stack
echo   gateway auth system ai pms ums oms sms
echo.

REM Package all services the launcher starts by default
call mvn -pl youlai-gateway,youlai-auth,youlai-system/system-boot,mall-ai/ai-boot,mall-pms/pms-boot,mall-ums/ums-boot,mall-oms/oms-boot,mall-sms/sms-boot,dev-launcher -am package -DskipTests
if errorlevel 1 (
  echo Maven package failed
  exit /b 1
)

REM PlatformLauncher defaults: start all core services (no extra flags needed)
call mvn -pl dev-launcher exec:java -Dexec.classpathScope=runtime
endlocal
