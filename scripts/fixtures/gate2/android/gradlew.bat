@echo off
echo %*>>"%~dp0gradle-invocations.log"
if not "%~1"=="-p" exit /b 0
if not "%~2"=="android" exit /b 8
if not "%~3"=="--offline" exit /b 8
if not "%~4"=="--no-daemon" exit /b 8
if not "%~5"=="-q" exit /b 8
if not "%~6"==":core:e2eTest" exit /b 8
if not "%~7"=="" exit /b 8
if exist "%~dp0fail-gate2" exit /b 9
exit /b 0
