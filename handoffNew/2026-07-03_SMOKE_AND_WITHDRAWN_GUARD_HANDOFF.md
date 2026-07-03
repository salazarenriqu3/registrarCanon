# 2026-07-03 Smoke + Withdrawn Guard Handoff

## Current State

- Registrar boot is clean on the live app.
- The live registrar at `http://localhost:8083/registrar/login` responds `200`.
- Recent startup/schema-repair chatter has been hardened away:
  - MariaDB driver is now used directly.
  - duplicate column / duplicate index repair noise has been reduced.
  - the live startup log is currently clean at boot.

## Smoke Check Performed

I ran a quick live route smoke pass against the registrar surface:

- `/login`
- `/admin/student-manager`
- `/admin/student-manager?username=ADDCLS-2026-001`
- `/admin/curriculum`
- `/admin/course-catalog`
- `/admin/class-scheduling`
- `/admin/grade-records`
- `/admin/scholarship`
- `/admin/withdrawals`

All of the above responded `200` on the live server.

Important caveat:

- those route checks only confirm that the pages respond
- they do not prove authenticated business flow correctness
- the live browser session still needs real user interaction for deeper UAT

## Withdrawn-Student Guard Fix

One real gap was found during targeted testing:

- the cashier walk-in payment flow could fall through to `Student not found` for a withdrawn student
- that was too weak for policy enforcement
- withdrawn students should be explicitly blocked before the lookup path can obscure the reason

Fix applied:

- `ScholarEnrollmentService.processWalkInPayment(...)` now checks `isWithdrawnStudent(...)` before trying to load the student record
- the withdrawn response is now explicit:
  - `ERROR: Withdrawn students cannot post payments through the cashier.`

## Test Alignment Update

The withdrawal controller unit test was aligned to the current controller contract:

- it now verifies redirect behavior and service delegation
- it no longer depends on a flash-message implementation detail in the standalone MVC test harness

## Targeted Verification

The following targeted test set was rerun after the fix:

- `WithdrawalServiceDirectDropTest`
- `WithdrawalControllerTest`
- `StudentIdentityReleaseServiceTest`
- `ScholarEnrollmentServiceWithdrawalGuardTest`

Result:

- targeted tests passed after the withdrawn guard fix

## Next Best Follow-Up

The next registrar seam worth hardening is the withdrawn identity / archive boundary:

- release of student numbers for reused enrollments
- archive-key lookup for withdrawn records
- ensuring withdrawn students stay blocked from payments, official release, and other live actions unless the flow is explicitly archive/reissue-related

If continuing from here, treat the withdrawn-student policy as the active seam and keep the admissions/enrollment coupling in mind while testing.
