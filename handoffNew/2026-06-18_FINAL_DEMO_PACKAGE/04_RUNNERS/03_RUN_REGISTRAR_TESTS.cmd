@echo off
setlocal
for %%I in ("%~dp0..\..\..") do set "REGISTRAR_ROOT=%%~fI"
pushd "%REGISTRAR_ROOT%"
call mvn -q test "-Dtest=!ModulithTests"
set "RESULT=%ERRORLEVEL%"
popd
echo.
exit /b %RESULT%
