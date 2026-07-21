@echo off
setlocal
cd /d "%~dp0\..\services\lightrag"

if not exist ".env" (
  if exist "env.example" (
    copy /Y env.example .env >nul
    echo [start-lightrag] 已从 env.example 生成 .env，请先填写 API Key 再启动。
    echo 文件: %cd%\.env
    exit /b 1
  )
)

where lightrag-server >nul 2>&1
if errorlevel 1 (
  echo [start-lightrag] 未找到 lightrag-server
  echo 请先安装: pip install "lightrag-hku[api]"
  echo 或: uv tool install "lightrag-hku[api]"
  exit /b 1
)

echo [start-lightrag] WORKING_DIR=%cd%
echo [start-lightrag] 启动后健康检查: http://localhost:9621/health
lightrag-server
endlocal
