@echo off
setlocal
for %%I in ("%~dp0..\..\..\..") do set "ROOT=%%~fI"
pushd "%ROOT%\registrar"
call mvn -q test
set "RESULT=%ERRORLEVEL%"
popd
echo.
if not "%RESULT%"=="0" echo NOTE: The known Modulith package-cycle architecture test currently makes the full suite non-zero.
exit /b %RESULT%
