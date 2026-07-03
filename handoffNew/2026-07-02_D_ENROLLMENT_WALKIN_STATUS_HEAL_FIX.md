# 2026-07-02 D Enrollment Walk-In Status Heal Fix

## Purpose

Record the fix applied to the latest D Enrollment canon after a real three-system demo exposed a cashier read path that could downgrade an already enrolled student back to `PENDING`.

## Canon Target

- Enrollment code patched:
  - `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

## Defect Observed

Using a real applicant created through Admission and paid through Enrollment:

- applicant reference: `24A00001`
- issued student number: `24-1-00001`

After cashier processing had already:

- issued the student number
- created committed enlistments
- marked the applicant row `ENROLLED`

the subsequent walk-in load path could still push the same student back to:

- `sys_users.admission_status = PENDING`
- `students.admission_status = PENDING`

That drift then propagated into Registrar, which showed:

- header state `PENDING`
- profile state `PENDING`

even while the admission snapshot and committed subject load already proved the student was officially enrolled.

## Root Cause

Latest D Enrollment walk-in load still contained repair-on-view status logic in:

- `src/main/java/com/example/enrollment/controller/AdminController.java`

The page could:

1. compute `officiallyFinalized = false`
2. set the in-memory `Student.applicantStatus` back to `PENDING`
3. call `financialService.onTransitionToPending(...)`
4. persist that mutation through `sys_users`
5. later leave the student timeout-blocked as well

That created a second-order side effect:

- Registrar current load became hidden because registrar treats blocked students as ineligible for active-load reads.

## Fix Applied

### 1. Preserve canonical enrolled status on walk-in load

Added a cashier-load guard in:

- `AdminController.loadStudentForWalkin(...)`

Behavior now:

- if the walk-in page can prove the student is canonically enrolled, it must not downgrade the student back to `PENDING`

The guard treats the student as canonically enrolled when either:

- `enrollmentFinalized` is already true, or
- the student is already `ENROLLED`, or
- the linked applicant row is `ENROLLED` **and** current-term committed enlistments exist

### 2. Heal stale rows created by the old bug

When the guard detects a canonically enrolled student but finds drifted status:

- it restores `sys_users.admission_status = ENROLLED`
- it keeps shared canonical status aligned

### 3. Clear stale timeout block on healed enrolled students

The old mutation could leave:

- `enrollment_blocked = 1`

even after the student was returned to `ENROLLED`.

The heal path now also:

- clears the blocked flag in `sys_users`
- syncs the `students` profile row from `sys_users`

This matters because Registrar reads `students.enrollment_blocked` and would otherwise keep hiding the current load.

## Validation Performed

### Before final heal

Observed drift:

- `sys_users.admission_status = PENDING`
- `students.admission_status = PENDING`
- `students.enrollment_blocked = 1`
- Registrar profile showed `PENDING`

### After first patch

Opening:

- `http://localhost:8082/admin/walkin-payment?keyword=24-1-00001`

healed:

- `sys_users.admission_status = ENROLLED`
- `students.admission_status = ENROLLED`

but the blocked flag still remained on the `students` row.

### After second patch

Reopening the same walk-in page healed all relevant shared state:

- `sys_users.admission_status = ENROLLED`
- `students.admission_status = ENROLLED`
- `sys_users.enrollment_blocked = 0`
- `students.enrollment_blocked = 0`

Registrar then reloaded correctly and showed:

- header state `ENROLLED`
- profile state `ENROLLED`
- current load `42 units`
- current enrolled subjects visible again

## Practical Impact

This closes the specific defect where merely viewing a student in the latest D Enrollment cashier/walk-in surface could corrupt the official cross-system status and indirectly break Registrar presentation.

## Next Recommended Step

Run one more fresh real applicant flow after this patch:

1. create a brand-new applicant in Admission
2. qualify and finalize program in Admission
3. pay downpayment in Enrollment
4. open the issued student in Enrollment walk-in
5. open the same student in Registrar

Expected result:

- no downgrade to `PENDING`
- no stale timeout block
- Registrar immediately shows `ENROLLED` with current load intact
