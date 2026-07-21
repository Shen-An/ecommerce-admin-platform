@echo off
setlocal
echo Stopping Java services on core ports ...
for %%P in (9999 9000 8800 8805 8802 8801 8803 8804) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P" ^| findstr LISTENING') do (
    echo kill pid %%A port %%P
    taskkill /F /PID %%A >nul 2>&1
  )
)
echo Done.
echo Ports: gateway 9999 / auth 9000 / system 8800 / ai 8805
echo        pms 8802 / ums 8801 / oms 8803 / sms 8804
endlocal
