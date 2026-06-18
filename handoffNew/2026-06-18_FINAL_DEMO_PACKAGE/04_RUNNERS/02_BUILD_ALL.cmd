@echo off
setlocal
for %%I in ("%~dp0..\..\..\..") do set "ROOT=%%~fI"

echo === BUILD REGISTRAR ===
pushd "%ROOT%\registrar"
call mvn -q -DskipTests package
if errorlevel 1 (popd & exit /b 1)
popd

echo === BUILD ENROLLMENT ===
pushd "%ROOT%\enrollment3"
if exist mvnw.cmd (
  call mvnw.cmd -q -DskipTests package
) else (
  call mvn -q -DskipTests package
)
if errorlevel 1 (popd & exit /b 1)
popd

echo BUILD PASS
exit /b 0
