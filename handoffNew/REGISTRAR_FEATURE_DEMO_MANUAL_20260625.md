# Registrar Feature Demo Manual

Date: 2026-06-25  
Workspace: `D:\registrarCanon_canon`  
Registrar URL: `http://localhost:8083/registrar`

This is the current front-door demo script for a Registrar-first presentation. It assumes a disposable demo database and uses a curated feature dataset, not ad hoc live edits.

## 1. Run order

1. `setup\RUN_FRESH_SETUP.cmd`
2. `setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
3. `setup\START_REGISTRAR_DEMO.cmd`
4. Sign in as `admin / 1234`

If you start Registrar manually instead of `setup\START_REGISTRAR_DEMO.cmd`, set:

```cmd
set APP_UPLOAD_DIR=D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\admission_uploads
```

The loader is repeatable. Re-run it whenever the demo students get mutated.

## 2. Demo accounts

| Account | Password | Use |
|---|---|---|
| `admin` | `1234` | Registrar admin demo |
| `prof.cruz` | `1234` | Faculty grading demo |

## 3. Demo students

| Student | Use |
|---|---|
| `2026-1001` | Baseline profile, admission snapshot, applicant document viewing, registration form printing, reg-form history, document trail |
| `ADDCLS-2026-001` | Add-subject demo from Student Profile using open `IRREG-A` sections |
| `TTRNS-2026-001` | TOR/transfer-credit demo and academic-history print demo |
| `TSHFT-2026-001` | Program-shift demo |
| `OVRPAY-2026-001` | Overpayment disposition demo |
| `SPRINT-DEMO-2026-001` | Registrar withdrawal demo |
| `SCH-UAT-ELIGIBLE` | Scholarship positive case |
| `SCH-UAT-LOWUNITS` | Scholarship negative case |

## 4. Pre-demo gate

Open these first:

1. `/admin/settings`
2. `/admin/class-scheduling?termId=1`
3. `/admin/student-manager?username=2026-1001`
4. `/admin/withdrawals`
5. `/admin/scholarships`

Pass when:

- active term is `1120242025`
- `BSIT-1-1-A` and `BSCPE-1-1-A` show real rooms, not TBA
- Maria shows an Admission Snapshot and Applicant Documents card
- `prof.cruz` can still be used later for grading

## 5. Presentation order

Use this order. It keeps the least destructive pages first and the heavily mutating pages later.

### Part A. Academic chain and scheduling

1. Open `/admin/class-scheduling?termId=1`.
2. Filter to `BSIT`.
3. Expand `BSIT-1-1-A`.
4. Point out the seeded roomed schedule data for:
   - `CC101`
   - `CC102`
   - `GE101`
   - `GE102`
   - `PE101`
   - `NSTP101`
5. Switch to `BSCPE-1-1-A`.
6. Point out that the demo blocks now use actual rooms as well.
7. State the hardening rules:
   - room conflicts blocked
   - faculty conflicts blocked
   - same-section overlaps blocked
   - faculty max-load enforced
8. If the warning banner is visible, show **Repair Current-Term Conflicts** and explain that it normalizes the term by clearing conflicted room stamps to TBA, deleting overlapping extra slots inside one section, and unassigning faculty from overlapping sections.

Expected result:

- the scheduling page shows concrete room assignments for the blocks used in the live demo
- the story is no longer dependent on TBA slots

### Part B. Student profile, admission bridge, and documents

Student: `2026-1001`

1. Open `/admin/student-manager?username=2026-1001`.
2. Show the top identity card and current load.
3. Show the Admission Snapshot card.
4. Show the Applicant Documents card.
5. Click `View` on:
   - Form 138
   - Good Moral
   - PSA Birth Certificate
   - ID Picture
6. Expand `Archive & Custody Tracking`.
7. Record a `REQUESTED` event with purpose `TOR generation`.
8. Record a `RELEASED` event with counterpart `evaluator.one`.
9. Return to Student Profile.
10. Open `/admin/document-trail?query=2026-1001`.
11. Show the new admission-document view events, archive custody events, and the older admission application logs for `DEMO-SANTOS-001`.

Expected result:

- Maria is visibly tied back to a passed admission record
- the uploaded files open inline
- document viewing creates registrar-side trail entries
- physical-file custody movements are stored and visible in Document Trail

### Part C. Registration form, history, COG, and TOR printing

Students:

- `2026-1001` for Registration Form
- `TTRNS-2026-001` for COG and TOR

1. From Maria’s profile, click `Print Registration Form`.
2. Confirm the print page lists her committed current load.
3. Open `/admin/document-trail?query=2026-1001`.
4. Show the `REGISTRATION_FORM / PRINTED` event.
5. Open `/admin/reg-form-history?studentNumber=2026-1001`.
6. Show the seeded baseline enrollment/curriculum events.
7. Open `/admin/student-manager?username=TTRNS-2026-001`.
8. Print COG.
9. Print TOR.

Expected result:

- registration-form printing is visible and auditable
- reg-form history is not empty
- COG/TOR printing has actual academic-history rows to show

### Part D. Add class from Student Profile

Student: `ADDCLS-2026-001`

1. Open `/admin/student-manager?username=ADDCLS-2026-001`.
2. Scroll to `Add Subjects`.
3. Use the local filter and look for `GE102` or `GE101`.
4. In the section dropdown, choose the `IRREG-A` option with the seeded schedule.
5. Click `Add`.
6. Reload the profile if needed.
7. Open `/admin/reg-form-history?studentNumber=ADDCLS-2026-001`.
8. Open `/admin/document-trail?query=ADDCLS-2026-001`.

Expected result:

- one additional subject is committed successfully
- a `SUBJECT_ADD` reg-form event exists
- a document-trail enrollment event exists

### Part E. TOR / transfer crediting

Student: `TTRNS-2026-001`

1. Open `/admin/student-manager?username=TTRNS-2026-001`.
2. Scroll to `TOR & Transfer Crediting`.
3. In the bulk CSV box, paste:

```csv
course_code,numeric_grade,source_school,note
GE102,1.75,Legacy College,TOR Batch 1
PE101,1.50,Legacy College,TOR Batch 1
```

4. Click `Submit bulk request`.
5. In `Accreditation Requests`, approve the pending rows.
6. Scroll to academic history and deficiency areas.
7. Open `/admin/reg-form-history?studentNumber=TTRNS-2026-001`.
8. Open `/admin/document-trail?query=TTRNS-2026-001`.

Expected result:

- the submitted requests appear first as pending, then as approved after registrar action
- the approved credits appear in history
- deficiency pressure is reduced
- transfer-credit request, approval, and posting events are recorded

### Part F. Program shift

Student: `TSHFT-2026-001`

1. Open `/admin/student-manager?username=TSHFT-2026-001`.
2. Scroll to the program/curriculum controls.
3. Shift from `BSCPE` to `BSIT`.
4. Use a reason like `Registrar feature demo shift`.
5. Save the shift.
6. Reload the profile.
7. Open `/admin/reg-form-history?studentNumber=TSHFT-2026-001`.
8. Open `/admin/document-trail?query=TSHFT-2026-001`.

Expected result:

- program code changes
- the action is auditable
- the page continues to show coherent curriculum context after the shift
- if the student had a current-term load, the load is cleared for the shift without marking the student withdrawn from school
- document trail shows `SHIFT_LOAD_CLEARED` separately from full withdrawal

### Part G. Withdrawal workflow

Student: `SPRINT-DEMO-2026-001`

1. Open `/admin/student-manager?username=SPRINT-DEMO-2026-001`.
2. In the withdrawal area, execute one single-subject withdrawal immediately.
3. Return to the profile and confirm the load changed at once.
4. Open `/admin/withdrawals` to review the archive.
5. Open `/admin/withdrawals/report`.
6. Open `/admin/document-trail?query=SPRINT-DEMO-2026-001`.

Optional second pass:

1. Re-run `setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd` to reset.
2. Execute a full-student withdrawal instead.

Expected result:

- the registrar executes the withdrawal directly
- the action is archived immediately
- report and trail pages show the same outcome
- single-subject withdrawal can remove the final current-term subject without changing the student to school-withdrawn status
- full-student withdrawal remains the intentional school-exit action and still marks the student withdrawn/inactive

### Part H. Scholarship workflow

Students:

- `SCH-UAT-ELIGIBLE`
- `SCH-UAT-LOWUNITS`

1. Open `/admin/scholarships`.
2. Confirm the screen is academic-only and no manual scholarship type catalog is visible.
3. Confirm GWA and period-grade caps are configurable, while the unit requirement is shown as taken units versus assigned curriculum load.
4. Search or inspect `SCH-UAT-ELIGIBLE`.
5. Confirm the student is eligible.
6. Submit, approve, and post.
7. Inspect `SCH-UAT-LOWUNITS`.
8. Confirm the rejection reason is the missing assigned-curriculum unit load.
9. If testing a 3rd/4th-year PE/NSTP row, confirm the student is blocked even with passing/high grades.

Expected result:

- Sofia progresses through `PENDING -> APPROVED -> POSTED`
- Liam remains blocked by curriculum-required units
- late PE/NSTP remains a hard academic-scholarship disqualifier for 3rd/4th year
- finance effect begins only after `POSTED`

### Part I. Faculty grading

Account: `prof.cruz / 1234`

1. Sign out of admin.
2. Sign in as `prof.cruz`.
3. Open the grading page.
4. Use one of the sections that now has seeded committed demo students from the BSIT block.
5. Save one disposable grade.
6. Sign out and return as admin.
7. Open the approvals/grade review surfaces.

Expected result:

- Cruz sees real assigned classes with demo students
- the grade save persists
- the admin review surface can observe the change

### Part J. Optional overpayment disposition

Student: `OVRPAY-2026-001`

1. Open `/admin/student-manager?username=OVRPAY-2026-001`.
2. Go to the finance/ledger area.
3. Demonstrate that the student has an overpayment.
4. Apply a credit, refund, or split disposition.
5. Open `/admin/reg-form-history?studentNumber=OVRPAY-2026-001`.
6. Open `/admin/document-trail?query=OVRPAY-2026-001`.

Expected result:

- the overpayment action is recorded and visible in the audit surfaces

## 6. Mutability rules

Keep these students clean unless you are in their matching section:

- Do not withdraw `2026-1001`
- Do not shift `2026-1001`
- Do not use `SPRINT-DEMO-2026-001` for anything except withdrawal
- Do not use `SCH-UAT-*` for unrelated academic mutations

If a session goes sideways, the fastest reset is:

1. stop the app
2. re-run `setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
3. start again with `setup\START_REGISTRAR_DEMO.cmd`

## 7. Success checklist

- scheduling shows real rooms on the named demo blocks
- Maria shows admission docs and printable current load
- add-class works on a non-block open section
- reg-form history is populated
- document trail shows admissions, print, enrollment, and withdrawal activity
- transfer credit and program shift both have dedicated disposable students
- withdrawal is formal and auditable
- scholarship has both positive and negative cases
- faculty grading still works with current assigned sections
