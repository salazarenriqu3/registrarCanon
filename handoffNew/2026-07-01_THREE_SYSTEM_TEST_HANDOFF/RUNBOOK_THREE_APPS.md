# Three-App Runbook

## Prerequisites

Run from PowerShell or Command Prompt on the local machine.

Required services/tools:

- Java 17 or newer. Current machine has Java 21.
- Maven.
- MariaDB/MySQL on `127.0.0.1:3306`.
- Database name: `eacdb`.

## Database Baseline

From `E:\registrarCanon_canon`:

```cmd
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

`RUN_FRESH_SETUP.cmd` drops and recreates `eacdb`. Only run it when a fresh database reset is intended.

After the baseline, run the compatibility SQL listed in `SQL_FEED_AND_VERIFY.md`.

## Port Check

```cmd
netstat -ano | findstr :8081
netstat -ano | findstr :8082
netstat -ano | findstr :8083
```

If a stale process owns a port:

```cmd
taskkill /PID <PID> /F
```

## Start Registrar

From `E:\registrarCanon_canon`:

```cmd
mvn spring-boot:run
```

URL:

```text
http://localhost:8083/registrar
```

Smoke pages:

```text
http://localhost:8083/registrar/admin/settings
http://localhost:8083/registrar/admin/curriculum
http://localhost:8083/registrar/admin/student-manager
http://localhost:8083/registrar/admin/class-scheduling?termId=1
```

## Start Enrollment

From `E:\EnrollLatest\enrollment3`:

```cmd
mvn spring-boot:run
```

URL:

```text
http://localhost:8082
```

Smoke pages:

```text
http://localhost:8082/login
http://localhost:8082/admin/dashboard
http://localhost:8082/admin/cashier
http://localhost:8082/admin/ledger
```

## Start Admission

From `E:\AdmitLatest\admission`:

```cmd
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

URL:

```text
http://localhost:8081/
```

Smoke pages:

```text
http://localhost:8081/
http://localhost:8081/admin/login
```

## Known Startup Notes

- Admission now pins `org.hibernate.dialect.MySQLDialect` to avoid Hibernate dialect detection failure against the local MariaDB metadata path.
- Admission may log warnings for duplicate index creation on `applicants`; those warnings are non-blocking if Tomcat reaches `Started EnrollmentApplication`.
- Enrollment `server.port` has a space in `server.port= 8082`, but Spring still resolves it as `8082`.
- Registrar startup repair creates several compatibility columns and indexes. Keep its logs available when verifying schema drift.
- Registrar startup repair also auto-runs the legacy lecture/lab migration helper when old mixed rows are still present.

## Lecture / Lab Check

If you want to confirm the component split during the demo rehearsal, run:

```cmd
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\04_verify_lec_lab_family_migration.sql
```
