# Next Agent Handoff

## Current Status

The local three-system workspace is:

- Registrar: `E:\registrarCanon_canon`
- Enrollment: `E:\EnrollLatest\enrollment3`
- Admission: `E:\AdmitLatest\admission`

The shared database is `eacdb`.

Admission was started successfully on `http://localhost:8081/` after adding explicit MySQL dialect settings.

## Changes Made In This Pass

Admission:

- Added explicit Hibernate dialect settings to:
  - `E:\AdmitLatest\admission\src\main\resources\application.properties`
  - `E:\AdmitLatest\admission\src\main\resources\application-local.properties`

Registrar docs and SQL:

- Added this handoff folder:
  - `E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF`
- Added runbook, SQL manifest, testing storyboard, next-agent handoff, and SQL verification/feed files.
- The SQL pack was run against local `eacdb` on 2026-07-01.
- Smoke applicant `3SYS-SMOKE-0001` and demo course family `DEMOCP-LEC` / `DEMOCP-LAB` were inserted or confirmed.

Earlier same-day Registrar change:

- LEC/LAB split foundation is implemented.
- Mixed lecture/lab courses now create `BASE-LEC` and `BASE-LAB` component rows for new catalog/manual curriculum entries.
- Existing used mixed rows are intentionally not auto-split.

Latest Registrar finance fix:

- `/admin/scholar-ledger?keyword=<student>` is now implemented and renders the official student ledger page.
- Student Profile and the official ledger page both call the same finance refresh path before rendering, which keeps the mini-ledger snapshot and the persisted transaction ledger aligned.
- The full ledger page now normalizes the academic-load payload so Thymeleaf can render course code/title/unit fields safely.

## First Thing To Do Next

1. Run `SQL_FEED_AND_VERIFY.md` in order.
2. Start all three apps using `RUNBOOK_THREE_APPS.md`.
3. Execute `TESTING_STORYBOARD.md` from Test 0 through Test 3 first.

## Known Risks

- Existing old mixed lecture/lab rows still need a deliberate migration if the user wants them structurally split.
- Admission startup may log duplicate index warnings on `applicants`; treat them as non-blocking if the app binds port `8081`.
- Current validation shows active-term schedules have concrete rooms, but many active-term sections still lack faculty assignment. Use curated demo sections or assign faculty before presenting schedule completeness.
- Enrollment ledger/status behavior was previously fixed/analyzed, but should be retested live: ledger search must not mutate an enrolled student back to pending.
- Released/reissued student-number flow should be tested with `14_fix_reissue_demo_snapshot_20260630.sql` if that demo case is needed.
- Do not import `E:\EnrollLatest\Dump20260629.sql` over the Registrar fresh baseline unless the user explicitly asks to replace the database with that dump.

## Current Ownership Rules

- Admission owns applicant intake and document submission.
- Enrollment owns pre-registration, cashier/accounting, fees, student-number issuance, and enrollment finalization.
- Registrar owns programs, curriculum, courses, schedules, sections, rooms, official student records, grade governance, withdrawal, archive custody, and academic approval gates.

## Quick Verification Commands

```cmd
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\03_verify_three_system_demo_ready.sql
```

```cmd
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\04_verify_lec_lab_family_migration.sql
```

```cmd
netstat -ano | findstr :8081
netstat -ano | findstr :8082
netstat -ano | findstr :8083
```

## Useful Handoff References

- `E:\registrarCanon_canon\handoffNew\CURRENT_STATE_MAP.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-30_THREE_SYSTEM_ALIGNMENT_PACK\README.md`
- `E:\registrarCanon_canon\handoffNew\2026-07-01_LEC_LAB_COMPONENT_ROLLOUT.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-30_ENROLLMENT_REGISTRAR_ALIGNMENT_HANDOFF.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-29_WITHDRAWN_STUDENT_GOVERNANCE.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-30_REGISTRAR_GRADE_GOVERNANCE_NOTE.md`
