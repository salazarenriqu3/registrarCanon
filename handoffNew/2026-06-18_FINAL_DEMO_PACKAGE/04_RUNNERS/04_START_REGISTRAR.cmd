@echo off
setlocal
for %%I in ("%~dp0..\..\..") do set "REGISTRAR_ROOT=%%~fI"
set "APP_UPLOAD_DIR=%~dp0..\03_TEST_DATA\admission_uploads"
cd /d "%REGISTRAR_ROOT%"
call mvn spring-boot:run
