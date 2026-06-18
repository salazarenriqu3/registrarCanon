@echo off
setlocal
if "%~1"=="" (
  echo Usage: _RUN_SQL.cmd path-to-sql
  exit /b 2
)

set "DB_HOST=%EAC_DB_HOST%"
if not defined DB_HOST set "DB_HOST=127.0.0.1"
set "DB_PORT=%EAC_DB_PORT%"
if not defined DB_PORT set "DB_PORT=3306"
set "DB_USER=%EAC_DB_USER%"
if not defined DB_USER set "DB_USER=root"
if defined EAC_DB_PASSWORD set "MYSQL_PWD=%EAC_DB_PASSWORD%"

set "MYSQL_EXE="
where mysql >nul 2>&1 && set "MYSQL_EXE=mysql"
if not defined MYSQL_EXE for /d %%D in ("C:\Program Files\MariaDB*") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if not defined MYSQL_EXE for /d %%D in ("C:\Program Files\MySQL\MySQL Server*") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if not defined MYSQL_EXE (
  echo FAILED: mysql.exe was not found.
  exit /b 1
)

echo Running %~f1
"%MYSQL_EXE%" -h "%DB_HOST%" -P "%DB_PORT%" -u "%DB_USER%" eacdb < "%~f1"
exit /b %ERRORLEVEL%
