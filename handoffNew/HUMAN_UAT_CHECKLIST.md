# Human UAT & Demo Checklist

Last updated: 2026-06-17
**Print-friendly sign-off sheet** for panel demos and QA. Full reference: `MASTER_DEMO_UAT_MANUAL.md`.  
**Status:** `PROJECT_STATUS_AND_ROADMAP.md` — UAT in progress; user re-tested 0/A/B areas positively (2026-06-10). Dean / Faculty irregular new-enrollee advising in Registrar is dormant and not part of active sign-off.

---

## Active demo configuration

| Item | Value |
|------|--------|
| **Open term** | `1120242025` — A.Y. **2024–2025, 1st Semester** |
| **term_id** | `1` |
| **Fresh DB** | `registrar\setup\RUN_FRESH_SETUP.cmd` |
| **Registrar** | http://localhost:8083/registrar |
| **Enrollment** | http://localhost:8082 |

| Role | Login | Password |
|------|-------|----------|
| Admin / Cashier | `admin` | `1234` |
| Faculty (grading) | `prof.cruz` | `1234` |

**Test data rule:** Use new applicants or prefixes `DREG-*`, `TTRNS-*`, `TSHFT-*`, `HTEST-*`. Do not use production student records.

## Execution checkpoint - 2026-06-18

- Registrar focused regression: **33/33 PASS** for grading windows, term transition, grade outcomes, and term-fee behavior.
- Full registrar suite: **42 PASS, 1 skipped, 1 structural error**. The remaining error is the existing Spring Modulith package-cycle check; functional tests pass.
- Live Registrar: active-term readiness is **Ready** for `1120242025`; fee scopes `192`, missing/fallback/incomplete `0/0/0`.
- Enrollment `http://localhost:8082` was started and `admin` login passed. Session C remains pending for transactional sign-off: `WDRW-UAT-2026-001` loaded, but its active enrollment term is `SL_1120262026` while Registrar open term is `1120242025`, and no current assessment exists.
- Enrollment startup logged `Unknown column 'RESERVED' in 'WHERE'`; the application still started, but this schema warning needs separate Enrollment-side analysis before hard finance testing.
- Session D remains pending because the documented `prof.cruz` / `1234` account was rejected by the live database. Admin Grade Records loaded with no pending submissions.
- Session E remains pending for transactional sign-off. Student Profile, TOR crediting, curriculum assignment, and program-shift controls loaded successfully in read-only inspection.
- No term transition, payment, grade, TOR credit, or program shift was posted during this checkpoint.

---

## Sign-off summary

| Session | Focus | Tester | Date | Pass? |
|---------|--------|--------|------|-------|
| **0** | Fresh setup + smoke | | | ☐ |
| **A** | Registrar admin | | | ☐ |
| **B** | Enrollment / cashier | | | ☐ |
| **C** | Balance / term close | | | ☐ |
| **D** | Faculty / grading | | | ☐ |
| **E** | Transferee / program shift | | | ☐ |
| **F** | Term transition *(optional)* | | | ☐ |

**Lifecycle demos (longer):** `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`

---

# Session 0 — Fresh setup & smoke (~15 min)

## 0.1 Prerequisites (machine gate)

```cmd
registrar\setup\CHECK_PREREQUISITES.cmd
```

**Pass when:** all required checks PASS (JDK 17+, Maven, MariaDB, project files).  
**Agent doc:** `registrar/setup/AGENT_FRESH_SETUP.md`

## 0.2 Bootstrap database

```cmd
registrar\setup\RUN_FRESH_SETUP.cmd
```

**Pass when:** Command finishes with no `FAILED` lines; verify SQL shows `1120242025` active, fee gaps 0.

## 0.3 Start applications

```cmd
cd registrar && mvn -q spring-boot:run
cd enrollment3 && mvn -q spring-boot:run
```

## 0.4 Smoke checks

| # | Open | Do | Pass when |
|---|------|-----|-----------|
| 0.3a | http://localhost:8083/registrar/login | Login `admin` / `1234` | Dashboard loads |
| 0.3b | http://localhost:8082/login | Login `admin` / `1234` | Dashboard loads |
| 0.3c | `/admin/settings` | Read active term | **`1120242025`**, readiness **Ready** |
| 0.3d | `/admin/term-fees?termId=1` | Scan readiness card | **0** missing / incomplete scopes |
| 0.3e | `/admin/classes` | Filter BSCPE | Schedules show times (not all TBA) |
| 0.3f | `/admin/finance-policy` | Open page | Admission, downpayment, installments visible |

☐ Session 0 complete

---

# Session A — Registrar admin (~45 min)

## A1 — Settings readiness

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/settings |
| **Do** | Confirm active term **`1120242025`**; read readiness card |
| **Pass** | Status **Ready for operation** (no fee/curriculum blockers) |

☐ A1

---

## A2 — Finance policy

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/finance-policy |
| **Do** | Change admission min → Save → reload; change downpayment → Save |
| **Pass** | Values persist after reload |

☐ A2

---

## A2b — Program fees

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/term-fees?termId=1 |
| **Do** | Zone 1 + Zone 2 load; pick BSCPE Y1 S1 |
| **Pass** | No red blockers; tuition/lec rates show demo amounts |
| **CSV fallback** | `registrar/setup/fees/term-fee-import-template-1120242025.csv` |

☐ A2b

---

## A3 — Course catalog

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/courses |
| **Do** | Search `AECO` → open a course row |
| **Pass** | Detail loads without error |

☐ A3

---

## A4 — Curriculum builder

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/curriculum |
| **Do** | Open **BSCPE** or **BSIT** active curriculum |
| **Pass** | Course rows visible; builder loads |

☐ A4

---

## A5 — Student Manager

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/student-manager |
| **Do** | Search any seeded or demo student; view profile, ledger tab |
| **Pass** | Profile + ledger load |

☐ A5

---

## A5b — Per-student installment override *(optional)*

| | |
|---|---|
| **URL** | Student Manager → **Installment Plan Override** |
| **Do** | Save 4 custom rows → Enrollment cashier → verify split → **Clear override** |
| **Pass** | Cashier shows 4-way split, then reverts to term plan |

☐ A5b

---

## A6 — TOR & transfer crediting

| | |
|---|---|
| **URL** | Student Manager → **TOR & Transfer Crediting** |
| **Do** | Credit one course; paste bulk CSV below |
| **Pass** | `remarks = Passed`; deficiency count drops |

Bulk CSV sample:

```csv
course_code,numeric_grade,source_school,note
AECO 11,1.75,Prior College,TOR 2024
```

☐ A6

---

## A7 — Class scheduling

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/class-scheduling?termId=1 |
| **Do** | Search `BSIT` or `BSCPE`; expand block; **Add Slot** on a course |
| **Pass** | Schedule tags show day/time/room; `IRREG-A` visible under Course Sections |

☐ A7

---

## A8 — Print Registration Form

| | |
|---|---|
| **URL** | Student Manager → **Print Registration Form** (enrolled demo student) |
| **Pass** | Registration Form print view lists committed subjects |

☐ A8

---

## A9 - Legacy Admission Y1

| | |
|---|---|
| **URL** | External Admission/Cashier flow; registrar URL is no longer canonical for regular Y1 admission |
| **Do** | Regular applicants are admitted, pre-registered, sectioned, paid, enrolled, and assigned student numbers outside Registrar |
| **Pass** | Registrar is not required for regular Y1 admission; regular applicant intake remains external to Registrar |

Quick applicant SQL: see `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` Part 1A.

☐ A9

---

## A10 — External Y2+ transferee intake (informational)

| | |
|---|---|
| **Do** | Use a student already created by the external Admission / Cashier flow as **Year 2** BSCPE transferee, then verify Registrar reads it correctly |
| **Pass** | Existing student opens in Registrar with `student_type = Transferee`, `enrollment_status_type = Irregular`, curriculum assignment `TRANSFEREE` |

☐ A10

---

## A11 — Scholarship eligibility demo

Seed once if the demo rows are not present yet:

```sql
SOURCE registrar/handoffNew/sql_manual/08_scholarship_demo_seed.sql;
```

| | |
|---|---|
| **URL** | http://localhost:8083/registrar/admin/scholarships |
| **Do** | Confirm Academic Scholarship Policy shows **Minimum Completed Units = 27** |
| **Pass 1** | `SCH-UAT-ELIGIBLE` / Sofia Scholar shows **27** units and **Eligible** |
| **Pass 2** | `SCH-UAT-LOWUNITS` / Liam Low Units shows **24** units and **Not eligible** with reason `Needs at least 27 graded/taken unit(s)` |
| **Workflow** | Sofia: **Submit for Review** → confirm `PENDING` → **Approve** → confirm no discount is active yet → **Post** |
| **Pass 3** | Sofia shows `POSTED`, the scholarship is active, and **Revoke** is available |
| **Guardrail** | Liam has no submission action because the configured 27-unit requirement is not met |

☐ A11

---

# Session B — Enrollment / cashier (~45 min)

Use student from **A9** or search by number.

**Cashier URL:** http://localhost:8082/admin/cashier?keyword=`<student_number>`

| # | Step | Pass when |
|---|------|-----------|
| **B1** | Find student by number | Profile loads |
| **B2** | Assign section group **A** | Section group saved |
| **B3** | **Enlist Entire Block** `BSCPE-1-1-A` | Staged subjects listed |
| **B4** | Check assessment | Amount **> ₱0**, matches term fees |
| **B5** | Walk-in pay ≥ **₱8,000** | Payment posted |
| **B6** | **Finalize** Regular | `ENROLLED`, load **COMMITTED** |
| **B7** | Export / print COR | Matches enlisted courses |
| **B8** | Enrollment ledger | No staged-only rows after finalize |
| **B9** | Irregular: enlist **`IRREG-A`** OK; try block `BSIT-1-1-A` on irregular student → **rejected** | Policy enforced |
| **B10** | Drop a subject *(if UI available)* | Penalty/refund reasonable |

☐ Session B complete

---

# Session C — Balance / term close (~60 min)

Detail: `MASTER_DEMO_UAT_MANUAL.md` Part 9.

| ID | One-line pass criteria | ☐ |
|----|------------------------|---|
| BAL-T01 | Admission payment → student ID | ☐ |
| BAL-T02 | Forward debt blocks enlist | ☐ |
| BAL-T03 | Walk-in pays forward first | ☐ |
| BAL-T04 | Overpay → credit forward | ☐ |
| BAL-T05 | Block finalize → ENROLLED | ☐ |
| BAL-T06 | Drop penalty + capped refund | ☐ |
| BAL-T07 | Ledger forward matches SQL | ☐ |
| BAL-T08 | Y1→Y4 progression *(Part 8 / THREE_TRACK Track 1)* | ☐ |
| BAL-T09 | Payments ↔ ledger aligned | ☐ |

**Term close in UI:** Cashier → **Update Semester** dropdown → next SL code.

☐ Session C complete

---

# Session D — Faculty / grading (~30 min)

Bootstrap assigns all sections to **`prof.cruz`** (`setup/sql/03_assign_prof_cruz_demo.sql`).

| # | Step | URL / login | Pass when |
|---|------|-------------|-----------|
| **D1** | Faculty login | http://localhost:8083/registrar/login → `prof.cruz` | `/grades` lists classes |
| **D2** | Open grade sheet | `/grades` → **View** on BSCPE/BSIT section | Student row visible |
| **D3** | Encode | Enter Prelim / Midterm / Finals (e.g. 85, 88, 90) | Auto-save succeeds |
| **D4** | Submit class | **Submit to Registrar** | Class status SUBMITTED |
| **D5** | Admin approve | `admin` → http://localhost:8083/registrar/admin/approvals | Class approved; Passed |
| **D6** | Closed period *(optional)* | Settings → close Prelim → faculty blocked | Lock works |
| **D7** | INC expire *(optional)* | Settings → Expire INC | INC → Failed in prereqs |

☐ Session D complete

---

# Session E — Transferee / program shift (~30 min)

Detail: `MASTER_DEMO_UAT_MANUAL.md` Part 10 · Full story: `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` Tracks 2 & 3.

Current scope note: this session starts from an already-created student record. It does not include dormant Registrar Dean / Faculty new-enrollee advising.

| ID | Pass when | ☐ |
|----|-----------|---|
| TRANS-T01 | Existing Y2+ transferee student resolves as Transferee / Irregular / TRANSFEREE in Registrar | ☐ |
| TRANS-T02 | Single TOR credit | ☐ |
| TRANS-T03 | Bulk TOR CSV | ☐ |
| TRANS-T04 | Irregular offerings match assigned curriculum | ☐ |
| SHIFT-T01 | Program shift updates program + curriculum | ☐ |
| SHIFT-T02 | Carry-over panel (carried / orphan / required) | ☐ |
| SHIFT-T03 | Credit after shift → prereqs work | ☐ |

☐ Session E complete

---

# Session F — Term transition *(optional ~20 min)*

Run after A–E on **`1120242025`**. Pre-seed: fees for `2120242025` already loaded by bootstrap step 02.

| # | Step | Pass when |
|---|------|-----------|
| F1 | `/admin/settings` readiness for **`2120252026`** or next AY | Green *(or run `prep_future_ay_term.py`)* |
| F2 | Settings → transition to next term | Audit row; students advanced |
| F3 | One student: block → pay → finalize on new term | Golden path works |
| F4 | Document forward balance policy | Keep vs restore documented |

☐ Session F complete

---

# Recommended demo order (panel)

For a **single sitting** showing what works today:

| Order | What | Time |
|-------|------|------|
| 1 | **Session 0** smoke | 15 min |
| 2 | **A2 + A2b + A7** — finance + scheduling | 10 min |
| 3 | **A11** — scholarship eligibility without full grading-finalization buildout | 10 min |
| 4 | **A9 + B1–B7** — admit → enroll → pay → COR | 25 min |
| 5 | **D1–D5** — grade one class live | 15 min |
| 6 | **A10 + B9** or **E** — transferee follow-up without dormant dean advising | 15 min |
| 7 | *(Optional)* **THREE_TRACK** lifecycle | 2+ hr |

---

# Troubleshooting

| Problem | Fix |
|---------|-----|
| Bootstrap fails | MariaDB running; `mysql` on PATH |
| Readiness not green | Re-run `setup/sql/02_materialize_term_fees.sql` or upload CSV from `setup/fees/` |
| All sections TBA | Re-run `registrar/db/seed_all_class_schedules.sql` |
| prof.cruz empty class list | Re-run `setup/sql/03_assign_prof_cruz_demo.sql` |
| Finalize blocked | Pay ≥ downpayment; clear forward debt ≥ ₱100 |
| Wrong term in UI | Settings should show `1120242025`; re-run `setup/sql/01_activate_term_2425_s1.sql` |

---

# Quick URL card

| Action | URL |
|--------|-----|
| Fresh setup readme | `registrar/setup/README.md` |
| Settings | http://localhost:8083/registrar/admin/settings |
| Term fees (term 1) | http://localhost:8083/registrar/admin/term-fees?termId=1 |
| Finance policy | http://localhost:8083/registrar/admin/finance-policy |
| Class scheduling | http://localhost:8083/registrar/admin/class-scheduling?termId=1 |
| Student Manager | http://localhost:8083/registrar/admin/student-manager |
| Admission review (dormant registrar path) | http://localhost:8083/registrar/admin/admission-acceptance |
| Cashier | http://localhost:8082/admin/cashier |
| Walk-in pay | http://localhost:8082/admin/walkin-payment |
| Faculty grades | http://localhost:8083/registrar/grades |
| Grade approvals | http://localhost:8083/registrar/admin/approvals |
| Scholarships | http://localhost:8083/registrar/admin/scholarships |
