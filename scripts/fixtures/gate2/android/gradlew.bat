@echo off
echo %*>>"%~dp0gradle-invocations.log"
echo %* | findstr /C:":core:test" >nul
if errorlevel 1 exit /b 0
echo %* | findstr /L /C:"nz.myinspection.core.e2e.*" >nul
if errorlevel 1 exit /b 8
if exist "%~dp0fail-gate2" exit /b 9
exit /b 0
