@echo off
call "%~dp0_RUN_SQL.cmd" "%~dp0..\03_TEST_DATA\02_scholarship_demo_seed.sql"
exit /b %ERRORLEVEL%
