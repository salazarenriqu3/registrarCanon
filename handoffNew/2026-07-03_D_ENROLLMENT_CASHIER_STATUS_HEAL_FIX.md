# 2026-07-03 D Enrollment Cashier Status Heal Fix

## Scope

This note records the latest Enrollment-side realignment patch applied against the new D canon copy:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

Registrar code was not changed in this pass. The goal was to stop the Enrollment cashier from mutating a legitimately enrolled student back to `PENDING` just by opening the cashier/ledger record.

## Bug Confirmed

Student used for live verification:

- student number: `24-1-00002`
- applicant reference: `24A00002`

Observed defect before patch:

1. Student already had committed enlistments and live ledger data.
2. Visiting Enrollment cashier/ledger for `24-1-00002` mutated shared identity rows from `ENROLLED` to `PENDING`.
3. Registrar then read the downgraded status even though the student still had active academic/financial state.

Canonical evidence captured before fix:

- `students.student_number='24-1-00002'` became `PENDING`
- `sys_users.username='24-1-00002'` became `PENDING`
- `applicants.reference_number='24A00002'` remained `ENROLLED`
- `student_enlistments` still held committed rows for the same student

This confirmed a real Enrollment-side mutation bug, not a Registrar display problem.

## Root Cause

The latest D Enrollment cashier flow still called `ensurePreRegEnrollmentFinalized(...)` while rendering the cashier page, but unlike the walk-in flow it did not preserve or heal the canonical enrolled state first.

That allowed cashier page access to downgrade a student even when the student already had enough evidence to remain treated as enrolled.

## File Patched

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3\src\main\java\com\example\enrollment\controller\AdminController.java`

## Patch Summary

### 1. Cashier preserve/heal gate added

Inside `ensurePreRegEnrollmentFinalized(Student s)`:

- calculate `officiallyFinalized` once using `financialService.isOfficialEnrollmentFinalized(s)`
- before downgrade logic, check whether the student must remain enrolled on cashier load
- if yes, heal the canonical status and return early

### 2. Preserve helper strengthened

The helper previously tied to walk-in behavior was renamed and hardened so cashier load now preserves `ENROLLED` only when there is real backing data:

- official finalization, or
- applicant/current identity says `ENROLLED` **and**
- the student has committed current-term enlistments

### 3. Canonical heal helper added

`healCanonicalEnrolledStatus(Student student)` now:

1. sets the live student row back to `ENROLLED` if needed
2. unblocks if necessary
3. saves the student
4. syncs canonical enrollment status
5. mirrors the state back into the student profile row

### 4. Current-term enlistment check fixed

Committed enlistment detection now uses the term resolution service instead of the brittle `student.termYear` parser:

- use `termContextService.resolveTermIdForStudent(student)`

This matters because the newer Enrollment copies use term labels like `SL_1120242025`, and string parsing was not reliable enough to anchor status governance.

## Validation Performed

### Build

Ran successfully:

- `mvn -q -DskipTests package`

Workdir:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

### Live runtime

Apps running during test:

- Admission: `http://localhost:8081`
- Enrollment: `http://localhost:8082`
- Registrar: `http://localhost:8083`

### Before cashier open

`24-1-00002` was still in the bad pre-test state:

- `students.admission_status = PENDING`
- `sys_users.admission_status = PENDING`
- applicant remained `ENROLLED`

### After opening patched cashier page

Visited:

- `http://localhost:8082/admin/cashier?keyword=24-1-00002`

Re-check after page load:

- `students.admission_status = ENROLLED`
- `sys_users.admission_status = ENROLLED`
- `applicants.applicant_status = ENROLLED`

Additional confirmation:

- `student_enlistments` for `24-1-00002` still contained `16` `COMMITTED` rows
- Registrar student profile for `24-1-00002` rendered as `ENROLLED`

## Important Clarification

The cashier page still visibly showed:

- `PENDING PAYMENT`

This did **not** indicate another status mutation. It is a payment-state label inside the cashier surface, not a downgrade of the student's canonical enrollment identity.

So after this fix:

- payment state may still be pending
- shared student identity no longer regresses to `PENDING` merely from cashier access

## Current Outcome

This pass closes the specific latest-D Enrollment cashier mutation path that was breaking three-system testing.

Registrar can now continue testing against the latest D Enrollment copy with much safer assumptions about:

- student identity stability
- shared status reads
- profile state coherence after cashier/ledger access

## Remaining Watchpoints

The broader three-system realignment still needs continued testing on:

1. cashier actions that do more than read, especially actual payment posting
2. withdrawal penalty movement into ledger
3. withdrawn-student hard locks across all Enrollment entry points
4. any additional latest-D cashier or ledger pages that may still contain older downgrade logic

But the plain page-open mutation that was corrupting `24-1-00002` is now fixed in the patched D Enrollment copy.
