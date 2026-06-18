@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CHECK_PREREQUISITES.ps1"
exit /b %ERRORLEVEL%
