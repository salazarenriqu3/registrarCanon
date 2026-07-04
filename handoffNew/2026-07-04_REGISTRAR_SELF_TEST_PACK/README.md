# Registrar Self-Test Pack

This is the single launch pack for a human-led registrar test pass on the current canon.

Use this when you want the registrar feature inventory, the demo data inventory, and the exact run order in one place before opening the browser.

## What This Pack Covers

This pack is registrar-only. It assumes Admission and Enrollment remain separate systems and only influence Registrar through the shared database and shared student identity contract.

Registrar feature areas currently in scope:

1. Curriculum lifecycle and current/legacy/draft/archived management.
2. Course Catalog and where-used visibility.
3. Course component handling for lecture/lab pairs.
4. Program, course, section, schedule, room, and slot monitoring.
5. Student Profile, student identity, and editable registrar records.
6. Applicant snapshot reading and applicant document viewing from the registrar side.
7. Registration Form history and registration-form PDF generation.
8. Withdrawal governance, including single-subject drop, full-school withdrawal, archive custody, and student-number release.
9. Program shift, including pre-enrollment shift and post-enrollment cleanup.
10. Grade approvals, grade storage, and grade-record event history.
11. Academic scholarship evaluation based on official grades and curriculum load.
12. Accreditation / transfer-credit review and registrar approval.
13. Finance read surfaces, overpayment disposition, and ledger visibility.
14. Audit trail and document trail.
15. TOR / transfer-record reporting.

### What Is Influenced By The Other Systems

- Admission influences registrar applicant snapshots, applicant qualification state, and the document set that Registrar can read later.
- Enrollment influences registrar student-number issuance, official enrollment finalization, cashier-ledger state, and assessment status.
- Registrar remains the authority for curriculum, schedule, records, approval, custody, withdrawal governance, scholarship rules, and official academic posting.

## Demo Data Inventory

The registrar demo seed already includes the canonical personas and supporting rows needed for the current flows:

- `2026-1001` - baseline admitted and enrolled student tied to an admission reference.
- `ADDCLS-2026-001` - add-class / irregular-load student for manual subject add tests.
- `TTRNS-2026-001` - transferee / accreditation student for TOR and crediting tests.
- `TSHFT-2026-001` - program-shift student for pre-enrollment and post-enrollment shift tests.
- `OVRPAY-2026-001` - overpayment student for finance disposition tests.
- `SPRINT-DEMO-2026-001` - withdrawal demo student for archive and release tests.
- `DEMO-SANTOS-001` - linked admission applicant reference for the main applicant snapshot path.
- `3SYS-SMOKE-0001` - basic cross-system smoke applicant from the compatibility pack.

Supporting demo data is already included for:

- current-term curriculum assignments
- committed current-term enlistments
- official grade rows for scholarship and TOR checks
- ledger rows for tuition, misc, payment, and overpay scenarios
- registration-form event history
- roomed schedule rows for the scheduling demo
- lecture/lab component split coverage

## Fresh Terminal Start Order

From a brand-new terminal:

```cmd
cd /d E:\registrarCanon_canon
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

Then apply the shared three-system compatibility pack if you are testing Registrar together with Admission and Enrollment:

```cmd
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\00_preflight_shared_contract.sql
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\01_apply_required_demo_contracts.sql
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\02_seed_minimum_cross_system_smoke_data.sql
mysql -uroot eacdb < E:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\sql\03_verify_three_system_demo_ready.sql
```

Then start the apps in this order:

```cmd
cd /d E:\registrarCanon_canon
mvn spring-boot:run
```

```cmd
cd /d E:\EnrollLatest\enrollment3
mvn spring-boot:run
```

```cmd
cd /d E:\AdmitLatest\admission
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## App URLs

- Registrar: `http://localhost:8083/registrar`
- Enrollment: `http://localhost:8082`
- Admission: `http://localhost:8081/`

## Login Accounts

- Registrar admin: `admin` / `1234`
- Registrar records: `registrar.records` / `1234`
- Registrar scholar: `registrar.scholar` / `1234`
- Registrar schedule: `registrar.schedule` / `1234`
- Enrollment admin: `admin` / `admin123`
- Admission admin: `admin-adms` / `adminadms`

## What To Test First

1. Login and startup readiness on all three apps.
2. Admission applicant snapshot visibility in Registrar.
3. Enrollment finalization and student-number issuance.
4. Student Profile read/write behavior.
5. Document Trail and archive custody tracking.
6. Course Catalog where-used and curriculum lifecycle screens.
7. Scheduling room and overlap enforcement.
8. Program shift before and after enrollment.
9. Withdrawal and release behavior.
10. Grade records and academic scholarship.
11. Accreditation and transfer-credit review.

## Expected Registrar Behavior

- Registrar should not revive the retired dean/admission bridge.
- Withdrawn students remain historical records and should not behave like active students.
- Released student numbers should be searchable through the archive key path, not by reopening the old live identity.
- Lecture/lab courses should behave as separate enlistable component rows when the curriculum uses a split family.
- Academic scholarship should read official grades and curriculum load, not a live grading system.

## Existing Seed Source

The main full demo dataset already lives here:

```text
E:\registrarCanon_canon\setup\sql\07_seed_registrar_feature_demo.sql
```

If you need to rerun only the registrar feature dataset on an existing demo database, the safe entry point is still:

```cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
```

## Notes

- This pack is for manual verification and demo rehearsal, not for replacing the canonical source files.
- If a page still shows an unexpected state after loading the seed, refresh the browser after a clean app restart.
- If you want the ledger, shift, withdrawal, and scholarship flows to stay aligned, keep using the current canon repo and the same shared `eacdb` database.
