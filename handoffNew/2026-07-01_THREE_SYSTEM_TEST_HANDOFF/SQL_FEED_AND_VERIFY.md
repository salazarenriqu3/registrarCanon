# SQL Feed And Verification

Use Registrar as the database baseline, then apply compatibility/demo SQL.

## Fresh Baseline

From `E:\registrarCanon_canon`:

```cmd
setup\RUN_FRESH_SETUP.cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

## Compatibility SQL Order

Run these against `eacdb` after the baseline or before a three-app smoke test on an existing database:

| Order | SQL | Purpose |
| --- | --- | --- |
| 1 | `sql\00_preflight_shared_contract.sql` | Confirms shared DB, current term, core tables, and demo accounts |
| 2 | `sql\01_apply_required_demo_contracts.sql` | Applies idempotent compatibility columns/indexes used by current Registrar/Enrollment/Admission flows |
| 3 | `sql\02_seed_minimum_cross_system_smoke_data.sql` | Adds one safe applicant smoke row and one LEC/LAB component sample if missing |
| 4 | `sql\03_verify_three_system_demo_ready.sql` | Verifies the database is ready for the basic three-system test path |

## Existing Canon SQL Worth Knowing

| File | When to use |
| --- | --- |
| `E:\registrarCanon_canon\handoffNew\sql_manual\14_fix_reissue_demo_snapshot_20260630.sql` | If testing released/reissued student-number demo case |
| `E:\registrarCanon_canon\handoffNew\sql_manual\15_lec_lab_course_components_20260701.sql` | If only patching an existing DB for LEC/LAB component metadata |
| `E:\registrarCanon_canon\handoffNew\sql_manual\16_migrate_legacy_lec_lab_courses_20260701.sql` | If an existing DB still has mixed lecture/lab legacy rows that need to be archived and split |
| `E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\04_verify_lec_lab_family_migration.sql` | If you want a one-step check that legacy mixed rows are gone and split families are present |
| `E:\EnrollLatest\enrollment3\src\main\resources\sql\07_pre_reg_subject_line_sort_order.sql` | If Enrollment pre-reg subject-line ordering fails on missing `sort_order` |
| `E:\registrarCanon_canon\handoffNew\sql_manual\08_scholarship_demo_seed.sql` | If the scholarship demo needs seeded official grade rows |
| `E:\registrarCanon_canon\handoffNew\sql_manual\13_transfer_credit_approval_demo_20260626.sql` | If testing Registrar approval of Enrollment-origin accreditation/credit requests |

## Quick CLI Example

If `mysql` is not on PATH, use:

```cmd
"C:\Program Files\MariaDB 12.3\bin\mysql.exe"
```

PowerShell on this machine does not accept `mysql < file.sql` redirection. Use `Get-Content | & mysql.exe` instead:

```powershell
Get-Content E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\00_preflight_shared_contract.sql | & "C:\Program Files\MariaDB 12.3\bin\mysql.exe" -uroot eacdb
Get-Content E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\01_apply_required_demo_contracts.sql | & "C:\Program Files\MariaDB 12.3\bin\mysql.exe" -uroot eacdb
Get-Content E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\02_seed_minimum_cross_system_smoke_data.sql | & "C:\Program Files\MariaDB 12.3\bin\mysql.exe" -uroot eacdb
Get-Content E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\03_verify_three_system_demo_ready.sql | & "C:\Program Files\MariaDB 12.3\bin\mysql.exe" -uroot eacdb
```

The SQL pack was validated on 2026-07-01 against the local `eacdb`.

Latest validation highlights:

- Active term resolves to `1120242025`, `term_id = 1`.
- Smoke applicant `3SYS-SMOKE-0001` exists and is `QUALIFIED FOR ENROLLMENT`.
- Demo LEC/LAB family `DEMOCP` exists as `DEMOCP-LEC` and `DEMOCP-LAB`.
- Legacy mixed lecture/lab rows now have a guarded migration helper that archives the source row and re-homes curriculum/prerequisite links to the split component rows.
- The Registrar bootstrap now auto-runs the same legacy LEC/LAB migration helper when old mixed rows are still present.
- `student_number_release_registry`, `student_identity_archive`, `student_archive_custody_events`, `grades`, `grade_record_events`, and `grade_change_requests` exist.
- `applicant_pre_reg_subject_lines.sort_order` exists.
- Active-term schedule rows currently have rooms, but many active-term sections still lack faculty assignment. Use curated demo sections or assign faculty before presenting schedule completeness.

## Do Not Run Blindly

- Do not run `RUN_FRESH_SETUP.cmd` if the tester needs to preserve live local data.
- Do not import `E:\EnrollLatest\Dump20260629.sql` over the Registrar baseline unless the task is specifically to compare or replace the shared database dump.
- Do not treat Enrollment fee SQL as Registrar-owned. Registrar reads fee readiness; Enrollment/Accounting owns fee authoring.
