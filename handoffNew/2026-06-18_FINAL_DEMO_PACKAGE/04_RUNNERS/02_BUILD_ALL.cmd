@echo off
setlocal
for %%I in ("%~dp0..\..\..") do set "REGISTRAR_ROOT=%%~fI"

echo === BUILD REGISTRAR ===
pushd "%REGISTRAR_ROOT%"
call mvn -q -DskipTests package
if errorlevel 1 (popd & exit /b 1)
popd

echo BUILD PASS
exit /b 0
