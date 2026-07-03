# EAC Enrollment + Registrar — Complete Demo, Setup & UAT Manual

**Start on a new PC?** Read **`PROJECT_STATUS_AND_ROADMAP.md`** → **`COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`**, then return here.

**Single-file edition** — setup, configuration, automated tests, human UAT, balance, lifecycle, transferee, and term transition.  
Last updated: 2026-06-17
**Active test term:** `1120242025` (A.Y. 2024–25, 1st sem, `term_id = 1`)

> **SQL only for bootstrap.** Run seeds in MySQL Workbench or via `run_full_uat_bootstrap.cmd` / `mysql` CLI. No Python or PowerShell required for database setup.

---

## Table of contents

| Part | Section |
|------|---------|
| **A** | [Quick start paths](#part-a--quick-start-paths) |
| **1** | [Prerequisites](#part-1--prerequisites) |
| **2** | [Database bootstrap (SQL)](#part-2--database-bootstrap-sql) |
| **3** | [SQL configuration index](#part-3--sql-configuration-index) |
| **4** | [Start applications](#part-4--start-applications) |
| **5** | [Registrar configuration](#part-5--registrar-configuration) |
| **6** | [Automated preflight](#part-6--automated-preflight) |
| **7** | [Human UAT sign-off (Sessions A–F)](#part-7--human-uat-sign-off-sessions-af) |
| **8** | [Full lifecycle demo (one student Y1→Y4)](#part-8--full-lifecycle-demo-one-student-y1y4) |
| **9** | [Balance & term close (BAL-T01–T10)](#part-9--balance--term-close-bal-t01t10) |
| **10** | [Transferee & program shift](#part-10--transferee--program-shift) |
| **11** | [Cross-app bridge tests](#part-11--cross-app-bridge-tests) |
| **12** | [Future AY & term transition](#part-12--future-ay--term-transition) |
| **13** | [Troubleshooting](#part-13--troubleshooting) |
| **14** | [Cleanup](#part-14--cleanup) |
| **15** | [Out of scope](#part-15--out-of-scope) |
| **16** | [Completion checklist](#part-16--completion-checklist) |
| **17** | [Quick reference](#part-17--quick-reference) |

---

## Part A — Quick start paths

| Goal | Steps |
|------|-------|
| **Full UAT prep** | Part 2B one command → Part 4 → Part 7 (preflight optional) |
| **Live config demo** | Part 2M (manual SQL + UI) → Part 5M → Part 7 as needed |
| **Full lifecycle demo** | Part 2B → Part 4 → Part 8 (one student Y1→Y4) |
| **Automated gate (optional)** | Part 6 — uses Python scripts; skip if doing manual UAT only |

**URLs & logins**

| App | URL | Admin |
|-----|-----|-------|
| Enrollment | http://localhost:8082 | `admin` / `1234` |
| Registrar | http://localhost:8083/registrar | `admin` / `1234` |
| Faculty | Enrollment dashboard → Registrar grade sheet | `prof.cruz` / `1234` |

**DB:** `eacdb` @ `127.0.0.1:3306`, user `root`, empty password.

---

## Part 1 — Prerequisites

### 1.1 Copy the project

Copy the entire workspace, e.g. `C:\Users\<you>\Downloads\new-20260606T044759Z-3-001\new`

| Folder | Role |
|--------|------|
| `registrar/` | Port **8083**, context `/registrar` |
| `enrollment3/` | Port **8082** |

### 1.2 Install software

| Software | Version |
|----------|---------|
| JDK | 17 or 21 |
| MariaDB / MySQL | 10.4+ / 8.0+ on port 3306 |
| Maven | 3.6+ |
| Python | 3.10+ (UAT scripts) |
| MySQL Workbench | any (recommended for SQL) |

Verify: `java -version`, `mysql --version`, `python --version`, `mvn -version`

### 1.3 Default logins (after `registrar/db/fix`)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `1234` | Admin (both apps) |
| `cashier` | `1234` | Cashier |
| `prof.cruz` | `1234` | Faculty |

Enrollment must reach Registrar: `registrar.portal-base-url=http://localhost:8083/registrar` in enrollment `application.properties`.

---

## Part 2 — Database bootstrap (SQL)

**Warning:** `registrar/db/fix` **drops and recreates** `eacdb`. Run only for a clean slate.

```powershell
cd C:\Users\<you>\Downloads\new-20260606T044759Z-3-001\new
```

### How to run SQL

| Method | Example |
|--------|---------|
| **One command (Windows)** | `registrar\db\run_full_uat_bootstrap.cmd` |
| **mysql CLI chain** | `mysql -u root < registrar/db/00_full_uat_bootstrap.sql` (from project root) |
| **Workbench** | Open each Part 2B file → Execute in order (see list in Part 2B) |

### Part 2M — Manual track (SQL + UI demo)

| Step | File / action |
|------|---------------|
| 1 | `registrar/db/fix` |
| 2 | `registrar/handoffNew/sql_manual/01_calendar_and_active_term.sql` |
| 3 | `registrar/handoffNew/sql_manual/06_retire_empty_programs.sql` |
| 4 | UI `/admin/courses` + `sql_manual/03_manual_add_course.sql` |
| 5 | UI `/admin/curriculum` **or** `registrar/db/04_seed_full_curriculum.sql` |
| 6 | UI `/admin/term-fees` **or** batch fee SQL (step 4 below) |
| 7 | `sql_manual/04_manual_bscpe_block_sections.sql` **or** UI `/admin/classes` |
| 8 | `sql_manual/05_manual_schedule_one_section.sql` **or** UI schedule row |
| 9 | `registrar/db/seed_faculty_professors_and_grading.sql` **or** UI faculty assign |
| 10 | `sql_manual/07_sample_applicant.sql` → external admission intake seed |
| 11 | `sql_manual/08_scholarship_demo_seed.sql` → scholarship eligibility demo seed |

Verify after each manual step: open `registrar/handoffNew/sql_manual/02_verify_readiness.sql` in Workbench → Execute.

### Part 2B — Batch track (full UAT prep) — ONE COMMAND, SQL ONLY

**Option A — Windows CMD** (from project root):

```text
registrar\db\run_full_uat_bootstrap.cmd
```

**Option B — mysql CLI** (from project root):

```text
mysql -u root < registrar/db/00_full_uat_bootstrap.sql
```

**Option C — Workbench** (if SOURCE fails, run each file in order — **must match** `run_full_uat_bootstrap.cmd`):

| Step | Open in Workbench → Execute |
|------|----------------------------|
| 1 | `registrar/db/fix` |
| 2 | `enrollment3/src/main/resources/sql/01_enlistment_status_schema.sql` |
| 3 | `registrar/db/04_seed_full_curriculum.sql` (~5–15 min) |
| 4 | `registrar/db/demo_scripts/00_upsert_academic_terms_calendar.sql` |
| 5 | `registrar/db/capss-demo-required/01_setup/03_seed_program_fees_full_lifecycle.sql` |
| 6 | `registrar/docs/business_logic/schema_migration_001.sql` |
| 7 | `registrar/db/manual_patches/20260608_retire_empty_curriculum_blockers.sql` |
| 8 | `registrar/db/seed_all_program_block_sections_calendar.sql` |
| 9 | `registrar/db/seed_all_class_schedules.sql` |
| 10 | `registrar/db/seed_faculty_professors_and_grading.sql` |
| 11 | `registrar/handoffNew/sql_manual/01_calendar_and_active_term.sql` |
| 12 | `registrar/handoffNew/sql_manual/11_bootstrap_materialize_active_term_fees.sql` |
| 13 | `registrar/handoffNew/sql_manual/02_verify_readiness.sql` |

Then Part 4 (start apps) → Part 5 (settings + finance policy; term-1 fees should already be green) → Part 7 human UAT.

### Activate term (if running standalone)

Default after `registrar/setup/RUN_FRESH_SETUP.cmd` is **1st sem**. To re-apply:

```sql
SOURCE registrar/setup/sql/01_activate_term_2425_s1.sql;
```

Or manually:

```sql
USE eacdb;
UPDATE academic_terms SET is_active = 0, status = 'INACTIVE';
UPDATE academic_terms SET is_active = 1, status = 'ACTIVE' WHERE term_code = '1120242025';
UPDATE system_settings SET setting_value = '1120242025' WHERE setting_key = 'CURRENT_ACADEMIC_TERM';
```

For **2nd sem** testing only: replace `1120242025` with `2120242025` and use `termId=2` in URLs.

### Optional shortcuts (not required)

| Script | Same as |
|--------|---------|
| `registrar/db/run_full_uat_bootstrap.cmd` | Part 2B (recommended — SQL only) |
| `registrar/db/00_full_uat_bootstrap.sql` | Part 2B (mysql SOURCE chain) |

---

## Part 3 — SQL configuration index

**Legend:** MANUAL = one-step demo | BATCH = run all at once | VERIFY = read-only | DESTRUCTIVE = drops data

### Core bootstrap

| File | Tag | Purpose |
|------|-----|---------|
| `registrar/db/00_full_uat_bootstrap.sql` | BATCH | **One-shot** chain — all Part 2B files |
| `registrar/db/fix` | DESTRUCTIVE | Schema + staff + sample applicants |
| `sql_manual/01_calendar_and_active_term.sql` | MANUAL | Calendar + active term |
| `sql_manual/02_verify_readiness.sql` | VERIFY | Readiness probes |
| `sql_manual/03_manual_add_course.sql` | MANUAL | One catalog course |
| `sql_manual/04_manual_bscpe_block_sections.sql` | MANUAL | BSCPE block sections only |
| `sql_manual/05_manual_schedule_one_section.sql` | MANUAL | One schedule slot |
| `sql_manual/06_retire_empty_programs.sql` | MANUAL/BATCH | Retire 6 empty programs |
| `sql_manual/07_sample_applicant.sql` | MANUAL | Applicant + payment seed for external admission flow |
| `sql_manual/08_scholarship_demo_seed.sql` | MANUAL | Scholarship eligibility candidates using completed units |

### Batch seeds (Part 2B)

| File | Purpose |
|------|---------|
| `enrollment3/.../01_enlistment_status_schema.sql` | `enlistment_status` column |
| `04_seed_full_curriculum.sql` | All programs + curricula |
| `03_seed_program_fees_full_lifecycle.sql` | Demo fee rows (legacy tables) |
| `schema_migration_001.sql` | Migrate → `program_fee_settings` |
| `11_bootstrap_materialize_active_term_fees.sql` | Materialize active demo-term fees |
| `seed_all_program_block_sections_calendar.sql` | Block sections all programs |
| `seed_all_class_schedules.sql` | Full schedule refresh |
| `seed_schedules_unscheduled_only.sql` | Append schedules only (safe) |
| `seed_faculty_professors_and_grading.sql` | Professors + grading windows |
| `02_verify_readiness.sql` | Post-bootstrap probes |

### UI equivalents

| Area | UI path |
|------|---------|
| Courses | `/admin/courses` |
| Curriculum | `/admin/curriculum` → builder; **Seed All from Manifest** |
| Term fees | `/admin/term-fees?termId=1` — **Zone 1** global import or CSV; **Zone 2** scoped edit |
| Finance policy | `/admin/finance-policy` — admission, downpayment, installments, drop penalties |
| Course fees (Enrollment) | http://localhost:8082/admin/course-fees — per-subject lab/computer add-ons |
| Classes | `/admin/classes` → open section, assign faculty, add schedule |
| Admission | External Admission / Cashier flow (Registrar path is dormant for intake) |

Fee CSV template: `registrar/handoffNew/fee_import/`

---

## Part 4 — Start applications

### Compile (first time)

```powershell
cd registrar; mvn -q -DskipTests compile
cd ..\enrollment3; mvn -q -DskipTests compile
```

### Run (two terminals)

```powershell
# Terminal 1
cd registrar; mvn -q spring-boot:run
# → http://localhost:8083/registrar/login

# Terminal 2
cd enrollment3; mvn -q spring-boot:run
# → http://localhost:8082/login
```

Health check: open both `/login` pages, or `python _runtime_logs/uat_common.py`

---

## Part 5 — Registrar configuration

### 5M — Manual track walkthrough

| # | Manual SQL | UI path |
|---|------------|---------|
| 1 | `01_calendar_and_active_term.sql` | `/admin/settings` |
| 2 | `06_retire_empty_programs.sql` | — |
| 3 | `03_manual_add_course.sql` | `/admin/courses` |
| 4 | — | `/admin/curriculum` builder |
| 5 | — | `/admin/term-fees?termId=1` |
| 6 | — | Import active-term fee scopes if needed |
| 7 | `04_manual_bscpe_block_sections.sql` | `/admin/classes` |
| 8 | `05_manual_schedule_one_section.sql` | Section → schedule row |
| 9 | — or faculty seed SQL | Assign faculty; grading windows |
| 10 | `07_sample_applicant.sql` | External admission intake seed |

### 5 — Batch track verify (after Part 2B)

**5.1 Settings** — `/admin/settings` → readiness **Ready** for `1120242025` (grading windows + INC expiry; finance gates moved to 5.2a)

**5.2a Finance policy** — `/admin/finance-policy`  
Admission min, downpayment (fixed or %), accounting block, drop penalties, installment schedule. Enrollment **Finance Policy** sidebar redirects here.

**5.2b Term fees** — `/admin/term-fees?termId=1`  
After full bootstrap, readiness should already be green. **Fallback:** CSV from `registrar/setup/fees/` or Zone 1 **Global import** from another term.

**5.2c Course fees (Enrollment)** — http://localhost:8082/admin/course-fees — per-course fee add-ons (separate from program term fees).

**5.3 Course catalog** — `/admin/courses` → search `AECO`, open row

**5.4 Curriculum** — `/admin/curriculum` → open BSCPE/BSIT builder  
Retired (no curriculum): BSBA, BSCE, BSCS, BSECE, BSED, BSMATH

**5.5 Classes** — `/admin/classes` → schedules not TBA; assign faculty if blank

**5.6 Admissions** — external Admission / Cashier owns Y1 and Y2+ intake. Registrar should only be used afterward to validate downstream academic data on existing students.

**5.7 Student Manager** — profile, TOR workspace, Print Registration Form, Program Shift, **Installment Plan Override** (per-student cashier split; falls back to Finance Policy term/default)

**5.8 Grading** — `prof.cruz` at Registrar `/grades` → encode → submit → admin approve

**5.8a Scholarship eligibility** — run `sql_manual/08_scholarship_demo_seed.sql`, then open `/admin/scholarships`.

- Policy should show **Minimum Completed Units = 27**.
- `SCH-UAT-ELIGIBLE` / Sofia Scholar should show **27** units and **Eligible**.
- `SCH-UAT-LOWUNITS` / Liam Low Units should show **24** units and **Not eligible** with reason `Needs at least 27 graded/taken unit(s)`.
- Sofia: **Submit for Review** → **Approve** → verify the discount is still inactive → **Post** → verify `POSTED` and active scholarship state.
- Liam remains blocked from submission by the 27-unit policy.

**5.9 Enrollment cashier** — `/admin/cashier`, `/admin/walkin-payment`, `/admin/ledger`

**5.10 Future AY prep (optional)** — `python _runtime_logs/prep_future_ay_term.py`

---

## Part 6 — Automated preflight (optional)

Uses Python — **skip this part** if you are doing manual UAT only in Part 7.

```powershell
python _runtime_logs/run_full_preflight.py
```

Quick mode (skip Y1→Y4): `python _runtime_logs/run_full_preflight.py --quick`

**Pass:** `_runtime_logs/preflight_result.json` → `"all_pass": true`

| Suite | Validates |
|-------|-------------|
| `bridge_uat_run.py` | Grades, INC, blocks, TOR workspace |
| `professor_grading_uat.py` | Encode → submit → approve → change |
| `lifecycle_balance_uat.py` | Forward balance, walk-in allocation |
| `run_transferee_uat.py` | TOR credit, shift, Y2 transferee |
| `cross_app_verify.py` | Admit → finalize → registrar mirror |
| `extended_cross_app_verify.py` | COR + grades; scholar path |
| `term2_edge_verify.py` | Staged vs committed, waitlist, drop |
| `lifecycle_y1_y4_run.py` | Y1S2→Y4S2 on one calendar term |

Individual suites: see Part 17 quick reference.

---

## Part 7 — Human UAT sign-off (Sessions A–F)

> **Easier to read:** use **`HUMAN_UAT_CHECKLIST.md`** in this folder — same tests, formatted for demo sign-off with URLs and pass criteria per card.  
> **Lifecycle demos:** **`THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`**.  
> **Fresh DB:** `registrar/setup/RUN_FRESH_SETUP.cmd` → active term **`1120242025`**.

**Rules:** Use disposable prefixes `HTEST-*`, `LCYCL-*`, `DREG-*`, `TTRNS-*`, `TSHFT-*`, or new admits. Do not test on production records.

### Sign-off record

| Session | Tester | Date | Pass? | Notes |
|---------|--------|------|-------|-------|
| A — Registrar admin | | | ☐ | |
| B — Enrollment / cashier | | | ☐ | |
| C — Balance / term close | | | ☐ | |
| D — Faculty / grading | | | ☐ | |
| E — Transferee / shift | | | ☐ | |
| F — Term transition (optional) | | | ☐ | |

### Session A — Registrar admin (~45 min)

| # | Test | Steps | Pass? |
|---|------|-------|-------|
| A1 | Settings readiness | `/admin/settings` → **Ready** for `1120242025` | ☐ |
| A2 | Finance policy | `/admin/finance-policy` → admission, downpayment, installments save | ☐ |
| A2b | Term fees | `/admin/term-fees?termId=1` → Zone 1 global + Zone 2 scoped; no blockers | ☐ |
| A3 | Course catalog | `/admin/courses` → search, open row | ☐ |
| A4 | Curriculum | `/admin/curriculum` → BSCPE/BSIT builder | ☐ |
| A5 | Student Manager | Profile, ledger, load | ☐ |
| A5b | Per-student installments | Student Manager → **Installment Plan Override** → save 4 rows → Enrollment cashier shows 4-way split; **Clear override** restores term plan | ☐ |
| A6 | TOR credit | **TOR & Transfer Crediting** → single + bulk CSV | ☐ |
| A7 | Class scheduling | `/admin/class-scheduling?termId=1` → search blocks (e.g. `BSIT`); expand block → **Add Slot** sets day/time/room per course; schedules show as tags (not just a count). **Course Sections** below is the alternate view for block sections and summer/tutorial specials. | ☐ |
| A8 | Print Registration Form | Student Manager → Print Registration Form | ☐ |
| A9 | Admission Y1 | External Admission / Cashier admits applicant and issues student ID; Registrar not required | ☐ |
| A10 | External Y2+ intake | Existing Y2+ transferee student opens in Registrar with expected flags | ☐ |
| A11 | Scholarship workflow | Minimum Completed Units = 27; Sofia Pending → Approved → Posted; Liam blocked by units | ☐ |

TOR bulk CSV:

```csv
course_code,numeric_grade,source_school,note
AECO 11,1.75,Prior College,TOR 2024
```

### Session B — Enrollment / cashier (~45 min)

| # | Test | Steps | Pass? |
|---|------|-------|-------|
| B1 | Find student | Cashier search by student number | ☐ |
| B2 | Block assign | Section group **A** for BSCPE Y1 | ☐ |
| B3 | Block enlist | Staged subjects appear (`BSCPE-1-1-A` on 1st sem) | ☐ |
| B4 | Assessment | Matches term fees (not zero) | ☐ |
| B5 | Walk-in pay | Post payment ≥ **₱8,000** | ☐ |
| B6 | Finalize | ENROLLED, COMMITTED load | ☐ |
| B7 | COR export | COR matches enlisted courses | ☐ |
| B8 | Ledger | No staged-only rows after finalize | ☐ |
| B9 | Irregular add | A seeded block section enlists OK; legacy irregular-open sections stay rejected | ☐ |
| B10 | Drop | Penalty/refund reasonable | ☐ |

### Session C — Balance / term close (~60 min)

Full step-by-step: **Part 9** (BAL-T01–T10). Summary checklist:

| ID | Pass when | ☐ |
|----|-----------|---|
| BAL-T01 | Admission → student ID | ☐ |
| BAL-T02 | Forward debt blocks enlist | ☐ |
| BAL-T03 | Walk-in pays forward first | ☐ |
| BAL-T04 | Overpay → credit forward | ☐ |
| BAL-T05 | Block finalize → ENROLLED | ☐ |
| BAL-T06 | Drop penalty + capped refund | ☐ |
| BAL-T07 | Ledger matches SQL forward | ☐ |
| BAL-T08 | Y1→Y4 block + forward carry | ☐ |
| BAL-T09 | Payments ↔ ledger aligned | ☐ |
| BAL-T10 | Scholar discount at close (optional) | ☐ |

### Session D — Faculty / grading (~30 min)

| # | Test | Steps | Pass? |
|---|------|-------|-------|
| D1 | Faculty login | Enrollment `/faculty/dashboard` → Registrar grade sheet | ☐ |
| D2 | Encode grades | Prelim/midterm/finals | ☐ |
| D3 | Submit | Submit class grades | ☐ |
| D4 | Admin approve | Registrar approves sheet | ☐ |
| D5 | Grade change | Request + approve | ☐ |
| D6 | Closed period | Close prelim → faculty cannot edit prelim | ☐ |
| D7 | INC expire | Settings → Expire INC → failed in prereqs | ☐ |

### Session E — Transferee / program shift (~30 min)

Full steps: **Part 10**. Summary:

| ID | Pass when | ☐ |
|----|-----------|---|
| TRANS-T01 | Existing Y2+ transferee student resolves as Transferee/Irregular/TRANSFEREE in Registrar | ☐ |
| TRANS-T02 | Single TOR credit | ☐ |
| TRANS-T03 | Bulk TOR CSV | ☐ |
| TRANS-T04 | Irregular offerings match assigned curriculum | ☐ |
| SHIFT-T01 | Program shift updates program + curriculum | ☐ |
| SHIFT-T02 | Carry-over panel (carried/orphan/required) | ☐ |
| SHIFT-T03 | Credit after shift → prereqs work | ☐ |

### Session F — Term transition (~20 min, optional)

After Sessions A–E on `1120242025`. Full steps: **Part 12**. (Fees for `2120242025` and future terms are pre-seeded by bootstrap.)

| # | Test | Pass? |
|---|------|-------|
| F1 | Readiness green for `1120252026` | ☐ |
| F2 | Settings → transition to `1120252026` | ☐ |
| F3 | Golden path on new term: block → pay → finalize | ☐ |
| F4 | Document keep vs restore forward balance | ☐ |

---

## Part 8 — Full lifecycle demo (one student Y1→Y4)

**Goal:** Walk one BSCPE student from admission through four year levels starting on active term `1120242025` (1st sem), with balance forward each close.

**Program:** BSCPE | **Prefix:** `LCYCL-Y4-001` | **Preferred:** `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` Track 1 (DREG)

| Year | SL code (1st sem start) | Block section |
|------|-------------------------|---------------|
| 1 | SL2024202511 | BSCPE-1-1-A |
| 2 | SL2024202521 | BSCPE-2-1-A |
| 3 | SL2024202531 | BSCPE-3-1-A |
| 4 | SL2024202541 | BSCPE-4-1-A |

*(2nd-sem variant: use `SL…12` and `BSCPE-{Y}-2-A` if cashier advances to S2.)*

### Step 0 — Prereq: Part 2B + apps running + readiness green

### Step 1 — Admit student (BAL-T01)

```sql
USE eacdb;
SET @ref = 'LCYCL-REF-Y4';

DELETE FROM applicant_payments WHERE applicant_id = @ref;
DELETE FROM payments WHERE reference_number = @ref;
DELETE FROM applicants WHERE reference_number = @ref;

INSERT INTO applicants (reference_number, first_name, last_name, email, program1,
  applicant_status, application_status, term_year, created_at, updated_at)
VALUES (@ref, 'Life', 'Cycle', 'lcycl@test.eac.edu.ph', 'BSCPE',
  'ADMISSION_PENDING', 'ADMISSION_PENDING', 'SL2024202512', NOW(), NOW());
```

1. External Admission/Cashier -> complete regular applicant admission, payment, enrollment, and student-number issuance.
2. Registrar is not part of the regular Y1 lifecycle in the current canon. Do not use Registrar Admission Acceptance as a live intake gate; the prior irregular Dean / Faculty bridge is dormant.
3. Note the student number issued by Admission/Cashier -> use as `@sn` below (e.g. `26-2-xxxxx`).

### Step 2 — Per year loop (repeat for Y1, Y2, Y3, Y4)

**Before Y2+:** seed prior-year Passed grades:

```sql
USE eacdb;
SET @sn = '<your_student_number>';
SET @yl = 1;  -- set to 1 before Y2 enlist, 2 before Y3, 3 before Y4

INSERT INTO grades (student_id, course_id, remarks, status)
SELECT @sn, cc.course_id, 'Passed', 'SUBMITTED'
FROM curriculum_courses cc
JOIN curriculum_templates ct ON ct.curriculum_id = cc.curriculum_id
JOIN programs p ON p.program_id = ct.program_id
WHERE p.program_code = 'BSCPE' AND ct.is_active = 1 AND cc.year_level <= @yl
ON DUPLICATE KEY UPDATE remarks='Passed';
```

**If advancing from prior year:** term close first, then bump SL:

```sql
UPDATE sys_users SET term_year='SL2024202522', year_level=2, semester=2, admission_status='PENDING'
WHERE username=@sn;
UPDATE students SET term_year='SL2024202522', year_level=2, semester=2, admission_status='PENDING'
WHERE student_number=@sn;
-- Adjust SL/year_level for Y3 (2532/3) and Y4 (2542/4)
```

**UI per year:**

1. If forward ≥ ₱100: Cashier → walk-in pay forward down first
2. Cashier → assign section **A**
3. **Enlist Entire Block** (e.g. `BSCPE-1-2-A`)
4. Walk-in pay ≥ **₱8,000**
5. **Finalize** as Regular
6. **Update Semester** to next SL (posts forward balance at close)
7. Verify forward:

```sql
SELECT COALESCE(SUM(debit),0)-COALESCE(SUM(credit),0) AS forward_net
FROM student_ledger WHERE student_id=@sn AND transaction_type='FORWARDED_BALANCE';
```

### Step 3 — Grading (optional mid-lifecycle)

After Y1 finalize: login `prof.cruz` → Registrar `/grades` → encode/submit → admin approve. Confirms cross-app grade bridge (Part 11).

### Step 4 — Automated equivalent

```powershell
python _runtime_logs/lifecycle_y1_y4_run.py
```

Included in full preflight (not `--quick` mode).

### Lifecycle demo sign-off

| Step | Done? |
|------|-------|
| Admitted Y1 with payment | ☐ |
| Y1 block enlist + finalize | ☐ |
| Term close → forward posted | ☐ |
| Y2 block enlist + finalize | ☐ |
| Y3 block enlist + finalize | ☐ |
| Y4 block enlist + finalize | ☐ |
| Forward balance sane each year | ☐ |

---

## Part 9 — Balance & term close (BAL-T01–T10)

**Rules:**

- Student `term_year` uses **SL format** (e.g. `SL2024202512` = Y1, 2nd sem)
- Term close uses `payments.term_year` — tag payments with SL when posting
- Walk-in pays **forward first**, then current term
- Forward debt **≥ ₱100** blocks enlist
- Downpayment default: **₱3,000**; UAT golden path uses **≥ ₱8,000**

Automated: `python _runtime_logs/lifecycle_balance_uat.py`

---

### BAL-T01 — Admission → student ID

See Part 8 Step 1. Pass when: student ID created; admission payment ≥ ₱1,000.

---

### BAL-T02 — Debt forwarded after term close

Partial pay → advance term → forward → enlist blocked.

```sql
USE eacdb;
SET @sn = 'LCYCL-FIN-001';
SET @sl  = 'SL2024202512';
SET @sl2 = 'SL2024202522';

DELETE FROM student_ledger WHERE student_id = @sn;
DELETE FROM payments WHERE reference_number = @sn;

INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit)
VALUES (@sn, 'TUITION_ASSESSMENT', 'Test tuition', 36000, 0),
       (@sn, 'MISC_ASSESSMENT',    'Test misc',     1000,  0);

INSERT INTO payments (transaction_id, reference_number, amount, payment_method, status,
  payment_date, term_year, year_level, semester)
VALUES (UUID(), @sn, 10000, 'Cash (OTC)', 'COMPLETED', NOW(), @sl, 1, 2);
```

UI: Cashier → **Update Semester** to `SL2024202522`. Expect forward ~**₱27,000**; enlist blocked.

Verify:

```sql
SELECT COALESCE(SUM(debit),0)-COALESCE(SUM(credit),0) AS forward_net
FROM student_ledger WHERE student_id=@sn AND transaction_type='FORWARDED_BALANCE';
```

---

### BAL-T03 — Walk-in pays forward first

From BAL-T02: Cashier → walk-in **₱5,000**. Ledger shows `FORWARDED_BALANCE` credit first; forward ~₱22,000.

---

### BAL-T04 — Credit forward (overpayment)

```sql
DELETE FROM student_ledger WHERE student_id = @sn;
DELETE FROM payments WHERE reference_number = @sn;
INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit)
VALUES (@sn, 'TUITION_ASSESSMENT', 'Test', 20000, 0), (@sn, 'MISC_ASSESSMENT', 'Test', 500, 0);
INSERT INTO payments (transaction_id, reference_number, amount, payment_method, status,
  payment_date, term_year, year_level, semester)
VALUES (UUID(), @sn, 25000, 'Cash (OTC)', 'COMPLETED', NOW(), @sl, 1, 2);
```

Advance term → expect **negative** forward (~ -₱4,500 credit).

---

### BAL-T05 — Full block enroll + finalize (BSCPE Y1 S2)

```sql
SET @sn = 'LCYCL-BLK-001';

INSERT INTO grades (student_id, course_id, remarks, status)
SELECT @sn, c.course_id, 'Passed', 'SUBMITTED'
FROM courses c WHERE c.course_code IN ('PE1 11', 'ANS1 11')
ON DUPLICATE KEY UPDATE remarks='Passed', status='SUBMITTED';

UPDATE sys_users SET term_year='SL2024202512', year_level=1, semester=2,
  section_group='A', admission_status='PENDING' WHERE username=@sn;
UPDATE students SET admission_status='PENDING' WHERE student_number=@sn;
DELETE FROM student_enlistments WHERE student_id=@sn;
DELETE FROM student_ledger WHERE student_id=@sn;
```

UI: Assign section A → block enlist → pay ≥ ₱8,000 → finalize. Expect `ENROLLED` + assessment debits.

---

### BAL-T06 — Drop with refund cap

Enroll + overpay → drop one committed subject. Verify `DROP_PENALTY` and capped `REFUND` in ledger.

---

### BAL-T07 — Ledger view

http://localhost:8082/admin/ledger?keyword=LCYCL-FIN-001 — forward matches SQL; view-term dropdown read-only.

---

### BAL-T08 — Y1S2 → Y4S2 progression

**Full walkthrough: Part 8.** Automated: `python _runtime_logs/lifecycle_y1_y4_run.py`

---

### BAL-T09 — Payments vs ledger cross-check

```sql
SET @sn = 'LCYCL-FIN-001';
SELECT 'payments_table' AS src, COALESCE(SUM(amount),0) AS total
FROM payments WHERE reference_number=@sn AND status='COMPLETED'
UNION ALL
SELECT 'ledger_pay', COALESCE(SUM(credit),0)
FROM student_ledger WHERE student_id=@sn
  AND transaction_type IN ('PAYMENT','INITIAL_PAYMENT','FORWARDED_BALANCE');
```

---

### BAL-T10 — Scholar mirror (optional)

Enrollment Ledger → set ACADEMIC scholarship → re-run term close; forward reflects discount.

---

## Part 10 — Transferee & program shift

Automated: `python _runtime_logs/run_transferee_uat.py`

### TRANS-T01 — Existing transferee student state (Y2+)

Start from a student already created by the external Admission / Cashier flow. In Registrar, assert:

- `sys_users.student_type = 'Transferee'`
- `sys_users.enrollment_status_type = 'Irregular'`
- `student_curriculum_assignments.assignment_type = 'TRANSFEREE'`

### TRANS-T02 — Single TOR credit

Student Manager → **TOR & Transfer Crediting** → submit one row → approve it in **Accreditation Requests**. Assert: request first appears as `PENDING`, then after registrar approval the credited course appears in history, `grade_lock_reason LIKE 'TRANSFER_CREDIT%'`, and deficiency pressure drops.

### TRANS-T03 — Bulk TOR CSV

```csv
course_code,numeric_grade,source_school,note
AECO 11,1.75,Prior College,TOR 2024
PATHFIT 1,2.0,,
```

Paste → **Submit bulk request** → approve pending rows in **Accreditation Requests**. Assert summary + skip reasons + approval/posting events.

### TRANS-T04 — Irregular curriculum scope

Enrollment cashier → irregular offerings align with `student_curriculum_assignments.curriculum_id`.

### SHIFT-T01 — Program shift

Student Manager → **Program Shift** → new program. Assert: program updated, `Irregular`, new assignment row, staged enlistments cleared.

### SHIFT-T02 — Carry-over report

Panel shows: **Carried over** | **Orphan passed** | **Required**

### SHIFT-T03 — Credit after shift

Credit remaining deficiencies → irregular enlist honors credited grades via `GradeOutcomeSql`.

---

## Part 11 — Cross-app bridge tests

Registrar and Enrollment share `eacdb`. Both use `GradeOutcomeSql` for passed/failed/INC semantics.

| ID | Steps | Assert |
|----|-------|--------|
| BRIDGE-01 | Registrar: grade Passed (`remarks`, `status=SUBMITTED`) | Enrollment prereq satisfied |
| BRIDGE-02 | Registrar: Expire INC | Scholarship blocked; prereq not satisfied |
| BRIDGE-03 | Irregular tries block section | Rejected in Enrollment |
| BRIDGE-04 | Enrollment faculty dashboard | Opens Registrar grade sheet |
| BRIDGE-05 | Closed prelim + faculty save | Registrar returns error |
| BRIDGE-06 | Student Manager deficiencies | Lists unpassed curriculum courses |
| BRIDGE-07 | Transfer credit + irregular enlist | Offerings match assigned curriculum |

Automated: `python _runtime_logs/bridge_uat_run.py` + full preflight.

**Config:** Enrollment `registrar.portal-base-url=http://localhost:8083/registrar`

---

## Part 12 — Future AY & term transition

Prepare terms beyond `2120242025`:

```powershell
python _runtime_logs/prep_future_ay_term.py
```

Prepares `1120252026`, `2120252026`, and later through 2027–2028 (fees copy + sections + schedules + readiness).

| Target term | Copied from |
|-------------|-------------|
| `1120252026` | `1120242025` |
| `2120252026` | `2120242025` |

Requires Registrar on **8083**.

### Session F — Term transition smoke

1. **F1** — `/admin/settings` → readiness green for `1120252026`
2. **F2** — Settings → transition to `1120252026` → audit row; students advanced
3. **F3** — One student: block → pay ≥ ₱8,000 → finalize on new term
4. **F4** — Document forward balance policy (keep vs restore)

Do **not** switch `CURRENT_ACADEMIC_TERM` until readiness passes and staff sign off.

Single term override: `python _runtime_logs/prep_future_ay_term.py 1120252026->1120242025`

---

## Part 13 — Troubleshooting

| Symptom | Fix |
|---------|-----|
| Connection refused 8082/8083 | Start both apps; check ports |
| Preflight Registrar not reachable | Port **8083**, context `/registrar` |
| Readiness: missing fee scopes | Re-run `run_full_uat_bootstrap.cmd`; fallback Part 5.2b Zone 1 global import |
| Readiness: curriculum blockers | Run `04_seed_full_curriculum.sql` or Seed All from Manifest |
| Sections all TBA | Re-run `seed_all_class_schedules.sql` |
| Finalize blocked | Pay ≥ downpayment; complete block; seed prereq grades for Y2+ |
| Faculty roster empty | Grading at Registrar `/grades/view/{sectionId}` |
| Collation errors in SQL | Use Python UAT scripts |
| MySQL password not empty | Update both `application.properties` |

```powershell
netstat -ano | findstr :8083
netstat -ano | findstr :8082
```

---

## Part 14 — Cleanup

```sql
USE eacdb;
DELETE FROM student_enlistments WHERE student_id LIKE 'HTEST%' OR student_id LIKE 'LCYCL%' OR student_id LIKE 'BRIDGE%' OR student_id LIKE 'TSHFT%' OR student_id LIKE 'TTRNS%';
DELETE FROM student_ledger WHERE student_id LIKE 'HTEST%' OR student_id LIKE 'LCYCL%' OR student_id LIKE 'BRIDGE%' OR student_id LIKE 'TSHFT%' OR student_id LIKE 'TTRNS%';
DELETE FROM payments WHERE reference_number LIKE 'HTEST%' OR reference_number LIKE 'LCYCL%' OR reference_number LIKE 'BRIDGE%' OR reference_number LIKE 'TSHFT%' OR reference_number LIKE 'TTRNS%';
DELETE FROM grades WHERE student_id LIKE 'HTEST%' OR student_id LIKE 'LCYCL%' OR student_id LIKE 'BRIDGE%' OR student_id LIKE 'TSHFT%' OR student_id LIKE 'TTRNS%';
DELETE FROM student_curriculum_assignments WHERE student_number LIKE 'HTEST%' OR student_number LIKE 'LCYCL%' OR student_number LIKE 'BRIDGE%' OR student_number LIKE 'TSHFT%' OR student_number LIKE 'TTRNS%';
DELETE FROM students WHERE student_number LIKE 'HTEST%' OR student_number LIKE 'LCYCL%' OR student_number LIKE 'BRIDGE%' OR student_number LIKE 'TSHFT%' OR student_number LIKE 'TTRNS%';
DELETE FROM sys_users WHERE username LIKE 'HTEST%' OR username LIKE 'LCYCL%' OR username LIKE 'BRIDGE%' OR username LIKE 'TSHFT%' OR username LIKE 'TTRNS%';
DELETE FROM applicants WHERE reference_number LIKE 'LCYCL%';
```

---

## Part 15 — Out of scope

Do **not** fail UAT for these:

- TOR PDF upload / OCR
- Course equivalency table
- Scheduling automation / room conflict engine
- Six retired programs (BSBA, BSCE, BSCS, BSECE, BSED, BSMATH)
- Registrar Spring Security hardening (**deferred** — see `PROPOSAL_REGISTRAR_SPRING_SECURITY.md`)
- Deprecated registrar walk-in payment screens
- Full official printable TOR
- Block-section generator UI (SQL seed remains)

---

## Part 16 — Completion checklist

Before production pilot sign-off:

- [ ] Part 2M or 2B SQL bootstrap verified (`02_verify_readiness.sql`)
- [ ] Part 5 registrar configuration done; readiness green
- [ ] Part 6 preflight **8/8 PASS**
- [ ] Part 7 Sessions **A–E** signed off
- [ ] Part 8 lifecycle demo (or BAL-T08) passed
- [ ] No open P1 defects (wrong balance, wrong grade, block on irregular, TBA schedules)
- [ ] Part 14 cleanup run or test data isolated
- [ ] Optional Part 12 Session F if next AY go-live planned

---

## Part 17 — Quick reference

| Item | Value |
|------|-------|
| Latest fixes log | `registrar/handoffNew/HANDOFF_UPDATES_20260609.md` |
| This manual | `registrar/handoffNew/MASTER_DEMO_UAT_MANUAL.md` |
| Finance policy UI | `/admin/finance-policy` |
| Program fees UI | `/admin/term-fees?termId=1` |
| Course fees (Enrollment) | http://localhost:8082/admin/course-fees |
| Setup folder | `registrar/setup/` (bootstrap + fee CSVs) |
| DB | `eacdb` @ `127.0.0.1:3306` |
| Enrollment | http://localhost:8082 |
| Registrar | http://localhost:8083/registrar |
| Active term | `1120242025` (1st sem, term_id=1) |
| Admin / Cashier | `admin` / `1234` |
| Professor | `prof.cruz` / `1234` |
| Fresh bootstrap | `registrar/setup/RUN_FRESH_SETUP.cmd` |
| Human UAT checklist | `registrar/handoffNew/HUMAN_UAT_CHECKLIST.md` |
| Full bootstrap (mysql SOURCE) | `mysql -u root < registrar/db/00_full_uat_bootstrap.sql` |
| Run one SQL file (Workbench) | File → Open → Execute |
| Preflight (optional, Python) | `python _runtime_logs/run_full_preflight.py` |
