@echo off
setlocal
set "APP_UPLOAD_DIR=%~dp0..\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\admission_uploads"
cd /d "%~dp0.."
call mvn spring-boot:run
exit /b %ERRORLEVEL%
