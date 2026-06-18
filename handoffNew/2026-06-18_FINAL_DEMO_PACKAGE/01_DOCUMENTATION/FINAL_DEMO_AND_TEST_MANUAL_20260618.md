# Registrar - Final Demo and Test Manual

Date: 2026-06-18  
Demo term: `1120242025` (A.Y. 2024-2025, 1st semester, `term_id = 1`)

## 1. Scope

This manual supports:

- a 60-90 minute Registrar-centered demonstration
- repeatable manual UAT
- cross-application checks with Enrollment/Cashier
- a go/no-go record for the controlled demo

Do not include Registrar irregular new-enrollee advising or pre-registration. That feature is retired from scope.

## 2. Safety rule

`02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd` drops and recreates `eacdb`. Run it only on a disposable demo database after confirming no live data is present.

For an existing prepared database, skip the destructive bootstrap and begin at Section 5.

## 3. Demo student dataset

The consolidated demo uses these prepared student fixtures from `03_TEST_DATA`:

- `2026-1001` / Maria Reyes Santos: primary enrolled student for profile, current subjects, academic history, registration form, ledger, TOR, and withdrawal checks
- `SCH-UAT-ELIGIBLE` / Sofia Scholar: scholarship approval and posting positive case
- `SCH-UAT-LOWUNITS` / Liam Low Units: scholarship ineligible comparison case

Run `03_TEST_DATA\02_scholarship_demo_seed.sql` before scholarship review tests, and use `03_TEST_DATA\04_registrar_student_dataset_verify.sql` to confirm the dataset is aligned before the demo.

## 4. Prerequisites

| Requirement | Expected value |
|---|---|
| Workspace | Folder containing `registrar` and `enrollment3` |
| JDK | 17+; Registrar targets Java 17 |
| Maven | 3.6+ or the supplied Enrollment wrapper |
| MySQL/MariaDB | Listening on `127.0.0.1:3306` |
| Database | `eacdb` |
| Demo DB user | `root`, empty password in current local configuration |
| Registrar URL | `http://localhost:8083/registrar` |
| Enrollment URL | `http://localhost:8082` |

Default demo accounts:

| Account | Password | Purpose |
|---|---|---|
| `admin` | `1234` | Registrar and Enrollment administration |
| `cashier` | `1234` | Enrollment/Cashier |
| `prof.cruz` | `1234` | Faculty grading |

These credentials are demo-only.

## 5. Fresh demo setup

From the workspace root:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\01_CHECK_MACHINE.cmd
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd
```

The second command is destructive. It creates the schema, curricula, terms, fees, sections, schedules, faculty data, and active term. Its final verification should report:

- current term `1120242025`
- zero active-term fee gaps
- block offerings greater than zero
- faculty assignments greater than zero
- schedules available for active-term sections

If a step fails, use `02_FRESH_DATABASE\README.md` and `05_MANIFEST\SOURCE_FILE_MAP.md` to identify it. Do not apply random legacy SQL files.

## 5. Build gate

Run the package build command from the workspace root:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\02_BUILD_ALL.cmd
```

Expected artifacts:

- `C:\newer\new\registrar\target\registrar-0.0.1-SNAPSHOT.war`
- `C:\newer\new\enrollment3\target\enrollment.war`

Optional functional regression command:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\03_RUN_REGISTRAR_TESTS.cmd
```

Known result on 2026-06-18: 42 pass, 1 skip, and the Modulith architecture test reports package cycles. Record this as a known structural exception; do not describe the complete suite as green.

## 6. Start and smoke gate

Start Registrar:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\04_START_REGISTRAR.cmd
```

Start Enrollment in another terminal:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\05_START_ENROLLMENT.cmd
```

Before presenting, verify:

| Check | Pass condition |
|---|---|
| Registrar login | `admin / 1234` opens the dashboard without returning to login |
| Enrollment login | `admin / 1234` opens Enrollment administration |
| Active term | Registrar shows `1120242025` |
| Program Fees | `/registrar/admin/term-fees?termId=1` has no demo blocker |
| Classes | `/registrar/admin/class-scheduling?termId=1` displays block and course sections |
| Schedules | Active sections display day/time; room may be TBA |
| Student Profile | `/registrar/admin/student-manager` renders search and profile actions |
| Enrollment Cashier | `/admin/cashier` loads without a fatal schema error |

Stop if the active term is wrong, exact term fees are absent, login loops, or the apps point at different databases.

## 7. Recommended demo story

### Demo 1 - Academic master data chain (15 minutes)

1. Open Program Builder and show an active program.
2. Open Course Builder and show catalog course code, title, and units.
3. Open Curriculum Builder and show courses mapped by year and semester.
4. Use **New Curriculum** to create an inactive editable draft for a selected program and academic year; the system opens the draft editor directly.
5. Open Class Scheduling for term 1.
6. Filter blocks by program, year, semester, status, or schedule completeness.
7. Load Course Details, then apply server filters for department, section count/status, faculty, schedule state, day, or room. Use course search for the fastest targeted lookup on large datasets.
8. Show a block section generated from curriculum data.
9. Open a schedule slot and show day, time, faculty, and room/TBA behavior.
10. On a different section in the same term, try an overlapping slot using the same assigned room. Confirm the save is rejected and names the conflicting section.
11. Try assigning a faculty member whose existing class overlaps one of the section's slots. Confirm the assignment is rejected.
12. Repeat the slot save with **Assign Room Later**. Confirm TBA remains allowed when the faculty has no time conflict, then remove the temporary slot.
10. Explain that Enrollment consumes these sections; it does not own the curriculum.

Pass when the same program/course/curriculum records can be traced into active-term class sections and schedule slots.

### Demo 2 - Student Profile and records (15 minutes)

1. Open `/registrar/admin/student-manager`.
2. Search an existing enrolled student by student number.
3. Open the profile and show Registrar Editable Profile.
4. Show current enrolled subjects and official units.
5. Show current curriculum assignment and deficiency indicators.
6. Show academic history and financial status as read-only context.
7. Open Registration Form history or print the Registration Form.
8. Show TOR/transfer crediting and Program Shift controls, without changing the demo student unless the test case is disposable.

Pass when identity, committed load, curriculum, alerts, and document history agree for the same student.

### Demo 3 - Withdrawal workflow (10 minutes)

Use a disposable student with a committed active-term subject.

1. From Student Profile, submit a withdrawal request with a reason.
2. Open `/registrar/faculty/withdrawals` for Dean review.
3. Approve or reject the Dean stage according to the test case.
4. Open `/registrar/admin/withdrawals` and complete Registrar action.
5. Open `/registrar/admin/withdrawals/report`.
6. Verify the event in `/registrar/admin/document-trail`.

Pass when status transitions, actor/action history, report, and student load reflect one consistent outcome. Do not use the stale `SL_1120262026` fixture for the active term.

### Demo 4 - Scholarship review and posting (15 minutes)

If the candidates are missing, run `04_RUNNERS\07_LOAD_SCHOLARSHIP_TEST_DATA.cmd` against the disposable demo database.

1. Open `/registrar/admin/scholarships`.
2. Confirm policy minimum is 27 completed units.
3. Evaluate `SCH-UAT-ELIGIBLE` / Sofia Scholar: expected 27 units and eligible.
4. Evaluate `SCH-UAT-LOWUNITS` / Liam Low Units: expected 24 units and ineligible.
5. Submit Sofia for review: expected `PENDING`.
6. Approve Sofia: expected `APPROVED`, with no active discount yet.
7. Post Sofia: expected `POSTED`, scholarship now available to finance consumption.
8. Demonstrate Revoke only if reset/cleanup is planned.

Pass when completed units drive eligibility and the financial flag activates only after posting.

### Demo 5 - Faculty grading (10 minutes)

1. Sign out and sign in as `prof.cruz / 1234`.
2. Open assigned classes/grade sheet.
3. Select an active-term section containing committed students.
4. Encode a disposable test grade or show existing demo grades.
5. Save and confirm the value persists after reload.
6. Return as admin and open `/registrar/admin/approvals`.
7. Show grade-change and grading-window controls.

Pass when the faculty sees assigned classes, committed students only, and saved grades appear in the Registrar review surface. Full grade finalization policy is deferred.

### Demo 6 - Enrollment/Cashier bridge (15 minutes)

1. In Registrar, note the active term, block section, schedule, and exact program fee scope.
2. In Enrollment, search a disposable student.
3. Stage the correct block subjects.
4. Confirm staged rows do not count as official Registrar enrollment.
5. Post the required demo payment.
6. Finalize enrollment.
7. Confirm rows become `COMMITTED` and the student becomes enrolled.
8. Return to Registrar and verify class count, roster, Student Profile load, and Registration Form.

Pass when Enrollment uses Registrar-owned academic structures and committed membership appears consistently in both applications.

If Enrollment logs `Unknown column 'RESERVED'`, stop hard finance/term-close acceptance and record it as an Enrollment schema blocker.

## 8. Full UAT checklist

Mark each row Pass, Fail, Blocked, or Not Run and attach a screenshot/student number where useful.

### A. Configuration and builders

| ID | Test | Expected |
|---|---|---|
| A01 | Active term resolution | All Registrar pages use `1120242025` |
| A02 | Program to curriculum | Curriculum belongs to the selected program/version |
| A03 | Course reuse | Curriculum references the canonical course record |
| A04 | Curriculum to block | Block subjects match year/semester curriculum |
| A05 | Schedule slot | Day/time/faculty persist; room may remain TBA |
| A06 | Close section | Closed section rejects new operational use |
| A07 | Capacity count | Only committed enlistments count as enrolled |
| A08 | Exact fees | Active scope reads exact term fee rows |
| A09 | New curriculum | Creates an inactive draft and opens its editor; it does not replace the active version |
| A10 | Scheduling filters | Applied filter values remain selected and only matching courses/sections are shown |

### B. Student records

| ID | Test | Expected |
|---|---|---|
| B01 | Student search | Search by student number returns one identity |
| B02 | Profile update | Registrar-owned fields persist |
| B03 | Current load | Committed courses and units are correct |
| B04 | Curriculum assignment | Current curriculum is explicit and visible |
| B05 | Program shift | New assignment and credited/carry-over subjects are auditable |
| B06 | Transfer credit | Credited course and source details appear in history |
| B07 | Registration Form | Printed data matches committed load and term |
| B08 | Document trail | Relevant actions appear with actor/time |

### C. Scholarship

| ID | Test | Expected |
|---|---|---|
| C01 | Eligible candidate | Sofia: 27 completed units, eligible |
| C02 | Ineligible candidate | Liam: 24 completed units, blocked with reason |
| C03 | Review submission | Status becomes `PENDING` |
| C04 | Approval | Status becomes `APPROVED`; no discount activation |
| C05 | Posting | Status becomes `POSTED`; finance flag activates |
| C06 | Rejection/revocation | Audit state persists and finance flag is inactive |

### D. Withdrawal and grading

| ID | Test | Expected |
|---|---|---|
| D01 | Withdrawal submission | Reason and active subject persist |
| D02 | Dean review | Status and reviewer are recorded |
| D03 | Registrar action | Final action updates report/trail |
| D04 | Faculty login | `prof.cruz` does not loop to login |
| D05 | Faculty roster | Assigned section shows committed students only |
| D06 | Grade save/reload | Grade persists |
| D07 | Approval surface | Admin sees applicable grade submissions/changes |

### E. Cross-application lifecycle

| ID | Test | Expected |
|---|---|---|
| E01 | Same active term | Registrar and Enrollment resolve the same term |
| E02 | Stage block | Staged load is visible to cashier but unofficial |
| E03 | Assessment | Uses exact active-term fee scope |
| E04 | Payment | Ledger records the transaction once |
| E05 | Finalize | Student status and rows become official/committed |
| E06 | Registrar reflection | Profile, roster, section count, form all agree |
| E07 | Repeated action | No duplicate student or duplicate committed load |

### F. Release regression

| ID | Test | Expected |
|---|---|---|
| F01 | Registrar package | WAR builds |
| F02 | Enrollment package | WAR builds |
| F03 | Registrar functional tests | No functional failures |
| F04 | Modulith architecture | Known cycle exception recorded or resolved |
| F05 | Browser smoke | Required pages render without 500/login loop |
| F06 | Database warning review | No unexplained schema warning affects tested flow |

## 9. Demo sign-off record

```text
Date/time:
Machine/database:
Active term:
Presenter:
Tester/approver:

Build gate: PASS / FAIL
Smoke gate: PASS / FAIL
Academic builders: PASS / FAIL / BLOCKED
Student records: PASS / FAIL / BLOCKED
Scholarship: PASS / FAIL / BLOCKED
Withdrawal/grading: PASS / FAIL / BLOCKED
Enrollment bridge: PASS / FAIL / BLOCKED

Known exceptions accepted:
Evidence paths/screenshots:
Final decision: DEMO GO / DEMO NO-GO / PRODUCTION NO-GO
```

## 10. Cleanup

- Do not run fresh setup against a database that must be preserved.
- Remove or reset disposable grades, withdrawals, payments, and scholarship posting after the presentation if the same dataset will be reused.
- Keep the active term unchanged unless term-transition testing is the stated purpose.
- Record all failures against the exact student number, term, URL, timestamp, and action.

For long-form balance, transferee, shift, and term-transition cases, use the older specialized manuals only as supplements. The scope and readiness statements in this final manual remain authoritative.
