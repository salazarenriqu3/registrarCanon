# Three-Track Student Lifecycle Demo Manual

Last updated: 2026-06-17
Companion to **`HUMAN_UAT_CHECKLIST.md`** and **`MASTER_DEMO_UAT_MANUAL.md`** — three end-to-end stories with **manual grade encoding** by one professor.

---

## What this covers

| Track | Story | Student prefix | Enrollment style |
|-------|--------|----------------|------------------|
| **1 — DREG** | Y1 → Y4 BSCPE regular, no sidetracks | `DREG-*` | Block **A** each year |
| **2 — TTRNS** | Y2 transferee → completes remaining years | `TTRNS-*` | Irregular across block sections |
| **3 — TSHFT** | Y1 regular BSCPE → **program shift at 2nd sem** | `TSHFT-*` | Block S1, then irregular after shift |

**Grading:** All tracks use **`prof.cruz` / `1234`** on Registrar. Fresh setup already seeds a curated active-term faculty mix and keeps Cruz grading-ready (`registrar/setup/sql/03_assign_prof_cruz_demo.sql`).

**Active term:** `1120242025` (A.Y. 2024–25, **1st Semester**). Y1 golden-path block: **`BSCPE-1-1-A`**.

**Demo compression:** Tracks 1 and 2 can advance through Y2–Y4 by bumping SL codes on the same calendar term (see Part 1E). For real S1→S2 progression within Y1, use Track 3 or cashier **Update Semester**.

Scope note: the old Registrar Dean / Faculty irregular new-enrollee advising bridge is dormant. Track 2 now starts only after the external Admission / Cashier flow has already created the transferee student record.

---

## Prerequisites

1. Full bootstrap — `registrar\setup\RUN_FRESH_SETUP.cmd`
2. **Registrar** running — http://localhost:8083/registrar  
3. **Enrollment** running — http://localhost:8082  
4. Settings readiness **green** for `1120242025`  
5. Logins:

| Role | Username | Password | URL |
|------|----------|----------|-----|
| Admin | `admin` | `1234` | Both apps |
| Cashier | `admin` or `cashier` | `1234` | Enrollment |
| Professor | **`prof.cruz`** | `1234` | **Registrar only** |

---

## Part 0 — One-time setup (included in fresh bootstrap)

### 0A. Restore the demo faculty assignment mix

**Already done** if you ran `RUN_FRESH_SETUP.cmd`. Re-run only if needed:

```sql
-- Or execute: registrar/setup/sql/03_assign_prof_cruz_demo.sql

```sql
USE eacdb;

SET @term_id = (SELECT term_id FROM academic_terms WHERE is_active = 1 LIMIT 1);

SELECT f.employee_number, COUNT(*) AS sections
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id
GROUP BY f.employee_number;
```

**Pass when:** the active term has assignments across multiple faculty, and `prof.cruz` still has one or more grading-ready classes.

### 0B. Confirm grading windows are open

Bootstrap + `seed_faculty_professors_and_grading.sql` set **FORCE_OPEN** through 2026-12-31. Quick check:

```sql
SELECT grading_period, override_status, start_date, end_date
FROM grading_term_windows
WHERE term_id = (SELECT term_id FROM academic_terms WHERE is_active = 1 LIMIT 1);
```

If any period shows **CLOSED** in the UI, open **Registrar → Settings**, pick the active term, set overrides to **FORCE_OPEN**, save.

---

## Part 1 — Shared workflows (all tracks)

### 1A. Create applicant (SQL)

Replace `@ref` and name fields per track.

```sql
USE eacdb;
SET @ref = 'DREG-REF-001';   -- change per track: TTRNS-REF-001, TSHFT-REF-001

DELETE FROM applicant_payments WHERE applicant_id = @ref;
DELETE FROM payments WHERE reference_number = @ref;
DELETE FROM applicants WHERE reference_number = @ref;

INSERT INTO applicants (reference_number, first_name, last_name, email, program1,
  applicant_status, application_status, term_year, created_at, updated_at)
VALUES (@ref, 'Demo', 'Student', 'demo@test.eac.edu.ph', 'BSCPE',
  'ADMISSION_PENDING', 'ADMISSION_PENDING', 'SL2024202511', NOW(), NOW());
```

### 1B. Admission payment + student number

Current canon:

1. Track 1 & 3 regular applicants continue through external Admission/Cashier. Registrar does not generate their pre-registration, section assignment, enrollment, or student number.
2. Track 2 transferee / irregular intake also starts outside Registrar in the current canon. Use the student number already issued by the external Admission / Cashier flow.
3. Registrar starts once the student record exists and staff needs academic follow-up such as TOR crediting, program shift, grading, approvals, or reporting.
4. Note that student number and use it as `@sn` below.

### 1C. Enrollment cashier loop

**Cashier terminal:** http://localhost:8082/admin/cashier?keyword=`@sn`

| Step | Action |
|------|--------|
| 1 | If **forward balance ≥ ₱100** → Walk-in Payment first (pays forward debt) |
| 2 | Assign section group **A** (regular block tracks) |
| 3 | **Enlist Entire Block** (e.g. `BSCPE-1-1-A` on 1st sem) *or* pick irregular subjects |
| 4 | Walk-in pay **≥ ₱8,000** (UAT golden path; downpayment default is ₱3,000) |
| 5 | **Finalize** as **Regular** or **Irregular** |
| 6 | Optional: **Print COR** from Student Manager |

After finalize, Enrollment auto-creates **DRAFT** rows in `grades` for each committed subject.

### 1D. Manual grading loop (Prof. Cruz) — **repeat after every finalize**

1. **Logout** admin (if needed). **Registrar login:**  
   http://localhost:8083/registrar/login → **`prof.cruz` / `1234`**

2. **My Classes:** http://localhost:8083/registrar/grades  
   You will see many sections (all assigned to Cruz). Open only sections where your demo student appears.

3. **Open grade sheet:** click **View** on a class → `/grades/view/{section_id}`  
   - Enter **Prelim**, **Midterm**, **Finals** (e.g. `85`, `88`, `90` — fields auto-save)  
   - Repeat for **every subject** in that enrollment

4. **Submit class:** green **Submit to Registrar** (once all students in that section are encoded)

5. **Admin approve:** logout → login **`admin`** →  
   http://localhost:8083/registrar/admin/approvals  
   **Approve** each submitted class for your demo student’s sections

6. **Verify (optional):**  
   **Student Manager** → http://localhost:8083/registrar/admin/student-manager?username=`@sn`  
   Deficiencies should drop as courses become **Passed**.

**Tip:** You do **not** need SQL `INSERT INTO grades … Passed` between years if you complete steps 3–5 for all subjects before advancing.

### 1E. Term close / advance to next year level

Still on **Cashier** for `@sn`:

1. Dropdown **Academic Term** → select next SL (e.g. `SL2024202522` for Y2 S2)
2. Submit **Update Semester** (posts forward balance / term close)
3. Confirm `admission_status` returns to **PENDING** for the new SL

Forward balance check:

```sql
SET @sn = '26-2-00005';
SELECT COALESCE(SUM(debit),0)-COALESCE(SUM(credit),0) AS forward_net
FROM student_ledger
WHERE student_id = @sn AND transaction_type = 'FORWARDED_BALANCE';
```

If forward ≥ ₱100 blocks enlistment → walk-in pay forward first (step 1C.1).

### 1F. Year-level reference (Tracks 1 & 2)

| Year | SL code | Block (Track 1) |
|------|---------|-----------------|
| 1 | `SL2024202511` | `BSCPE-1-1-A` |
| 2 | `SL2024202521` | `BSCPE-2-1-A` |
| 3 | `SL2024202531` | `BSCPE-3-1-A` |
| 4 | `SL2024202541` | `BSCPE-4-1-A` |

*(After Y1 S1, use cashier **Update Semester** → `SL2024202512` / `BSCPE-1-2-A` for 2nd sem.)*

**Fallback only** (if block enlist fails on prereqs and you skipped grading): seed passed rows — prefer real grading instead.

```sql
SET @sn = '26-2-00005';
SET @yl = 1;  -- 1 before Y2, 2 before Y3, 3 before Y4

INSERT INTO grades (student_id, course_id, remarks, status)
SELECT @sn, cc.course_id, 'Passed', 'SUBMITTED'
FROM curriculum_courses cc
JOIN curriculum_templates ct ON ct.curriculum_id = cc.curriculum_id
JOIN programs p ON p.program_id = ct.program_id
WHERE p.program_code = 'BSCPE' AND ct.is_active = 1 AND cc.year_level <= @yl
ON DUPLICATE KEY UPDATE remarks='Passed', status='SUBMITTED';
```

---

## Part 2 — Track 1: Regular Y1 → Y4 (DREG)

**Goal:** One BSCPE regular student, four block enrollments, **you grade every subject**, balance forwards each year.

### Setup

| Item | Value |
|------|--------|
| Applicant ref | `DREG-REF-001` |
| Program | BSCPE |
| Admit year | Y1 |
| Prefix for cleanup | `DREG%` |

### Steps

| # | Year | SL | Block | After finalize |
|---|------|-----|-------|----------------|
| 1 | Y1 | `SL2024202512` | `BSCPE-1-2-A` | **Grade all Y1 subjects** (Part 1D) → Update Semester → `SL2024202522` |
| 2 | Y2 | `SL2024202522` | `BSCPE-2-2-A` | Grade all → advance → `SL2024202532` |
| 3 | Y3 | `SL2024202532` | `BSCPE-3-2-A` | Grade all → advance → `SL2024202542` |
| 4 | Y4 | `SL2024202542` | `BSCPE-4-2-A` | Grade all → done |

### Sign-off

| Check | ☐ |
|-------|---|
| Admitted with payment | ☐ |
| Y1–Y4 each: block enlist + finalize | ☐ |
| **You encoded + admin approved grades every year** | ☐ |
| Forward balance reasonable after each close | ☐ |
| Y4 student `year_level = 4`, `ENROLLED` | ☐ |
| COR matches committed load each year | ☐ |

**Estimated time:** 2–3 hours manual (grading is the long pole).

---

## Part 3 — Track 2: Transferee completes (TTRNS)

**Goal:** Enter as **Y2 transferee**, TOR-credit prior work, irregular enroll each year through Y4, grade everything yourself.

### Setup

| Item | Value |
|------|--------|
| Applicant ref | `TTRNS-REF-001` |
| Admit | BSCPE **Year 2** through the external Admission / Cashier flow; use the issued student number in Registrar |
| Prefix | `TTRNS%` |

### Steps

#### 3.1 Confirm transferee flags on the existing student

After Admission/Cashier issues the student number, verify:

```sql
SET @sn = '<student_number>';
SELECT su.student_type, su.enrollment_status_type, su.year_level, sca.assignment_type
FROM sys_users su
JOIN student_curriculum_assignments sca ON sca.student_number = su.username AND sca.is_current = 1
WHERE su.username = @sn;
```

**Pass:** `Transferee`, `Irregular`, `year_level = 2`, `assignment_type = TRANSFEREE`.

#### 3.2 TOR crediting (Registrar Student Manager)

http://localhost:8083/registrar/admin/student-manager?username=`@sn`

**Single credit:** TOR & Transfer Crediting → pick a course → numeric grade e.g. `1.75` → Submit → approve in `Accreditation Requests`.

**Bulk CSV** (paste into bulk import):

```csv
course_code,numeric_grade,source_school,note
AECO 11,1.75,Prior College,TOR 2024
PE1 11,2.0,Prior College,
ANS1 11,1.50,Prior College,
```

Approve enough Y1-equivalent credit requests so Y2 irregular offerings are manageable (3–6 courses is enough for demo).

#### 3.3 Enroll each year (Y2 → Y4)

**Cashier:** http://localhost:8082/admin/cashier?keyword=`@sn`  
Mode: **Irregular** (not block — block sections should **reject** for irregular students).

| Year | SL | Section code | Finalize as |
|------|-----|--------------|-------------|
| 2 | `SL2024202522` | block mix-and-match | Irregular |
| 3 | `SL2024202532` | block mix-and-match | Irregular |
| 4 | `SL2024202542` | block mix-and-match | Irregular |

Per year: pay forward if needed → enlist subjects → pay ≥ ₱8,000 → finalize → **grade all (Part 1D)** → advance SL.

#### 3.4 Sign-off

| Check | ☐ |
|-------|---|
| Y2 transferee admit flags correct | ☐ |
| TOR single + bulk credit applied | ☐ |
| Irregular enlist (not block) each year | ☐ |
| Grades encoded + approved each year | ☐ |
| Y4 reached with sane ledger | ☐ |

**Estimated time:** 2–3 hours.

**Note:** There is no single automation script for the full transferee arc. Session E / Part 10 tests individual gates; this track **stitches** them into a lifecycle.

---

## Part 4 — Track 3: Y1 regular → shift at 2nd sem (TSHFT)

**Goal:** Start BSCPE Y1 **1st semester** regular, finalize, grade; move to **2nd sem**, **program shift** (e.g. to BSIT), continue irregular, optionally continue to later years.

### Setup

| Item | Value |
|------|--------|
| Applicant ref | `TSHFT-REF-001` |
| Start program | BSCPE Y1 |
| Shift target | **BSIT** (or BSCPE → BSIT — both have curriculum + sections) |
| Prefix | `TSHFT%` |

### Phase A — Y1 1st semester (regular BSCPE)

This doc pack assumes the active demo term is **1st sem** (`1120242025`). If your database is already on a later term, restore/bootstrap or set the student to the 1st sem SL before first enlist:

```sql
SET @sn = '<student_number>';
UPDATE sys_users SET term_year='SL2024202511', year_level=1, semester=1, admission_status='PENDING' WHERE username=@sn;
UPDATE students SET term_year='SL2024202511', year_level=1, semester=1, admission_status='PENDING' WHERE student_number=@sn;
```

**Cashier** → assign section **A** → **Enlist Entire Block** **`BSCPE-1-1-A`**  
*(If block missing, check `/admin/classes` or re-run bootstrap step 8.)*

Pay → **Finalize Regular** → **Grade all BSCPE Y1 S1 classes** (Part 1D).

### Phase B — Advance to 2nd sem

**Cashier** → Academic Term dropdown → **`SL2024202512`** → **Update Semester** (term close / forward).

### Phase C — Program shift (before S2 enrollment)

**Registrar → Student Manager** → http://localhost:8083/registrar/admin/student-manager?username=`@sn`

1. Scroll to **Program Shift**
2. Target program: **BSIT**
3. Target curriculum: active BSIT curriculum (default)
4. Year **1**, Semester **2**
5. Reason: `Demo shift at Y1 S2`
6. Save / submit shift

**Verify:**

- `program_code = BSIT`
- `student_type = Irregular`
- Carry-over panel shows **Carried over | Orphan passed | Required**
- Staged enlistments cleared

### Phase D — Y1 2nd semester (irregular BSIT)

**Cashier** → mode **Irregular** → enlist from seeded block sections that match the student's curriculum and semester.

Pay → **Finalize Irregular** → **Grade all** new BSIT subjects (Part 1D).

### Phase E — Optional continuation

Advance SL to Y2 (`SL2024202522`) and repeat irregular enroll + grade for BSIT Y2, or stop after shift demo.

### Sign-off

| Check | ☐ |
|-------|---|
| Y1 S1 BSCPE block finalized | ☐ |
| Grades approved for S1 | ☐ |
| Advanced to `SL2024202512` | ☐ |
| Program shift BSCPE → BSIT | ☐ |
| Carry-over panel sensible | ☐ |
| Y1 S2 irregular BSIT finalized | ☐ |
| Grades approved for post-shift load | ☐ |

**Estimated time:** 1–1.5 hours for shift story; +1–2 hours if continuing to Y4.

---

## Part 5 — Grading reference (Prof. Cruz)

### Where grading lives

| Step | URL |
|------|-----|
| Faculty login | http://localhost:8083/registrar/login |
| Class list | http://localhost:8083/registrar/grades |
| Grade sheet | http://localhost:8083/registrar/grades/view/{section_id} |
| Admin approvals | http://localhost:8083/registrar/admin/approvals |

Enrollment **faculty dashboard** (http://localhost:8082/faculty/dashboard) redirects to Registrar for encoding — use Registrar directly for this demo.

### Workflow order (important)

```
Finalize enrollment  →  DRAFT grade rows created automatically
       ↓
Prof encodes Prelim / Midterm / Finals
       ↓
Prof submits class
       ↓
Admin approves on /admin/approvals
       ↓
remarks = Passed (prereqs satisfied for next year)
       ↓
Advance SL / next enrollment
```

### Sample grades

| Style | Prelim | Midterm | Finals |
|-------|--------|---------|--------|
| Percentage | 85 | 88 | 90 |
| GWA (if UI accepts) | 2.0 | 1.75 | 1.50 |

Use consistent passing values. Failed grades block later prereqs.

### How many classes to grade?

After each **block** finalize, grade **every section** listed on `/grades` that contains your student (typically 8–12 courses per BSCPE sem). After **irregular** finalize, grade only the sections you enlisted.

### SQL: confirm grade status

```sql
SET @sn = '26-2-00005';
SELECT c.course_code, g.prelim, g.midterm, g.finals, g.remarks, g.status, g.registrar_final_remarks
FROM grades g
JOIN courses c ON c.course_id = g.course_id
WHERE g.student_id = @sn
ORDER BY c.course_code;
```

---

## Part 6 — Cleanup (after all demos)

Run in Workbench when done testing:

```sql
USE eacdb;

DELETE FROM student_enlistments WHERE student_id LIKE 'DREG%' OR student_id LIKE 'TTRNS%' OR student_id LIKE 'TSHFT%';
DELETE FROM student_ledger WHERE student_id LIKE 'DREG%' OR student_id LIKE 'TTRNS%' OR student_id LIKE 'TSHFT%';
DELETE FROM payments WHERE reference_number LIKE 'DREG%' OR reference_number LIKE 'TTRNS%' OR reference_number LIKE 'TSHFT%';
DELETE FROM grades WHERE student_id LIKE 'DREG%' OR student_id LIKE 'TTRNS%' OR student_id LIKE 'TSHFT%';
DELETE FROM student_curriculum_assignments WHERE student_number LIKE 'DREG%' OR student_number LIKE 'TTRNS%' OR student_number LIKE 'TSHFT%';
DELETE FROM students WHERE student_number LIKE 'DREG%' OR student_number LIKE 'TTRNS%' OR student_number LIKE 'TSHFT%';
DELETE FROM sys_users WHERE username LIKE 'DREG%' OR username LIKE 'TTRNS%' OR username LIKE 'TSHFT%';
DELETE FROM applicants WHERE reference_number LIKE 'DREG%' OR reference_number LIKE 'TTRNS%' OR reference_number LIKE 'TSHFT%';
DELETE FROM applicant_payments WHERE applicant_id LIKE 'DREG%' OR applicant_id LIKE 'TTRNS%' OR applicant_id LIKE 'TSHFT%';
```

To restore default faculty assignment (optional), re-run:

```text
registrar/db/seed_faculty_professors_and_grading.sql
```

---

## Part 7 — Troubleshooting

| Symptom | Fix |
|---------|-----|
| Prof. Cruz sees no classes | Run **Part 0A** SQL; confirm active term |
| Grade sheet empty for student | Finalize enrollment first; check `grades` table for DRAFT rows |
| Grading period CLOSED | Settings → FORCE_OPEN overrides; see Part 0B |
| Block enlist blocked (forward debt) | Walk-in pay forward until forward < ₱100 |
| Block enlist blocked (prereqs) | Complete **Part 1D** for prior year before advancing |
| Irregular student blocked on block code | Expected — use seeded block sections; special sections are the only exception |
| `BSCPE-1-1-A` missing | Re-run bootstrap or `seed_all_program_block_sections_calendar.sql` |
| Downpayment blocks finalize | Pay so credits ≥ downpayment (₱3,000 default; use ₱8,000 in demo) |
| Too many classes on `/grades` | Normal after Part 0A — open sections one-by-one; search student name on sheet |

---

## Part 8 — Quick URL card

| Action | URL |
|--------|-----|
| Cashier | http://localhost:8082/admin/cashier?keyword=`@sn` |
| Walk-in pay | http://localhost:8082/admin/walkin-payment?keyword=`@ref` or `@sn` |
| Admission | External Admission / Cashier flow (not Registrar canonical path) |
| Student Manager | http://localhost:8083/registrar/admin/student-manager?username=`@sn` |
| Prof. grades | http://localhost:8083/registrar/grades |
| Admin approvals | http://localhost:8083/registrar/admin/approvals |
| Finance policy | http://localhost:8083/registrar/admin/finance-policy |
| Class scheduling | http://localhost:8083/registrar/admin/class-scheduling?termId=1 |

---

## Related docs

| File | Purpose |
|------|---------|
| `START_HERE_NEW_PC_HANDOFF.md` | Machine setup |
| `MASTER_DEMO_UAT_MANUAL.md` | Full UAT matrix (Part 8 = Track 1 automation only) |
| `HANDOFF_UPDATES_20260609.md` | Latest code fixes |

**Automated shortcut (Track 1 only, no manual grading):**  
`python _runtime_logs/lifecycle_y1_y4_run.py` — use this for smoke tests, not for the graded demo in this manual.
