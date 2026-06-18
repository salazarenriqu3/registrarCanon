@echo off
setlocal EnableExtensions EnableDelayedExpansion
echo This removes only SCH-UAT scholarship demo records.
set "CONFIRM="
set /p "CONFIRM=Type CLEAN to continue: "
if /I not "!CONFIRM!"=="CLEAN" exit /b 2
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\03_scholarship_demo_cleanup.sql"
exit /b %ERRORLEVEL%
