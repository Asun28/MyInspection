@echo off
echo %*>>"%~dp0gradle-invocations.log"
echo %* | findstr /L /C:"--tests" >nul
if errorlevel 1 exit /b 0
if not "%~1"=="-p" exit /b 8
if not "%~2"=="android" exit /b 8
if not "%~3"=="--offline" exit /b 8
if not "%~4"=="--no-daemon" exit /b 8
if not "%~5"=="-q" exit /b 8
if not "%~6"==":core:test" exit /b 8
if not "%~7"=="--tests" exit /b 8
if not "%~8"=="nz.myinspection.core.e2e.*" exit /b 8
if not "%~9"=="" exit /b 8
if exist "%~dp0fail-gate2" exit /b 9
exit /b 0
