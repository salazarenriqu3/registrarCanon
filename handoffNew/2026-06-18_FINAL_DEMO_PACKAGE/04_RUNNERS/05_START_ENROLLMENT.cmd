@echo off
setlocal
for %%I in ("%~dp0..\..\..\..") do set "ROOT=%%~fI"
cd /d "%ROOT%\enrollment3"
if exist mvnw.cmd (call mvnw.cmd spring-boot:run) else (call mvn spring-boot:run)
