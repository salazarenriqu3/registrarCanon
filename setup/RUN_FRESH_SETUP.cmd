@echo off
setlocal EnableExtensions

REM Standalone Registrar bootstrap using the self-contained SQL bundle.
set "FRESH_DB_RUNNER=%~dp0..\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd"

if not exist "%FRESH_DB_RUNNER%" (
  echo FAILED: bundled fresh-database runner was not found.
  echo Expected: %FRESH_DB_RUNNER%
  exit /b 1
)

if /I not "%~1"=="--skip-prereq" (
  call "%~dp0CHECK_PREREQUISITES.cmd"
  if errorlevel 1 exit /b 1
)

if /I "%~1"=="--skip-prereq" shift
call "%FRESH_DB_RUNNER%" %*
exit /b %ERRORLEVEL%
