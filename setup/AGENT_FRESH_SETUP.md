# Agent Playbook - Fresh Machine Setup

Last updated: 2026-06-25
Audience: agents, devops, and anyone provisioning a demo PC from scratch.

Canonical project root: folder containing `registrar/` and `enrollment3/`
Example: `C:\Users\sune\Downloads\new`

Human companion: `registrar/handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`

---

## Agent mission

1. Verify prerequisites.
2. Run one bootstrap that seeds the canonical academic baseline.
3. If the goal is the full registrar feature presentation, overlay the dedicated demo dataset.
4. Start Registrar and Enrollment.
5. Confirm readiness on active term `1120242025`.

Do not hand-edit scattered SQL unless bootstrap fails on a specific step. Fix the failing step, then re-run bootstrap or the targeted loader cleanly.

---

## Phase 0 - Prerequisite check

From project root:

```cmd
registrar\setup\CHECK_PREREQUISITES.cmd
```

Or PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File registrar\setup\CHECK_PREREQUISITES.ps1
```

Required checks that must pass:

| ID | What | If it fails |
|----|------|-------------|
| `PRJ-*` | Project folders and bootstrap scripts exist | Wrong working directory or incomplete copy |
| `JDK` | Java 17+ on PATH | Install JDK 17 or 21 and add `java` to PATH |
| `MAVEN` | `mvn` or `enrollment3\mvnw.cmd` exists | Install Maven 3.6+ |
| `MYSQL-CLIENT` | `mysql.exe` found | Install MariaDB/MySQL and add `bin` to PATH |
| `MARIADB-SERVICE` | Server reachable at `127.0.0.1:3306` as `root` | Start MariaDB and fix credentials |

Default DB credentials:

| Setting | Value |
|---------|-------|
| Host | `127.0.0.1:3306` |
| Database | `eacdb` |
| User | `root` |
| Password | empty |

If the password is not empty, update:

- `registrar/src/main/resources/application.properties`
- `enrollment3/src/main/resources/application.properties`

---

## Phase 1 - Full bootstrap

```cmd
registrar\setup\RUN_FRESH_SETUP.cmd
```

This is destructive. It drops and recreates `eacdb`.

Bootstrap summary:

| Area | Seeded? |
|------|---------|
| Schema and finance gates | Yes |
| Enlistment lifecycle columns | Yes |
| Full curriculum | Yes |
| Calendar terms through 2728 | Yes |
| Global fee templates | Yes |
| Program fee migration | Yes |
| Block sections and block offerings | Yes |
| `IRREG-A` sections | Yes |
| Faculty, grading windows, and demo faculty mix | Yes |
| Class schedules | Yes |
| Active term `1120242025` | Yes |
| Verification SQL | Yes |

Success criteria from the readiness SQL:

- current academic term is `1120242025`
- fee gaps on the active term are `0`
- room conflicts are `0`
- faculty conflicts are `0`
- section self-overlaps are `0`
- overloaded faculty are `0`
- block offerings exist on the active term
- active-term faculty assignments exist
- inactive-term faculty assignments are `0`
- `prof.cruz` has active-term sections
- registrar governance contract summary returns `PASS`
- `grade_change_requests` exposes `reviewed_by`, `review_note`, and `rejected_at`
- archive and released-number tables exist before UAT begins

If fee gaps are non-zero, use `registrar/setup/fees/term-fee-import-template-1120242025.csv` in the Program Fees UI.

---

## Phase 1.5 - Full registrar feature demo overlay

Run this when you need the named demo dataset used in the June 25 registrar feature presentation:

```cmd
registrar\setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

That overlay adds:

- concrete rooms for `BSIT-1-1-A`, `BSCPE-1-1-A`, and the `IRREG-A` sections used in the live demo
- `2026-1001` with admission-linked applicant files, printable current load, and history baselines
- disposable students for add-class, transfer credit, program shift, overpayment, and withdrawal
- scholarship comparison students through the bundled scholarship seed

When starting only Registrar for the demo, prefer:

```cmd
registrar\setup\START_REGISTRAR_DEMO.cmd
```

That start script wires `APP_UPLOAD_DIR` to the seeded admission-upload assets automatically.

---

## Phase 2 - Start applications

Terminal 1, Registrar:

```cmd
cd registrar
mvn -q spring-boot:run
```

URL: http://localhost:8083/registrar/login

Terminal 2, Enrollment:

```cmd
cd enrollment3
mvn -q spring-boot:run
```

URL: http://localhost:8082/login

Demo logins:

| Login | Password |
|-------|----------|
| `admin` | `1234` |
| `prof.cruz` | `1234` |

---

## Phase 3 - UI smoke

| Step | URL | Pass when |
|------|-----|-----------|
| 1 | http://localhost:8083/registrar/admin/settings | Active term is `1120242025` and readiness is green |
| 2 | http://localhost:8083/registrar/admin/term-fees?termId=1 | No fee blockers |
| 3 | http://localhost:8083/registrar/admin/class-scheduling?termId=1 | Blocks and `IRREG-A` sections are visible |
| 4 | http://localhost:8083/registrar/admin/student-manager?username=2026-1001 | Admission snapshot and applicant documents render after demo overlay |

For the detailed presentation sequence, use `registrar/handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`.

---

## Troubleshooting

| Symptom | Action |
|---------|--------|
| `mysql` not found | Install MariaDB/MySQL and add its `bin` directory to PATH |
| MariaDB not reachable on 3306 | Start the MariaDB service |
| Bootstrap readiness fails | Re-run the exact failing SQL step, then rerun verification |
| Rooms still show TBA | Expected after bootstrap; run the full registrar demo overlay |
| Admission documents do not open | Start Registrar with `setup\START_REGISTRAR_DEMO.cmd` or set `APP_UPLOAD_DIR` manually |
| Withdrawal demo student missing | Re-run `setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd` or the focused withdrawal runner |
| Faculty grading looks empty | Verify `prof.cruz` assignments and rerun the active-term demo data loader if needed |

---

## Command cheat sheet

```cmd
REM Prerequisites
registrar\setup\CHECK_PREREQUISITES.cmd

REM Canonical baseline bootstrap
registrar\setup\RUN_FRESH_SETUP.cmd

REM Registrar feature presentation dataset
registrar\setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd

REM Start Registrar with admission uploads wired in
registrar\setup\START_REGISTRAR_DEMO.cmd
```
