# Test Data

These are the only current test SQL files included in the final package.

| File | Type | Purpose |
|---|---|---|
| `01_read_only_demo_smoke.sql` | Read-only | Active term, contract, builder, fee, faculty, and test-candidate checks |
| `02_scholarship_demo_seed.sql` | Idempotent seed | Sofia Scholar (27 completed units) and Liam Low Units (24 completed units), each with a current load and curriculum assignment |
| `03_scholarship_demo_cleanup.sql` | Cleanup | Removes only `SCH-UAT-*` and `SCH-DEMO-*` test records |
| `04_registrar_student_dataset_verify.sql` | Read-only | Verifies Maria plus both scholarship comparison students |
| `05_registrar_feature_demo_seed.sql` | Idempotent seed | Full registrar demo dataset: roomed schedules, admission bridge, print/history students, add-class, transfer, shift, overpayment, and withdrawal baselines |
| `06_registrar_feature_demo_verify.sql` | Read-only | Verifies the full registrar demo dataset after the seed bundle runs |
| `20_withdrawal_uat_seed.sql` | Idempotent seed | Creates a current-term student with three committed classes for Registrar withdrawal UAT |
| `21_withdrawal_uat_cleanup.sql` | Cleanup | Removes only the focused withdrawal UAT records |
| `REGISTRAR_STUDENT_TEST_MATRIX.md` | Guide | Run order, expected data, Registrar actions, and reset order |
| `admission_uploads/` | Static assets | Inline-viewable admission documents referenced by the registrar feature demo |

Run the seed only against a disposable demo/UAT database. The obsolete legacy BSIT applicant and old `student_grades` fixtures are intentionally excluded because they do not match the current canonical schema and term.

For the current end-to-end Registrar presentation path, use:

1. `04_RUNNERS\13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
2. `04_RUNNERS\04_START_REGISTRAR.cmd`
3. `01_DOCUMENTATION\REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`

Focused withdrawal UAT can be loaded with `04_RUNNERS\11_LOAD_WITHDRAWAL_UAT_DATA.cmd`
and reset with `04_RUNNERS\12_CLEAN_WITHDRAWAL_UAT_DATA.cmd`.
