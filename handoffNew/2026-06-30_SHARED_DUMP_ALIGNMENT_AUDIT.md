# Shared Dump Alignment Audit

Date: 2026-06-30

Purpose: record the result of comparing the latest Enrollment dump against the current Registrar canon so the next agent does not have to rediscover which gaps are true code gaps and which are only shared-seed drift.

## Bottom Line

- The three-system ownership model is aligned in the latest handoffs.
- The current Registrar repo already carries the newer registrar-only governance contract in its bootstrap SQL and runtime setup.
- The latest Enrollment dump is still behind that registrar contract in several registrar-owned tables and columns.
- Fresh registrar setup should therefore be validated against the registrar bootstrap bundle, not judged only by the older Enrollment dump.

## Aligned In The Dump

- `system_settings.CURRENT_ACADEMIC_TERM`
- `academic_terms`
- `student_enlistments.enlistment_status` contract path
- `applicant_pre_reg_subject_lines.sort_order`
- `student_withdrawal_requests`
- `student_withdrawal_request_lines`
- `student_document_events`

## Still Missing From The Enrollment Dump

- `student_identity_archive`
- `student_number_release_registry`
- `student_archive_files`
- `student_archive_custody_events`
- `grade_record_events`
- `transfer_credit_requests`
- `grade_change_requests.reviewed_by`
- `grade_change_requests.review_note`
- `grade_change_requests.rejected_at`

## Registrar Canon Source For The Missing Contract

- `setup\RUN_FRESH_SETUP.cmd`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\sql\01_SCHEMA\01_base_schema_and_seed.sql`
- `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\sql\02_CONTRACTS\18_registrar_withdrawal_governance.sql`
- `src\main\java\com\iuims\registrar\core\DatabaseSetupService.java`
- `src\main\java\com\iuims\registrar\core\StudentIdentityReleaseService.java`
- `src\main\java\com\iuims\registrar\forms\StudentArchiveCustodyService.java`
- `src\main\java\com\iuims\registrar\academic\GradeRecordEventService.java`

## Practical Reading

- This is not a sign that Registrar behavior is missing.
- This is a sign that the shared dump should not be treated as the only current schema authority for registrar-owned governance features.
- A fresh registrar clone that runs the bundled bootstrap should create the current registrar contract even if the compared Enrollment dump does not include those tables yet.

## Action Taken In This Pass

- `setup\sql\04_verify_readiness.sql` now checks the registrar governance contract explicitly.
- `setup\sql\08_verify_registrar_feature_demo.sql` now checks the same contract before demo assertions.
- `setup\README.md`, `setup\BOOTSTRAP_SEED_MANIFEST.md`, and `setup\AGENT_FRESH_SETUP.md` now say plainly that the bundled bootstrap includes these registrar-only contract tables.

## Remaining Cross-System Follow-Up

- Enrollment and Admission still need their own shared dump / seed refresh if we want one shared exported schema snapshot to match the current registrar canon exactly.
- Until then, use the registrar bootstrap bundle as the authoritative registrar setup path.
