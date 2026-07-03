# Testing / Demo File Map

Date: 2026-06-27

This is the shortest “what files do I run for which demo?” guide.

## 1. Core files to keep open

- [Full runbook](D:\registrarCanon_canon\handoffNew\2026-06-27_THREE_PROJECT_DEMO_RUNBOOK.md)
- [Short checklist](D:\registrarCanon_canon\handoffNew\2026-06-27_THREE_PROJECT_DEMO_CHECKLIST.md)
- [5-minute rehearsal](D:\registrarCanon_canon\handoffNew\2026-06-27_THREE_PROJECT_5_MINUTE_REHEARSAL.md)
- [Cue card](D:\registrarCanon_canon\handoffNew\2026-06-27_THREE_PROJECT_CUE_CARD.md)

## 2. Full registrar demo bundle

Use these when you want the complete showcase dataset:

- Seed all registrar demo data:
  - [13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd)
- Seed SQL:
  - [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)
- Verify SQL:
  - [06_registrar_feature_demo_verify.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\06_registrar_feature_demo_verify.sql)

Use this bundle for:

- admission document viewing
- registration form printing
- reg-form history
- document trail
- add subject
- TOR / transfer credit
- shift
- overpayment
- roomed schedules
- general registrar demo flow

## 3. Scenario file map

### A. Admission / document trail demo

Use:

- [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)
- [06_registrar_feature_demo_verify.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\06_registrar_feature_demo_verify.sql)

Click path:

1. Admission login
2. Open `DEMO-SANTOS-001`
3. Show uploaded files and trail
4. Move to Registrar and open `2026-1001`

### B. Add subject demo

Use:

- [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)

Click path:

1. Registrar login
2. Open `/admin/student-manager?username=ADDCLS-2026-001`
3. Scroll to Add Subjects
4. Pick a valid block-section offering
5. Click `Add`

### C. Shift demo

Use:

- [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)

Click path:

1. Registrar login
2. Open `/admin/student-manager?username=TSHFT-2026-001`
3. Open the shift workspace
4. Choose target program and curriculum
5. Submit shift

### D. Withdrawal demo

Use:

- [20_withdrawal_uat_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\20_withdrawal_uat_seed.sql)
- [21_withdrawal_uat_cleanup.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\21_withdrawal_uat_cleanup.sql)

Click path:

1. Registrar login
2. Open `/admin/student-manager?username=SPRINT-DEMO-2026-001`
3. Show committed classes
4. Run withdrawal / direct-drop
5. Show the trail entry

### E. Scholarship demo

Use:

- [02_scholarship_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\02_scholarship_demo_seed.sql)
- [03_scholarship_demo_cleanup.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\03_scholarship_demo_cleanup.sql)
- [04_registrar_student_dataset_verify.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\04_registrar_student_dataset_verify.sql)

Click path:

1. Registrar login
2. Open `/admin/scholarships`
3. Inspect `SCH-UAT-ELIGIBLE`
4. Inspect `SCH-UAT-LOWUNITS`

### F. Student profile / print / history demo

Use:

- [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)

Click path:

1. Registrar login
2. Open `/admin/student-manager?username=2026-1001`
3. Show profile, admission snapshot, documents, reg-form history
4. Print registration form
5. Open document trail

## 4. Read-only smoke checks

Use:

- [01_read_only_demo_smoke.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\01_read_only_demo_smoke.sql)

Use this when you want to confirm:

- active term
- roomed schedules
- builder state
- fee/readiness state
- faculty / test-candidate baseline

## 5. Quick reset order

If you mutate the demo data, reset with:

1. For scholarship: `03_scholarship_demo_cleanup.sql`
2. For withdrawal UAT: `21_withdrawal_uat_cleanup.sql`
3. Re-run the matching seed
4. Re-run `06_registrar_feature_demo_verify.sql` or `04_registrar_student_dataset_verify.sql`

## 6. If you want the shortest possible path

Just use these three files:

- [13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd)
- [05_registrar_feature_demo_seed.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\05_registrar_feature_demo_seed.sql)
- [06_registrar_feature_demo_verify.sql](D:\registrarCanon_canon\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\03_TEST_DATA\06_registrar_feature_demo_verify.sql)
