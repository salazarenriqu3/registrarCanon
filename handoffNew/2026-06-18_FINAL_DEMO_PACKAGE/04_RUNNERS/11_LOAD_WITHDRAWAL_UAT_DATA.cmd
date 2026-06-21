@echo off
setlocal
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\20_withdrawal_uat_seed.sql"
exit /b %ERRORLEVEL%
