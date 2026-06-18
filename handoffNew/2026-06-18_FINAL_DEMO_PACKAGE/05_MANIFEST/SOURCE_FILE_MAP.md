# Source File Map

This package freezes the current demo inputs on 2026-06-18. Runtime scripts use the package copies, not the source paths below.

| Package file | Original source |
|---|---|
| `01_DOCUMENTATION/*` | `registrar/handoffNew/FINAL_*_20260618.md` |
| `01_SCHEMA/01_base_schema_and_seed.sql` | `registrar/db/fix` |
| `02_CONTRACTS/02_enlistment_status_contract.sql` | `enrollment3/src/main/resources/sql/01_enlistment_status_schema.sql` |
| `02_CONTRACTS/06_exact_fee_schema.sql` | `registrar/docs/business_logic/schema_migration_001.sql` |
| `03_ACADEMIC_MASTER/03_full_curriculum.sql` | `registrar/db/04_seed_full_curriculum.sql` |
| `03_ACADEMIC_MASTER/04_academic_term_calendar.sql` | `registrar/db/demo_scripts/00_upsert_academic_terms_calendar.sql` |
| `04_TERM_AND_FEES/05_demo_fee_templates.sql` | `registrar/db/capss-demo-required/01_setup/03_seed_program_fees_full_lifecycle.sql` |
| `03_ACADEMIC_MASTER/07_retire_empty_programs.sql` | `registrar/db/manual_patches/20260608_retire_empty_curriculum_blockers.sql` |
| `03_ACADEMIC_MASTER/08_block_sections.sql` | `registrar/db/seed_all_program_block_sections_calendar.sql` |
| `03_ACADEMIC_MASTER/09_block_offerings.sql` | `registrar/db/seed_block_offerings.sql` |
| `03_ACADEMIC_MASTER/10_irregular_open_sections.sql` | `registrar/db/seed_irregular_open_sections.sql` |
| `03_ACADEMIC_MASTER/11_faculty_and_grading.sql` | `registrar/db/seed_faculty_professors_and_grading.sql` |
| `03_ACADEMIC_MASTER/12_class_schedules.sql` | `registrar/db/seed_all_class_schedules.sql` |
| `04_TERM_AND_FEES/13_activate_demo_term.sql` | `registrar/setup/sql/01_activate_term_2425_s1.sql` |
| `04_TERM_AND_FEES/14_materialize_demo_term_fees.sql` | `registrar/setup/sql/02_materialize_term_fees.sql` |
| `04_TERM_AND_FEES/15_materialize_calendar_term_fees.sql` | `registrar/setup/sql/05_materialize_all_calendar_term_fees.sql` |
| `03_ACADEMIC_MASTER/16_assign_demo_faculty.sql` | `registrar/setup/sql/03_assign_prof_cruz_demo.sql` |
| `05_VERIFICATION/17_verify_readiness.sql` | `registrar/setup/sql/04_verify_readiness.sql` |
| `03_TEST_DATA/02_scholarship_demo_seed.sql` | `registrar/handoffNew/sql_manual/08_scholarship_demo_seed.sql` |
| `03_TEST_DATA/03_scholarship_demo_cleanup.sql` | `package-generated scholarship cleanup companion` |
| `03_TEST_DATA/04_registrar_student_dataset_verify.sql` | `package-generated registrar UAT verification` |
| `03_TEST_DATA/REGISTRAR_STUDENT_TEST_MATRIX.md` | `package-generated registrar UAT guide` |

Excluded intentionally:

- retired Registrar irregular new-enrollee advising/pre-registration SQL
- legacy BSIT applicant fixture tied to a different term
- legacy grading fixture based on `student_grades` and old schedule columns
- historical term-2-only fee patches
