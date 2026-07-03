# Bootstrap Seed Manifest

Everything **`RUN_FRESH_SETUP.cmd`** loads, in order.  
Agents: if a demo feature is missing, find its step here and re-run that file in Workbench (or fix bootstrap and re-run full setup).

**Active term after full run:** `1120242025` (A.Y. 2024–2025, 1st Semester)

---

## Step-by-step

Important note:

- `setup\RUN_FRESH_SETUP.cmd` delegates to the bundled fresh-database runner under `handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\`.
- That self-contained SQL bundle is the live bootstrap authority for fresh-clone setup.
- The bundle already includes the newer registrar governance contract used by the current canon:
  - `student_identity_archive`
  - `student_number_release_registry`
  - `student_archive_files`
  - `student_archive_custody_events`
  - `transfer_credit_requests`
  - `grade_record_events`
  - reviewer metadata on `grade_change_requests`

| # | File | Seeds |
|---|------|--------|
| 0 | *(cmd)* DROP + CREATE `eacdb` | Empty UTF8MB4 database |
| 1 | `registrar/db/fix` | Full schema, `system_settings` (finance gates, term), `enrollment_settings`, `term_installment_plan`, admin user, demo applicants |
| 2 | `enrollment3/src/main/resources/sql/01_enlistment_status_schema.sql` | `student_enlistments.enlistment_status` (STAGED/COMMITTED) |
| 3 | `registrar/db/04_seed_full_curriculum.sql` | Courses, 21 program curricula, legacy class section rows |
| 4 | `registrar/db/demo_scripts/00_upsert_academic_terms_calendar.sql` | Calendar terms **1120242025** … **2120272028** |
| 5 | `registrar/db/capss-demo-required/01_setup/03_seed_program_fees_full_lifecycle.sql` | Global fee templates (`term_id` NULL), demo tuition/misc/other |
| 6 | `registrar/docs/business_logic/schema_migration_001.sql` | `program_fee_settings` table + migrate from legacy fee tables |
| 7 | `registrar/db/manual_patches/20260608_retire_empty_curriculum_blockers.sql` | Soft-retire 6 empty programs |
| 8 | `registrar/db/seed_all_program_block_sections_calendar.sql` | **Block sections** `PROGRAM-Y-S-A` for **every calendar term** × active programs |
| 9 | `registrar/db/seed_block_offerings.sql` | **`block_offerings`** rows + `class_sections.block_id` links |
| 10 | `registrar/db/seed_irregular_open_sections.sql` | **`IRREG-A`** open section per course/term (irregular enlist) |
| 11 | `registrar/db/seed_faculty_professors_and_grading.sql` | Faculty accounts (`prof.cruz`, etc.), faculty assignment, **grading windows FORCE_OPEN**, DRAFT grade backfill |
| 12 | `registrar/db/seed_all_class_schedules.sql` | Rooms, **class_schedules** (day/time/room), minimize TBA |
| 13 | `registrar/setup/sql/01_activate_term_2425_s1.sql` | Activate **`1120242025`**, set `CURRENT_ACADEMIC_TERM` |
| 14 | `registrar/setup/sql/02_materialize_term_fees.sql` | Exact fees for term 1; Y3/Y4 gap fill; pre-seed term 2 |
| 15 | `registrar/setup/sql/05_materialize_all_calendar_term_fees.sql` | Copy fees to **all other calendar terms** (2425 S2 → 2728 S2) |
| 16 | `registrar/setup/sql/03_assign_prof_cruz_demo.sql` | Apply a curated active-term demo faculty mix (keeps `prof.cruz` grading-ready); refresh grading windows |
| 17 | `registrar/setup/sql/06_clean_schedule_dataset.sql` | Clear seeded room collisions to TBA, drop section self-overlaps, and clear inactive-term faculty assignments |
| 18 | `registrar/setup/sql/04_verify_readiness.sql` | SQL report — fees, sections, blocks, faculty, scheduling constraints, gates |

---

## Per-term data after bootstrap

| Calendar term | term_code example | Block sections | IRREG-A | Fees | Schedules |
|---------------|-------------------|----------------|---------|------|-----------|
| 2425 S1 | `1120242025` | Yes | Yes | Yes | Yes |
| 2425 S2 | `2120242025` | Yes | Yes | Yes | Yes |
| 2526 S1/S2 | `1120252026` … | Yes | Yes | Yes | Yes |
| … through 2728 | `1120272028` … | Yes | Yes | Yes | Yes |

**Only one term is active** (`is_active=1`) at a time — default **`1120242025`**. Others are pre-seeded for term transition without rebuilding sections, but faculty assignments outside the active term are intentionally cleared by the cleanup step until scheduling is done for that term.

---

## Not in bootstrap (manual / optional)

| Item | How to add |
|------|------------|
| Official production fee rates | Edit CSV in `setup/fees/` → Program Fees import |
| New AY beyond 2728 | Add row to `00_upsert_academic_terms_calendar.sql`, re-run seeds 8–12 + fee SQL |
| Custom block layout | Class Scheduling UI or `handoffNew/sql_manual/` |
| Python preflight | `_runtime_logs/run_full_preflight.py` (optional) |

---

## Re-run single step (Workbench)

```sql
USE eacdb;
SOURCE registrar/db/seed_all_class_schedules.sql;
SOURCE registrar/setup/sql/03_assign_prof_cruz_demo.sql;
SOURCE registrar/setup/sql/06_clean_schedule_dataset.sql;
SOURCE registrar/setup/sql/04_verify_readiness.sql;
```

Paths relative to **project root** when using mysql CLI from project root.
