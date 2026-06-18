@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PACKAGE_DIR=%~dp0.."
for %%I in ("%PACKAGE_DIR%") do set "PACKAGE_DIR=%%~fI"
set "WORKSPACE_ROOT=%~dp0..\..\..\.."
for %%I in ("%WORKSPACE_ROOT%") do set "WORKSPACE_ROOT=%%~fI"

set "DB_HOST=%EAC_DB_HOST%"
if not defined DB_HOST set "DB_HOST=127.0.0.1"
set "DB_PORT=%EAC_DB_PORT%"
if not defined DB_PORT set "DB_PORT=3306"
set "DB_USER=%EAC_DB_USER%"
if not defined DB_USER set "DB_USER=root"
if defined EAC_DB_PASSWORD set "MYSQL_PWD=%EAC_DB_PASSWORD%"

call :find_mysql
if errorlevel 1 (
  echo FAILED: mysql.exe was not found. Install MySQL/MariaDB or add its bin folder to PATH.
  exit /b 1
)

if /I not "%~1"=="--yes" (
  echo.
  echo WARNING: This will DROP and recreate eacdb at %DB_HOST%:%DB_PORT%.
  set "CONFIRM="
  set /p "CONFIRM=Type RECREATE to continue: "
  if /I not "!CONFIRM!"=="RECREATE" (
    echo Cancelled. No database changes were made.
    exit /b 2
  )
)

echo.
echo === EAC FRESH DATABASE SETUP - 2026-06-18 ===
echo Workspace: %WORKSPACE_ROOT%
echo SQL bundle: %~dp0sql
echo Database:  %DB_USER%@%DB_HOST%:%DB_PORT%/eacdb
echo.

"%MYSQL_EXE%" -h "%DB_HOST%" -P "%DB_PORT%" -u "%DB_USER%" -e "DROP DATABASE IF EXISTS eacdb; CREATE DATABASE eacdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 exit /b 1

call :run "sql\01_SCHEMA\01_base_schema_and_seed.sql"
if errorlevel 1 exit /b 1
call :run "sql\02_CONTRACTS\02_enlistment_status_contract.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\03_full_curriculum.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\04_academic_term_calendar.sql"
if errorlevel 1 exit /b 1
call :run "sql\04_TERM_AND_FEES\05_demo_fee_templates.sql"
if errorlevel 1 exit /b 1
call :run "sql\02_CONTRACTS\06_exact_fee_schema.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\07_retire_empty_programs.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\08_block_sections.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\09_block_offerings.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\10_irregular_open_sections.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\11_faculty_and_grading.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\12_class_schedules.sql"
if errorlevel 1 exit /b 1
call :run "sql\04_TERM_AND_FEES\13_activate_demo_term.sql"
if errorlevel 1 exit /b 1
call :run "sql\04_TERM_AND_FEES\14_materialize_demo_term_fees.sql"
if errorlevel 1 exit /b 1
call :run "sql\04_TERM_AND_FEES\15_materialize_calendar_term_fees.sql"
if errorlevel 1 exit /b 1
call :run "sql\03_ACADEMIC_MASTER\16_assign_demo_faculty.sql"
if errorlevel 1 exit /b 1
call :run "sql\05_VERIFICATION\17_verify_readiness.sql"
if errorlevel 1 exit /b 1

echo.
echo SUCCESS: eacdb was rebuilt and verified.
echo Next: run ..\04_RUNNERS\02_BUILD_ALL.cmd
exit /b 0

:find_mysql
set "MYSQL_EXE="
where mysql >nul 2>&1 && set "MYSQL_EXE=mysql"
if defined MYSQL_EXE exit /b 0
for /d %%D in ("C:\Program Files\MariaDB*") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if defined MYSQL_EXE exit /b 0
for /d %%D in ("C:\Program Files\MySQL\MySQL Server*") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if defined MYSQL_EXE exit /b 0
exit /b 1

:run
echo --- %~1 ---
"%MYSQL_EXE%" -h "%DB_HOST%" -P "%DB_PORT%" -u "%DB_USER%" eacdb < "%~dp0%~1"
if errorlevel 1 (
  echo FAILED: %~1
  exit /b 1
)
exit /b 0
