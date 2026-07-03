# 2026-07-02 Enrollment Status Type Mirror Fix

## Purpose

Record the shared-row normalization fix applied after the fresh post-fix rerun exposed one remaining mismatch:

- `sys_users.enrollment_status_type = Regular`
- `students.enrollment_status_type = NULL`

The flow itself still worked, but the mirror was incomplete.

## Canon Target

- Enrollment code patched:
  - `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

## Root Cause

Two paths were still skipping `enrollment_status_type` for the shared `students` row:

1. fresh student-number issuance inserted into `students` without the column
2. normal `sys_users -> students` synchronization updated academic standing fields but did not include `enrollment_status_type`

## Files Changed

- `src/main/java/com/example/enrollment/service/ApplicantStudentNumberService.java`
- `src/main/java/com/example/enrollment/service/StudentProfileService.java`

## Patch Applied

### 1. Fresh issuance insert now carries the field

`ApplicantStudentNumberService.issueNewStudentNumber(...)` now inserts:

- `enrollment_status_type`

into the shared `students` row during first issuance.

### 2. Shared profile sync now mirrors the field

`StudentProfileService.syncStudentRowFromSysUser(...)` now updates:

- `students.enrollment_status_type`

from the current `sys_users` student entity.

### 3. Fallback profile creation now carries the field too

`StudentProfileService.ensureProfileRow(...)` now includes:

- `enrollment_status_type`

when it creates a missing `students` profile row from `sys_users`.

### 4. Reverse mirror symmetry updated

`StudentProfileService.syncSysUserFromStudentRow(...)` now also mirrors:

- `u.enrollment_status_type = s.enrollment_status_type`

so future student-row-first corrections do not lose the field in the opposite direction.

## Validation Performed

Build:

- `mvn -q -DskipTests package` on the latest D Enrollment copy

Runtime:

1. restarted Enrollment on `8082`
2. opened live cashier walk-in for the fresh real student:
   - `24-1-00002`
3. rechecked shared DB state

Verified result:

- `sys_users.enrollment_status_type = Regular`
- `students.enrollment_status_type = Regular`

## Practical Impact

The fresh live student now has fully aligned enrollment-type metadata across both shared identity rows.

This closes the smaller normalization seam that remained after the larger walk-in `ENROLLED -> PENDING` mutation fix.
