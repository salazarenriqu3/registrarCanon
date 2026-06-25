# Repo Push Guide for `registrarCanon`

Date: 2026-06-25
Workspace: `D:\registrarCanon_canon`
Target GitHub repo: `https://github.com/salazarenriqu3/registrarCanon`
Target branch: `codex/registrar-withdrawal-governance`
Target remote alias in this workspace: `registrarCanon`

## 1. Purpose

This guide is for another agent who needs to publish the current updated Registrar system state from this workspace to the GitHub repo above.

It answers:

- which remote to push to
- which branch to use
- what changes are part of the updated system
- what must be validated before push
- a safe command sequence for commit and push

## 2. Critical remote and branch warning

This workspace has multiple remotes:

- `registrarCanon` -> `https://github.com/salazarenriqu3/registrarCanon.git`
- `origin` -> `https://github.com/salazarenriqu3/registrar.git`
- `localCanon` -> `https://github.com/salazarenriqu3/registrarCanon-local.git`

For this publish task, the correct remote is:

- `registrarCanon`

Do not accidentally push this work to `origin`.

The correct branch is already checked out:

- `codex/registrar-withdrawal-governance`

It tracks:

- `registrarCanon/codex/registrar-withdrawal-governance`

## 3. Current branch state

At the time this guide was written:

- local branch: `codex/registrar-withdrawal-governance`
- tracking branch: `registrarCanon/codex/registrar-withdrawal-governance`
- branch state: ahead by 1 commit
- that existing local-only commit is:
  - `bbbf21f` - `Restore withdrawal penalty policy`

Important:

- Do not lose or reset that commit.
- The next push must include both:
  - the already-ahead local commit
  - the remaining uncommitted working-tree changes

## 4. What the updated system includes

The current updated system is not just one feature. It is the combination of:

- withdrawal governance and penalty-policy restoration
- scheduling hardening
- faculty max-load and suspicious-assignment audit/repair
- active-term faculty/demo login corrections
- schedule dataset cleanup and readiness verification
- full registrar feature demo dataset and runners
- applicant document demo assets
- refreshed handoff/specification/demo docs
- package mirror updates under the dated handoff bundle

If you publish only part of this set, the repo will not fully reflect the current working system.

## 5. File groups that must be pushed

Unless there is an intentional reason to split work into multiple PRs, publish the full set below together.

### A. Core code and templates

- `src/main/java/com/iuims/registrar/academic/AcademicGradingService.java`
- `src/main/java/com/iuims/registrar/academic/BlockOfferingService.java`
- `src/main/java/com/iuims/registrar/academic/ScheduleConflictValidator.java`
- `src/main/java/com/iuims/registrar/config/DemoPasswordResetRunner.java`
- `src/main/java/com/iuims/registrar/core/DatabaseSetupService.java`
- `src/main/java/com/iuims/registrar/faculty/FacultyLoadController.java`
- `src/main/java/com/iuims/registrar/faculty/FacultyLoadService.java`
- `src/main/java/com/iuims/registrar/portal/AcademicController.java`
- `src/main/resources/templates/admin_class_scheduling.html`
- `src/main/resources/templates/admin_faculty_load.html`

### B. Tests

- `src/test/java/com/iuims/registrar/academic/ScheduleConflictValidatorTest.java`
- `src/test/java/com/iuims/registrar/academic/BlockOfferingServiceTest.java`
- `src/test/java/com/iuims/registrar/faculty/FacultyLoadServiceTest.java`

### C. Source seeds and setup files

- `db/seed_all_class_schedules.sql`
- `db/seed_faculty_professors_and_grading.sql`
- `setup/sql/03_assign_prof_cruz_demo.sql`
- `setup/sql/04_verify_readiness.sql`
- `setup/sql/06_clean_schedule_dataset.sql`
- `setup/sql/07_seed_registrar_feature_demo.sql`
- `setup/sql/08_verify_registrar_feature_demo.sql`
- `setup/LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
- `setup/START_REGISTRAR_DEMO.cmd`
- `setup/README.md`
- `setup/AGENT_FRESH_SETUP.md`
- `setup/BOOTSTRAP_SEED_MANIFEST.md`

### D. Top-level handoff and specification docs

- `README.md`
- `handoffNew/START_HERE_NEW_PC_HANDOFF.md`
- `handoffNew/FINAL_SYSTEM_DOCUMENTATION_20260618.md`
- `handoffNew/FINAL_HANDOVER_20260618.md`
- `handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`
- `handoffNew/REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
- `handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`
- `handoffNew/HUMAN_UAT_CHECKLIST.md`
- `handoffNew/THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`

### E. Dated package mirror updates

- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/00_START_HERE.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/01_DOCUMENTATION/FINAL_HANDOVER_20260618.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/01_DOCUMENTATION/FINAL_SYSTEM_DOCUMENTATION_20260618.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/01_DOCUMENTATION/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/01_DOCUMENTATION/REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/README.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/RUN_FRESH_DATABASE.cmd`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/sql/03_ACADEMIC_MASTER/11_faculty_and_grading.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/sql/03_ACADEMIC_MASTER/16_assign_demo_faculty.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/sql/03_ACADEMIC_MASTER/17_clean_schedule_dataset.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/sql/05_VERIFICATION/17_verify_readiness.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/README.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/05_registrar_feature_demo_seed.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/06_registrar_feature_demo_verify.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/admission_uploads/DEMO-SANTOS-001-form138.svg`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/admission_uploads/DEMO-SANTOS-001-good-moral.svg`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/admission_uploads/DEMO-SANTOS-001-psa-birth-cert.svg`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/admission_uploads/DEMO-SANTOS-001-id-picture.svg`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/admission_uploads/DEMO-SANTOS-001-other-doc.svg`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/04_RUNNERS/04_START_REGISTRAR.cmd`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/04_RUNNERS/13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/04_RUNNERS/_RUN_SQL.cmd`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/05_MANIFEST/SOURCE_FILE_MAP.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/05_MANIFEST/SHA256SUMS.txt`

## 6. Why these groups matter

### Code without seeds is not enough

The scheduling hardening and faculty-load logic rely on cleanup and demo setup SQL to make the system demonstrable and consistent.

### Seeds without docs are not enough

The current repo state now includes a specific front-door specification and a demo data loader. Another agent or human will miss critical context if the docs do not land with the code.

### Package mirror files are not optional

The dated package under `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE` is part of the handoff contract. If source files change but the package copies do not, the repo becomes internally inconsistent.

## 7. Validation to run before pushing

At minimum, run these from the current workspace:

```cmd
cmd /c setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

Expected outcome:

- final verifier prints `PASS: Full registrar feature demo dataset is ready`

Recommended additional checks:

```cmd
mvn -q -DskipTests compile
mvn -q "-Dtest=FacultyLoadServiceTest,BlockOfferingServiceTest,ScheduleConflictValidatorTest" test
```

If package-manifest or package file contents changed, regenerate checksums:

```cmd
powershell -ExecutionPolicy Bypass -File handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\05_MANIFEST\GENERATE_CHECKSUMS.ps1
```

## 8. Safe command sequence for the next agent

If the agent is using this exact workspace and is ready to publish:

```cmd
git status -sb
git add README.md db setup src handoffNew
git commit -m "Publish registrar scheduling hardening and demo dataset refresh"
git push registrarCanon codex/registrar-withdrawal-governance
```

Notes:

- `git add README.md db setup src handoffNew` is intentionally broad because the updated system spans all of those trees.
- Run `git status --short` after staging to verify no intended file was missed.
- Do not use interactive git flows.

## 9. Suggested commit strategy

If the agent wants a single clean publish commit on top of the existing ahead commit, that is acceptable.

Suggested message:

- `Publish registrar scheduling hardening and demo dataset refresh`

If the agent prefers two commits, use:

1. `Finalize registrar scheduling hardening and dataset cleanup`
2. `Add full registrar demo dataset and updated handoff specification`

Either way, do not rewrite or drop the existing ahead local commit unless explicitly instructed.

## 10. Post-push checks

After push, confirm:

```cmd
git status -sb
git log --oneline --decorate -n 5
```

Expected:

- branch still `codex/registrar-withdrawal-governance`
- no uncommitted intended publish files left behind
- pushed history includes:
  - the already-ahead local commit
  - the new publish commit(s)

## 11. If the agent is working from a fresh clone

If another agent is not using this workspace, they should:

1. Clone `https://github.com/salazarenriqu3/registrarCanon.git`
2. Checkout `codex/registrar-withdrawal-governance`
3. Apply or copy the exact file groups listed in Section 5
4. Run the validation in Section 7
5. Commit and push back to `registrarCanon/codex/registrar-withdrawal-governance`

## 12. Final caution

This publish is broader than just withdrawal.

If the goal is to have the repo reflect the current working Registrar system shown in this workspace, the push must include:

- code
- tests
- setup SQL
- demo dataset
- handoff docs
- package mirror

Leaving out any of those buckets will publish an incomplete system state.
