@echo off
setlocal
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\05_registrar_feature_demo_seed.sql" || exit /b %ERRORLEVEL%
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\02_scholarship_demo_seed.sql" || exit /b %ERRORLEVEL%
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\20_withdrawal_uat_seed.sql" || exit /b %ERRORLEVEL%
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\06_registrar_feature_demo_verify.sql"
exit /b %ERRORLEVEL%
