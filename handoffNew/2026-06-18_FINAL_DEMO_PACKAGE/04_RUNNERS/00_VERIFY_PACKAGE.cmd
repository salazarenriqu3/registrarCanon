@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFY_PACKAGE.ps1"
exit /b %ERRORLEVEL%
