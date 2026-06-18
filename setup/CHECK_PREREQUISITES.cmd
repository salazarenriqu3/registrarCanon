@echo off
setlocal EnableExtensions
REM Standalone Registrar prerequisite checker.
for %%I in ("%~dp0\..") do set "REGISTRAR_ROOT=%%~fI"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CHECK_PREREQUISITES.ps1" -ProjectRoot "%REGISTRAR_ROOT%"
exit /b %ERRORLEVEL%
