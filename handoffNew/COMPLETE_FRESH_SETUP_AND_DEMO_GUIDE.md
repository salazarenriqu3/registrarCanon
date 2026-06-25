# Complete Fresh Setup & Demo Guide

Last updated: 2026-06-17
**One document** for new PC setup, agent handoff, directory map, commands, and demo instructions.

**Status / roadmap:** **`PROJECT_STATUS_AND_ROADMAP.md`** — read for done vs pending.  
**Use this file for setup and demo.** Deeper detail lives in linked docs at the bottom.

---

## Project root

Copy the **entire** folder containing `registrar/` and `enrollment3/`.

| Path | Status |
|------|--------|
| **`C:\newer\new`** | **Primary** — has `registrar\setup\` (recommended) |
| `C:\newer` | Workspace root for the current machine |

All commands below assume you are at **project root** (the folder that contains `registrar`).

---

## What you are deploying

| Component | Folder | URL (dev) |
|-----------|--------|-----------|
| **Registrar** | `registrar/` | http://localhost:8083/registrar |
| **Enrollment / Cashier** | `enrollment3/` | http://localhost:8082 |
| **Database** | MariaDB `eacdb` | `127.0.0.1:3306` |

Both apps share one database.

| Setting | Value |
|---------|--------|
| Active term | **`1120242025`** — A.Y. 2024–25, **1st Semester** |
| `term_id` | `1` |
| Y1 golden-path block | **`BSCPE-1-1-A`** |

---

## Directory map (setup + demo)

```text
<project-root>\
│
├── registrar\
│   ├── setup\                          ★ SETUP — run prereqs + bootstrap here
│   │   ├── AGENT_FRESH_SETUP.md        ★ Agent playbook (phased steps)
│   │   ├── BOOTSTRAP_SEED_MANIFEST.md    Full seed inventory (17 steps)
│   │   ├── CHECK_PREREQUISITES.cmd       Machine gate (JDK, MariaDB, Maven)
│   │   ├── RUN_FRESH_SETUP.cmd           ★ One-command DB bootstrap
│   │   ├── README.md
│   │   ├── sql\                          Term, fees, prof.cruz, verify
│   │   └── fees\                         CSV fee templates (UI import fallback)
│   │
│   ├── handoffNew\                     ★ DEMO / UAT DOCS (this file lives here)
│   │   ├── COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md   ← you are here
│   │   ├── HUMAN_UAT_CHECKLIST.md        ★ Demo sign-off (Sessions 0–F)
│   │   ├── THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md
│   │   ├── MASTER_DEMO_UAT_MANUAL.md     Full reference
│   │   └── START_HERE_NEW_PC_HANDOFF.md
│   │
│   ├── db\                             ★ Heavy seeds (auto-run by bootstrap)
│   │   ├── fix                           Schema rebuild (step 1)
│   │   ├── 04_seed_full_curriculum.sql
│   │   ├── seed_all_program_block_sections_calendar.sql
│   │   ├── seed_block_offerings.sql
│   │   ├── seed_irregular_open_sections.sql
│   │   ├── seed_faculty_professors_and_grading.sql
│   │   ├── seed_all_class_schedules.sql
│   │   ├── run_full_uat_bootstrap.cmd    → forwards to setup\ (if present)
│   │   └── 00_full_uat_bootstrap.sql
│   │
│   └── src\main\resources\
│       └── application.properties        DB URL, port 8083
│
├── enrollment3\
│   └── src\main\resources\
│       ├── application.properties        DB URL, port 8082
│       └── sql\01_enlistment_status_schema.sql
│
└── _runtime_logs\                      Optional — Python preflight only
```

### Skip unless debugging

| Path | Why |
|------|-----|
| `handoffNew\sql_manual\` | One-off patches; bootstrap replaces most |
| `handoffNew\fee_import\` | Legacy CSVs → use `registrar\setup\fees\` |
| `handoffNew\MASTER_HANDOFF.md`, `NEXT_AGENT_*` | Historical agent notes |
| `_runtime_logs\` | Optional automation |

---

## Software to install (new PC)

| Software | Version | Notes |
|----------|---------|-------|
| **JDK** | 17 or 21 | Add `java` to PATH |
| **MariaDB** or **MySQL** | 10.4+ / 8.0+ | Port **3306**, user **`root`**, password **empty** (default) |
| **Maven** | 3.6+ | Or use `enrollment3\mvnw.cmd` |
| **MySQL Workbench** | any | Recommended for running SQL |

Verify:

```cmd
java -version
mysql --version
mvn -version
```

**Optional:** Python 3.10+ — automated preflight only; **not required** for setup or manual demo.

### Default DB credentials (both apps)

| Setting | Value |
|---------|--------|
| Host | `127.0.0.1:3306` |
| Database | `eacdb` (created by bootstrap) |
| User | `root` |
| Password | *(empty)* |

If root password is not empty, edit:

- `registrar/src/main/resources/application.properties`
- `enrollment3/src/main/resources/application.properties`

---

## Paste this to a new Cursor agent (fresh terminal)

```text
Fresh machine setup + demo prep for EAC Registrar + Enrollment.

Project root: C:\newer\new
(Open the folder that contains registrar/ and enrollment3/)

Read and follow:
  registrar/setup/AGENT_FRESH_SETUP.md
  registrar/handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md

Mission:
1. cd to project root
2. Run: registrar\setup\CHECK_PREREQUISITES.cmd  (fix any FAIL)
3. Run: registrar\setup\RUN_FRESH_SETUP.cmd     (drops/rebuilds eacdb, ~10-20 min)
4. Start both apps in separate terminals:
   - cd registrar && mvn -q spring-boot:run   → http://localhost:8083/registrar
   - cd enrollment3 && mvn -q spring-boot:run → http://localhost:8082
5. Smoke test: login admin/1234, confirm Settings readiness is Ready for 1120242025 (term_id=1)

Demo docs after setup:
  registrar/handoffNew/HUMAN_UAT_CHECKLIST.md
  registrar/handoffNew/THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md

DB: eacdb @ 127.0.0.1:3306, user root, empty password
Logins: admin/1234, prof.cruz/1234 (grading)

Do not hand-edit scattered SQL unless bootstrap fails on a specific step.
Report pass/fail for each phase with URLs checked.
```

**If `registrar\setup\` is missing** (zip copy only), tell the agent to run `registrar\db\run_full_uat_bootstrap.cmd` instead, then manually confirm active term in Settings.

---

## Fresh setup — run in this order

### Step 0 — Prerequisites

```cmd
registrar\setup\CHECK_PREREQUISITES.cmd
```

**Pass when:** JDK 17+, Maven, `mysql` client, MariaDB reachable on 3306, project files found.

| If FAIL | Fix |
|---------|-----|
| `mysql` not found | Install MariaDB; add `bin` to PATH |
| Connection refused 3306 | Start MariaDB service (`services.msc`) |
| Access denied | Set password in `application.properties` |

---

### Step 1 — Bootstrap database (destructive)

**Drops and recreates `eacdb`.** Runtime ~10–20 minutes (curriculum seed is longest).

```cmd
registrar\setup\RUN_FRESH_SETUP.cmd
```

Skip prereq check only if already verified:

```cmd
registrar\setup\RUN_FRESH_SETUP.cmd --skip-prereq
```

**Legacy fallback** (no `setup\` folder):

```cmd
registrar\db\run_full_uat_bootstrap.cmd
```

Or mysql CLI from project root:

```cmd
mysql -u root < registrar/db/00_full_uat_bootstrap.sql
```

#### Bootstrap success criteria (last SQL report)

| Check | Expected |
|-------|----------|
| `CURRENT_ACADEMIC_TERM` | `1120242025` |
| Fee gaps (active term) | `unresolved = 0` |
| Unscheduled sections | `0` (or very low) |
| Block offerings (active term) | **> 0** |
| prof.cruz sections (active term) | **> 0** |

If fee gaps remain: import `registrar/setup/fees/term-fee-import-template-1120242025.csv` via Program Fees UI.

#### What bootstrap seeds (summary)

| Area | Seeded? |
|------|---------|
| Full schema + finance gates + installment plan | Yes |
| Curriculum (21 active programs) | Yes |
| Calendar terms 2425–2728 (S1 + S2) | Yes |
| Block sections + block offerings + IRREG-A (all calendar terms) | Yes |
| Faculty + grading windows FORCE_OPEN | Yes |
| Class schedules (not TBA) | Yes |
| Fees on all calendar terms | Yes |
| Active term **1120242025** | Yes |
| Curated demo faculty assignments on active-term sections | Yes |

Full step list: `registrar/setup/BOOTSTRAP_SEED_MANIFEST.md`

---

### Step 2 — Start both applications

**Terminal 1 — Registrar:**

```cmd
cd registrar
mvn -q spring-boot:run
```

Wait for `Started RegistrarApplication`.  
Open: http://localhost:8083/registrar/login

**Terminal 2 — Enrollment:**

```cmd
cd enrollment3
mvn -q spring-boot:run
```

Open: http://localhost:8082/login

First run may take several minutes while Maven downloads dependencies.

---

### Step 3 — Smoke test (~5 min)

Login **`admin` / `1234`** on both apps.

| # | URL | Pass when |
|---|-----|-----------|
| 1 | http://localhost:8083/registrar/admin/settings | Active term **`1120242025`**, readiness **Ready** |
| 2 | http://localhost:8083/registrar/admin/term-fees?termId=1 | No fee blockers |
| 3 | http://localhost:8083/registrar/admin/finance-policy | Admission, downpayment, installments visible |
| 4 | http://localhost:8083/registrar/admin/classes | BSCPE sections have times (not all TBA) |
| 5 | http://localhost:8083/registrar/admin/class-scheduling?termId=1 | Blocks + IRREG-A visible |
| 6 | http://localhost:8082/admin/cashier | Cashier loads |

---

## Default logins

| Username | Password | Use |
|----------|----------|-----|
| `admin` | `1234` | Admin — both apps |
| `cashier` | `1234` | Cashier — Enrollment |
| **`prof.cruz`** | `1234` | Faculty grading — Registrar only |

---

## Quick URL card

| Action | URL |
|--------|-----|
| Registrar login | http://localhost:8083/registrar/login |
| Enrollment login | http://localhost:8082/login |
| Settings / readiness | http://localhost:8083/registrar/admin/settings |
| Term fees (term 1) | http://localhost:8083/registrar/admin/term-fees?termId=1 |
| Finance policy | http://localhost:8083/registrar/admin/finance-policy |
| Class scheduling | http://localhost:8083/registrar/admin/class-scheduling?termId=1 |
| Course catalog | http://localhost:8083/registrar/admin/courses |
| Curriculum | http://localhost:8083/registrar/admin/curriculum |
| Student Manager | http://localhost:8083/registrar/admin/student-manager |
| Admission review (dormant registrar path) | http://localhost:8083/registrar/admin/admission-acceptance |
| Cashier | http://localhost:8082/admin/cashier |
| Walk-in payment | http://localhost:8082/admin/walkin-payment |
| Faculty grades | http://localhost:8083/registrar/grades |
| Grade approvals | http://localhost:8083/registrar/admin/approvals |

---

## Demo instructions

### Test data rule

Use new applicants or prefixes **`DREG-*`**, **`TTRNS-*`**, **`TSHFT-*`**, **`HTEST-*`**.  
Do **not** use production student records.

### Recommended panel demo order (~90 min)

| Order | What | Time |
|-------|------|------|
| 1 | **Session 0** — smoke (above) | 15 min |
| 2 | **Finance + scheduling** — Settings, term fees, class scheduling | 10 min |
| 3 | **Admit → enroll → pay → COR** — A9 + B1–B7 | 25 min |
| 4 | **Grade one class live** — prof.cruz → admin approvals | 15 min |
| 5 | **Transferee follow-up** — A10 + B9 or Session E (without dormant dean advising) | 15 min |
| 6 | *(Optional)* **Three-track lifecycle** | 2+ hr |

Full sign-off sheet: **`HUMAN_UAT_CHECKLIST.md`**

---

### Session 0 — Fresh setup & smoke

Already covered in **Fresh setup** above. Mark complete when all smoke URLs pass.

---

### Session A — Registrar admin (~45 min)

| ID | URL | Do | Pass when |
|----|-----|-----|-----------|
| A1 | `/admin/settings` | Confirm term `1120242025` | Readiness **Ready** |
| A2 | `/admin/finance-policy` | Change admission min → Save → reload | Values persist |
| A2b | `/admin/term-fees?termId=1` | Open BSCPE Y1 S1 | No red blockers |
| A3 | `/admin/courses` | Search `AECO` | Detail loads |
| A4 | `/admin/curriculum` | Open BSCPE or BSIT | Builder loads |
| A5 | `/admin/student-manager` | Search demo student | Profile + ledger load |
| A6 | Student Manager → TOR | Credit one course | `remarks = Passed` |
| A7 | `/admin/class-scheduling?termId=1` | Expand BSCPE block; Add Slot | Times show; IRREG-A visible |
| A8 | Student Manager → Print COR | Enrolled student | COR lists subjects |
| A9 | External Admission / Cashier | Admit BSCPE Y1 and issue student ID outside Registrar | Student number created |
| A10 | External Admission / Cashier + Registrar | Start from a created Year 2 BSCPE transferee student and verify Registrar flags | Transferee / Irregular / TRANSFEREE |

---

### Session B — Enrollment / cashier (~45 min)

**Cashier:** http://localhost:8082/admin/cashier?keyword=`<student_number>`

Use student from **A9**.

| Step | Action | Pass when |
|------|--------|-----------|
| B1 | Find student by number | Profile loads |
| B2 | Assign section group **A** | Saved |
| B3 | **Enlist Entire Block** `BSCPE-1-1-A` | Staged subjects listed |
| B4 | Check assessment | Amount **> ₱0** |
| B5 | Walk-in pay ≥ **₱8,000** | Payment posted |
| B6 | **Finalize** Regular | `ENROLLED`, load **COMMITTED** |
| B7 | Print COR | Matches enlisted courses |
| B8 | Enrollment ledger | No staged-only rows after finalize |
| B9 | Irregular: enlist `IRREG-A` OK; block on irregular student **rejected** | Policy enforced |

---

### Session C — Balance / term close (~60 min)

| ID | Pass when |
|----|-----------|
| BAL-T01 | Admission payment → student ID |
| BAL-T02 | Forward debt blocks enlist |
| BAL-T03 | Walk-in pays forward first |
| BAL-T04 | Overpay → credit forward |
| BAL-T05 | Block finalize → ENROLLED |
| BAL-T06 | Drop penalty + capped refund |
| BAL-T07 | Ledger forward matches SQL |
| BAL-T08 | Y1→Y4 progression |
| BAL-T09 | Payments ↔ ledger aligned |

**Term close in UI:** Cashier → **Update Semester** → next SL code.

Detail: `MASTER_DEMO_UAT_MANUAL.md` Part 9.

---

### Session D — Faculty / grading (~30 min)

Bootstrap seeds a curated active-term faculty mix and keeps **`prof.cruz`** grading-ready.

| Step | URL / login | Pass when |
|------|-------------|-----------|
| D1 | `/login` → `prof.cruz` / `1234` | `/grades` lists classes |
| D2 | `/grades` → **View** on BSCPE section | Student row visible |
| D3 | Encode Prelim / Midterm / Finals (e.g. 85, 88, 90) | Auto-save succeeds |
| D4 | **Submit to Registrar** | Class status SUBMITTED |
| D5 | `admin` → `/admin/approvals` | Class approved; Passed |

---

### Session E — Transferee / program shift (~30 min)

| ID | Pass when |
|----|-----------|
| TRANS-T01 | Existing Y2+ transferee student resolves as Transferee / Irregular / TRANSFEREE in Registrar |
| TRANS-T02 | Single TOR credit |
| TRANS-T03 | Bulk TOR CSV |
| TRANS-T04 | Irregular offerings match curriculum |
| SHIFT-T01 | Program shift updates program + curriculum |
| SHIFT-T02 | Carry-over panel works |
| SHIFT-T03 | Credit after shift → prereqs work |

Full stories: **`THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`** Tracks 2 & 3.

---

### Session F — Term transition *(optional ~20 min)*

Fees for future terms are pre-seeded by bootstrap.

| Step | Pass when |
|------|-----------|
| F1 | Readiness green for next AY term | No blockers |
| F2 | Settings → transition to next term | Audit row; students advanced |
| F3 | One student: block → pay → finalize on new term | Golden path works |

---

## Three-track lifecycle demos (long form)

**Doc:** `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`

| Track | Story | Prefix | Style |
|-------|--------|--------|-------|
| **1 — DREG** | Y1 → Y4 BSCPE regular | `DREG-*` | Block **A** each year |
| **2 — TTRNS** | Y2 transferee → Y4 | `TTRNS-*` | Irregular (`IRREG-A`) |
| **3 — TSHFT** | Y1 BSCPE → shift at 2nd sem | `TSHFT-*` | Block S1, irregular after shift |

**Grading:** All tracks use **`prof.cruz` / `1234`**.

### Shared workflow (every track)

1. **Create applicant** (SQL) — see THREE_TRACK Part 1A  
2. **External Admission / Cashier** — complete applicant intake and note the issued student number
3. **Cashier loop** — enlist block or IRREG-A → pay ≥ ₱8,000 → finalize
4. **Grade loop** — prof.cruz encodes → admin approves at `/admin/approvals`
5. **Advance year** — Cashier → **Update Semester** → next SL code

### Quick applicant SQL

```sql
USE eacdb;
SET @ref = 'DREG-REF-001';   -- or TTRNS-REF-001, TSHFT-REF-001

DELETE FROM applicant_payments WHERE applicant_id = @ref;
DELETE FROM payments WHERE reference_number = @ref;
DELETE FROM applicants WHERE reference_number = @ref;

INSERT INTO applicants (reference_number, first_name, last_name, email, program1,
  applicant_status, application_status, term_year, created_at, updated_at)
VALUES (@ref, 'Demo', 'Student', 'demo@test.eac.edu.ph', 'BSCPE',
  'ADMISSION_PENDING', 'ADMISSION_PENDING', 'SL2024202511', NOW(), NOW());
```

### Year-level blocks (Track 1)

| Year | SL code | Block |
|------|---------|-------|
| 1 | `SL2024202511` | `BSCPE-1-1-A` |
| 2 | `SL2024202521` | `BSCPE-2-1-A` |
| 3 | `SL2024202531` | `BSCPE-3-1-A` |
| 4 | `SL2024202541` | `BSCPE-4-1-A` |

---

## Agent command cheat sheet

```cmd
REM From project root:

REM 0. Prerequisites
registrar\setup\CHECK_PREREQUISITES.cmd

REM 1. Bootstrap (~10-20 min)
registrar\setup\RUN_FRESH_SETUP.cmd

REM 2. Optional compile check
cd registrar && mvn -q -DskipTests compile
cd ..\enrollment3 && mvn -q -DskipTests compile

REM 3. Apps (two terminals)
cd registrar && mvn -q spring-boot:run
cd enrollment3 && mvn -q spring-boot:run
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `mysql` not recognized | Add MariaDB `bin` to PATH |
| Bootstrap fails step 1 | MariaDB service not running |
| `Connection refused` on 8082/8083 | Start both apps; check ports |
| Readiness not green | Re-run `setup/sql/02_materialize_term_fees.sql` and `05`; or CSV from `setup/fees/` |
| Sections all **TBA** | Re-run `registrar/db/seed_all_class_schedules.sql` then `setup/sql/03` |
| prof.cruz empty class list | Re-run `setup/sql/03_assign_prof_cruz_demo.sql` |
| Finalize blocked | Pay ≥ downpayment; clear forward debt ≥ ₱100 |
| Wrong term in UI | Re-run `setup/sql/01_activate_term_2425_s1.sql` |
| Enrollment enlistment_status error | Re-run `enrollment3/.../01_enlistment_status_schema.sql`; **restart Enrollment** |
| Port in use | `netstat -ano \| findstr :8083` and `:8082` |

---

## Handoff checklist — PC ready when

- [ ] JDK, MariaDB, Maven installed; `CHECK_PREREQUISITES` passes
- [ ] `RUN_FRESH_SETUP.cmd` completed without errors
- [ ] Registrar + Enrollment start on 8083 / 8082
- [ ] Settings readiness **Ready** for `1120242025`
- [ ] Smoke test (Step 3) passed
- [ ] Panel demo or full UAT per `HUMAN_UAT_CHECKLIST.md`

---

## Out of scope (do not block demo)

- TOR PDF upload / OCR
- Course equivalency table
- Automatic timetable generation (hard room/faculty/section overlap validation is already live for manual schedule saves)
- Six retired programs: BSBA, BSCE, BSCS, BSECE, BSED, BSMATH
- Production fee rates (seeds use demo amounts)
- Python preflight (optional)

---

## Related docs (deeper detail)

| File | Use |
|------|-----|
| **`PROJECT_STATUS_AND_ROADMAP.md`** | Master status — done, sidetracks, pending |
| **`COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`** | This file — setup + demo |
| `registrar/setup/AGENT_FRESH_SETUP.md` | Agent phased playbook |
| `registrar/setup/BOOTSTRAP_SEED_MANIFEST.md` | 17-step seed inventory |
| `registrar/setup/README.md` | Setup folder quick reference |
| **`HUMAN_UAT_CHECKLIST.md`** | Printable sign-off Sessions 0–F |
| **`THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`** | DREG / TTRNS / TSHFT + grading |
| `MASTER_DEMO_UAT_MANUAL.md` | Full reference manual |
| `START_HERE_NEW_PC_HANDOFF.md` | Short human entry point |
| `HANDOFF_UPDATES_20260609.md` | Latest fixes log |
