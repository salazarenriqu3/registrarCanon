# Registrar Student Test Dataset

This is the current-code test set for Registrar student-facing workflows. Use it only in a disposable demo/UAT database.

## Load Order

Run the normal fresh database setup first. Then execute these files as separate scripts in this order:

1. `db/capss-demo-required/02_demo_seed_pick_one/demo_full_lifecycle.sql`
2. `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/02_scholarship_demo_seed.sql`
3. `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/04_registrar_student_dataset_verify.sql`

The final verification result must say `PASS: Registrar student dataset is ready`.

## Core Students

| Student number | Student | Purpose | Expected baseline |
|---|---|---|---|
| `2026-1001` | Maria Reyes Santos | Normal enrolled student | BSIT curriculum, committed current subjects, academic history, initial payment ledger |
| `SCH-UAT-ELIGIBLE` | Sofia Scholar | Scholarship success case | 27 current units, 27 completed units, eligible |
| `SCH-UAT-LOWUNITS` | Liam Low Units | Scholarship rejection case | 27 current units, 24 completed units, blocked by minimum completed units |

## Registrar Test Matrix

| Registrar action | Student | Expected result |
|---|---|---|
| Student search and profile | `2026-1001` | Profile is found and shows active/enrolled BSIT data |
| Current enrolled subjects | `2026-1001` | Committed subjects and total units are visible |
| Academic history | `2026-1001` | Passed grade rows are visible |
| Curriculum assignment and deficiencies | `2026-1001` | Current BSIT curriculum is assigned; deficiencies calculate from it |
| Print Registration Form and event history | `2026-1001` | Form uses the current committed load; printing records an event |
| Installment plan override | `2026-1001` | Override can be saved and reloaded for the student |
| TOR or transfer-credit entry | `2026-1001` | Credit appears in academic history and deficiency calculation after posting |
| Scholarship eligibility | `SCH-UAT-ELIGIBLE` | Shows 27 completed units and Eligible |
| Scholarship submit, approve, post, revoke | `SCH-UAT-ELIGIBLE` | Workflow moves through `PENDING`, `APPROVED`, `POSTED`; finance effect begins at `POSTED` |
| Scholarship policy rejection | `SCH-UAT-LOWUNITS` | Shows 24 completed units and `Needs at least 27 completed unit(s)` |
| Withdrawal workflow | `2026-1001` | Request is visible to Dean, then Registrar; use only when that test can be completed/reset |

## Comparison Rules

- Sofia and Liam intentionally have the same current load. Their scholarship result differs because completed grade units differ.
- Use Maria for non-destructive profile, load, history, curriculum, registration-form, and finance-policy checks.
- TOR crediting, program shifting, withdrawal completion, scholarship posting, and installment overrides mutate data. Run them one at a time and record the before/after result.
- Do not use the retired Registrar irregular advising or pre-registration screens; current ownership places enlistment and scheduling in Enrollment.
- Use disposable `TTRNS-*` and `TSHFT-*` lifecycle students for a full transferee or program-shift presentation when Maria must remain the clean baseline.

## Reset

1. Run `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/03_scholarship_demo_cleanup.sql`.
2. Run `db/capss-demo-required/06_reset/00_demo_reset.sql`.
3. Re-run the load order above.
