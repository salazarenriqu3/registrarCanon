# Test Data

These are the only current test SQL files included in the final package.

| File | Type | Purpose |
|---|---|---|
| `01_read_only_demo_smoke.sql` | Read-only | Active term, contract, builder, fee, faculty, and test-candidate checks |
| `02_scholarship_demo_seed.sql` | Idempotent seed | Sofia Scholar (27 completed units) and Liam Low Units (24 completed units), each with a current load and curriculum assignment |
| `03_scholarship_demo_cleanup.sql` | Cleanup | Removes only `SCH-UAT-*` and `SCH-DEMO-*` test records |
| `04_registrar_student_dataset_verify.sql` | Read-only | Verifies Maria plus both scholarship comparison students |
| `REGISTRAR_STUDENT_TEST_MATRIX.md` | Guide | Run order, expected data, Registrar actions, and reset order |

Run the seed only against a disposable demo/UAT database. The obsolete legacy BSIT applicant and old `student_grades` fixtures are intentionally excluded because they do not match the current canonical schema and term.

For full Registrar student UAT, seed Maria `2026-1001` first with
`db/capss-demo-required/02_demo_seed_pick_one/demo_full_lifecycle.sql`, then follow
`REGISTRAR_STUDENT_TEST_MATRIX.md`.
