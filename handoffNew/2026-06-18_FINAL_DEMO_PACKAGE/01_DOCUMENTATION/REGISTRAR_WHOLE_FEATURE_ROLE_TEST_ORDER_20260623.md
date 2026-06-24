# Registrar Whole-Feature Role Test Order

Date: 2026-06-23
Base repo: `C:\newer\registrarCanon_canon`
Base URL: `http://localhost:8083/registrar`
Active demo term: `1120242025`

This is the current Registrar-first execution order for manual demo and UAT. Use this document as the front door before opening the older lifecycle and UAT manuals.

## 1. Source-of-truth order

Use the documents in this order:

1. `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\00_START_HERE.md`
2. `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\01_DOCUMENTATION\REGISTRAR_WHOLE_FEATURE_ROLE_TEST_ORDER_20260623.md`
3. `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\01_DOCUMENTATION\FINAL_DEMO_AND_TEST_MANUAL_20260618.md`
4. `handoffNew\2026-06-22_WITHDRAWAL_UAT_CHECKLIST.md`
5. `handoffNew\THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`

Interpretation rule:

- If the older lifecycle manual conflicts with this test order, follow this document.
- If a workflow depends on retired Dean irregular pre-registration inside Registrar, skip it.
- If a workflow depends on external Admission or Enrollment ownership, treat Registrar as the consumer, not the owner.

## 2. Current scope boundaries

In scope:

- Registrar admin builders and configuration
- Registrar student profile and student records
- Transfer crediting and program shift controls
- Registrar-only withdrawal workflow and archive trail
- Scholarship policy, review, approval, and posting
- Faculty grading pages and Registrar approval pages
- Registration form, document trail, and student-facing academic record checks

Out of scope for Registrar-only sign-off:

- Retired Dean irregular new-enrollee advising and Registrar-side pre-registration
- Admission-owned applicant intake logic
- Enrollment-owned enlistment, payment, and student-number issuance logic
- Full grading finalization policy beyond the current demo surfaces

## 3. Accounts, fixtures, and reset points

Primary accounts:

- `admin / 1234`
- `prof.cruz / 1234`

Primary test students:

- `2026-1001` - baseline student profile and records
- `SCH-UAT-ELIGIBLE` - scholarship positive case
- `SCH-UAT-LOWUNITS` - scholarship negative case
- `SPRINT-DEMO-2026-001` - disposable withdrawal case

Recommended reset rhythm:

1. Run fresh database setup before a formal demo day.
2. Use the scholarship seed only when entering the scholarship session.
3. Use the withdrawal seed only immediately before the withdrawal session.
4. Keep `2026-1001` as the least-mutated baseline for profile, load, and history checks.

## 4. Whole-feature test order

Run the sessions in this order. Do not start lifecycle or cross-app stories until Sessions 0 to 6 are stable.

### Session 0 - Machine, database, and smoke gate

Goal:

- Confirm the demo machine, disposable database, and Registrar startup are healthy.

Run:

- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\00_VERIFY_PACKAGE.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\01_CHECK_MACHINE.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\02_BUILD_ALL.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\03_RUN_REGISTRAR_TESTS.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\04_START_REGISTRAR.cmd`

Pass when:

- `admin / 1234` logs in successfully
- active term is `1120242025`
- Student Profile loads
- Class Scheduling loads
- Term Fees load with no demo-blocking gaps

### Session 1 - Registrar configuration and academic builders

Role:

- Registrar Admin

Focus:

- settings readiness
- finance policy
- course catalog
- curriculum builder
- class scheduling
- faculty teaching load

Pass when:

- the academic chain is traceable from program -> course -> curriculum -> section -> schedule
- overlapping room assignments are rejected
- overlapping faculty schedules are rejected
- room `TBA` or tentative assignment still works where intended

Important note:

- This is where we validate builder coherence, not just page rendering.

### Session 2 - Student Profile and records

Role:

- Registrar Admin

Primary student:

- `2026-1001`

Focus:

- registrar editable profile
- current load
- academic history
- curriculum assignment
- deficiencies
- registration form
- document trail

Pass when:

- identity, load, curriculum, and history all agree for the same student
- profile edits persist only on Registrar-owned fields
- registration form reflects the current committed load

### Session 3 - Transfer crediting and program shift controls

Role:

- Registrar Admin

Primary student:

- use a disposable student if a full mutation will be performed

Focus:

- TOR or transfer credit entry
- curriculum effect of credited subjects
- program shift controls and audit trail

Pass when:

- credited courses affect history and deficiency calculation
- program shift is traceable and not visually detached from the student record

### Session 4 - Withdrawal workflow

Role:

- Registrar Admin only

Primary student:

- `SPRINT-DEMO-2026-001`

Prep:

- run `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\11_LOAD_WITHDRAWAL_UAT_DATA.cmd`

Focus:

- single-subject withdrawal request
- full current-term withdrawal request
- registrar approval queue
- withdrawal history
- document trail

Pass when:

- Student Profile creates requests instead of directly dropping classes
- Registrar approval is required
- class withdrawal and full-student withdrawal both leave archived traces
- no Dean route is involved

### Session 5 - Scholarship workflow

Role:

- Registrar Admin

Primary students:

- `SCH-UAT-ELIGIBLE`
- `SCH-UAT-LOWUNITS`

Prep:

- run `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\07_LOAD_SCHOLARSHIP_TEST_DATA.cmd`

Current policy areas to verify:

- weighted GWA
- maximum Prelim grade allowed
- maximum Midterm grade allowed
- maximum Finals grade allowed
- minimum graded or taken units required

Pass when:

- Sofia is eligible
- Liam is blocked for units
- review -> approve -> post changes state correctly
- finance effect activates only after posting

### Session 6 - Faculty grading and Registrar approvals

Roles:

- Faculty
- Registrar Admin

Account:

- `prof.cruz / 1234`

Focus:

- assigned classes page
- grade sheet access
- grade save and reload
- admin approvals page

Pass when:

- faculty sees only assigned teaching context
- saved grades persist
- Registrar can see the corresponding review surface

Note:

- Full grading finalization remains deferred. This session is about grade-entry flow and Registrar visibility.

### Session 7 - Optional Enrollment bridge check

Role:

- Registrar Admin plus external Enrollment operator

Purpose:

- confirm Registrar-owned academic structures are consumable outside Registrar

Do this only after Sessions 0 to 6 pass. This is not part of Registrar-only acceptance, but it is useful before full storyboard demo day.

### Session 8 - Full lifecycle storyboard

Use:

- `handoffNew\THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`

Run this last, not first.

Reason:

- it is the broadest narrative test, but it contains older assumptions and is safest once the isolated Registrar features are already proven

## 5. Recommended test-day route

For the fastest meaningful Registrar-first pass:

1. Session 0
2. Session 1
3. Session 2
4. Session 4
5. Session 5
6. Session 6
7. Session 3
8. Session 8 only if the focused sessions are green

Why this order:

- it catches the high-risk surfaces first
- it keeps baseline student data cleaner for longer
- it delays the more destructive or cross-cutting workflows until the core pages are already trusted

## 6. Known doc mismatches to keep in mind

- `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` is still useful, but it is older than the current withdrawal and scholarship work.
- `FINAL_DEMO_AND_TEST_MANUAL_20260618.md` is the right broad manual, but some wording is older than the latest scholarship and test-suite state.
- Use the focused withdrawal checklist for withdrawal truth.
- Use the current scholarship seed and current app behavior for scholarship truth.

## 7. Stop conditions

Pause testing and record a blocker if any of these happen:

- login loops back to the login page
- active term is not `1120242025`
- Student Profile cannot find the baseline student dataset
- Class Scheduling cannot enforce room or faculty collision rules
- withdrawal requests bypass Registrar approval
- scholarship status changes but finance activation timing is wrong
- faculty account cannot reach assigned classes or saved grades do not persist

## 8. What to open beside this guide

Keep these open during testing:

- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\01_DOCUMENTATION\FINAL_DEMO_AND_TEST_MANUAL_20260618.md`
- `handoffNew\2026-06-22_WITHDRAWAL_UAT_CHECKLIST.md`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\REGISTRAR_STUDENT_TEST_MATRIX.md`

This gives us one current execution order, one broad manual, one focused withdrawal checklist, and one fixture map.
