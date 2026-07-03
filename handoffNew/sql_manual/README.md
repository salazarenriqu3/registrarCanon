# sql_manual — legacy / optional patches

**For fresh PC demo setup, use `registrar/setup/` instead.**

| New location | Old file here |
|--------------|----------------|
| `setup/sql/01_activate_term_2425_s1.sql` | `01_calendar_and_active_term.sql` |
| `setup/sql/02_materialize_term_fees.sql` | `11_bootstrap_materialize_active_term_fees.sql` |
| `setup/sql/04_verify_readiness.sql` | `02_verify_readiness.sql` |
| `setup/fees/` | `../fee_import/` |

Files kept here for one-off manual config (sample applicant, single section, etc.):

| File | Purpose |
|------|---------|
| `03_manual_add_course.sql` | Add one course row to curriculum draft |
| `04_manual_bscpe_block_sections.sql` | Manual BSCPE block section seed |
| `05_manual_schedule_one_section.sql` | Schedule one section |
| `06_retire_empty_programs.sql` | Soft-retire empty programs |
| `07_sample_applicant.sql` | Insert test applicant |
| `08_migrate_program_fee_settings.sql` | Fee migration helper |
| `09_seed_term1_s2_exact_fees_bscpe_bsit.sql` | BSCPE/BSIT S2 exact fees (historical) |
| `10_seed_term2_bsbio_y3_y4_fees.sql` | BSBio Y3/Y4 gap fill (historical) |
| `14_fix_reissue_demo_snapshot_20260630.sql` | Repairs the disposable reissue demo snapshot to a real course/section pair |
| `15_lec_lab_course_components_20260701.sql` | Optional existing-DB patch for LEC/LAB component columns and indexes |
| `16_migrate_legacy_lec_lab_courses_20260701.sql` | Legacy mixed-course migration into archived source rows plus live LEC/LAB component rows |

**Run order for fresh DB:** `registrar/setup/RUN_FRESH_SETUP.cmd`
