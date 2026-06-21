@echo off
setlocal
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\21_withdrawal_uat_cleanup.sql"
exit /b %ERRORLEVEL%
