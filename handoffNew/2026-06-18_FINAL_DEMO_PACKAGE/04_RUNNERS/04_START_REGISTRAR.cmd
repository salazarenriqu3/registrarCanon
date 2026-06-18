@echo off
setlocal
for %%I in ("%~dp0..\..\..") do set "REGISTRAR_ROOT=%%~fI"
cd /d "%REGISTRAR_ROOT%"
call mvn spring-boot:run
