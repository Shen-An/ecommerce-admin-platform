@echo off
setlocal
echo Stopping Java services on 9999/9000/8800/8805 ...
for %%P in (9999 9000 8800 8805 8802 8803) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P" ^| findstr LISTENING') do (
    echo kill pid %%A port %%P
    taskkill /F /PID %%A >nul 2>&1
  )
)
echo Done.
endlocal
