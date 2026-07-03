# Handoff Updates — 2026-06-09

**Read this after `START_HERE_NEW_PC_HANDOFF.md`** when continuing development or handing off to another agent/PC.

This document captures **code and doc changes** applied during the June 2026 stabilization closure (term fees UX, finance policy unification, bootstrap hardening). Older files (`MASTER_HANDOFF.md`, pre-closure sections of `CURRENT_STATE_MAP.md`, `BATCH3_*`) describe earlier snapshots — trust this file + `READINESS_CLOSURE_20260608.md` + `START_HERE` for current state.

---

## 1. Term fees admin — simplified import (Registrar)

**Problem:** Too many import buttons (`Prepare`, `Copy templates`, `Import from term`, etc.) confused operators.

**Solution:** Single page with **two zones** only.

| Zone | URL action | Purpose |
|------|------------|---------|
| **1. Global import** | `POST /admin/term-fees/import-global` | Copy **all** program/year/S1|S2 scopes from source term → target term |
| **2. Scoped import & edit** | `POST /admin/term-fees/import-scope` + `POST /save` | One program + year + curriculum slot (S1/S2) |

**Files changed:**
- `registrar/src/main/resources/templates/admin_term_fees.html` — 2-zone layout, readiness card, gaps queue
- `registrar/src/main/java/com/iuims/registrar/finance/TermFeeAdminController.java` — `import-global`, `import-scope`; legacy endpoints redirect to same logic
- `registrar/src/main/java/com/iuims/registrar/finance/TermFeeAdminService.java` — `importFeesGlobal()`, `importFeesScoped()`, `resolvePreviousTermId()`, primary-rate check includes `LEC_FEE_PER_UNIT`

**Enrollment:** `enrollment3/.../TermFeeAdminController.java` — GET redirects to Registrar; POST blocked with flash message.

**UI:** http://localhost:8083/registrar/admin/term-fees?termId=2

---

## 2. Finance & accounting policy — unified admin (Registrar)

**Problem:** Admission fee, downpayment, drop penalties, and installment schedules were split across Registrar Settings, Enrollment Settings, hardcoded constants, and SQL-only tables.

**Solution:** One Registrar page — **Finance & Accounting Policy**.

| Section | Storage | Notes |
|---------|---------|-------|
| Payment gates | `system_settings` | `ADMISSION_MIN_PAYMENT`, `DOWNPAYMENT_THRESHOLD`, `DOWNPAYMENT_PERCENT` (new), `ACCOUNTING_BLOCK_THRESHOLD` |
| Enrollment rules | `enrollment_settings` | max units, drop penalties, session timeout, RLE hours/unit |
| Installment schedule | `term_installment_plan` | Default (NULL term_id) + per-term; copy from default or previous term |

**Files added:**
- `registrar/.../finance/FinancePolicyService.java`
- `registrar/.../finance/FinancePolicyController.java`
- `registrar/src/main/resources/templates/admin_finance_policy.html`

**Files changed:**
- `registrar/.../core/PolicySettings.java` — `DOWNPAYMENT_PERCENT`
- `registrar/.../core/DatabaseSetupService.java` — seed `DOWNPAYMENT_PERCENT`
- `registrar/.../academic/AcademicGradingService.java` — read/save percent (Settings no longer shows finance gates)
- `registrar/src/main/resources/templates/admin_settings.html` — link to Finance Policy; finance fields removed
- `registrar/src/main/resources/templates/fragments/layout.html` — sidebar **Finance Policy**

**Enrollment reads single source:**
- `enrollment3/.../EnrollmentSettingsService.java` — downpayment + admission from `system_settings`; legacy `enrollment_settings` mirror kept on save
- `enrollment3/.../FinancialService.java` — `admissionMinPayment()` from DB (not hardcoded `1000.0`)
- `enrollment3/.../AdminController.java` — applicant walk-in uses dynamic admission min
- `enrollment3/.../EnrollmentSettingsAdminController.java` — redirects to Registrar `/admin/finance-policy`

**UI:** http://localhost:8083/registrar/admin/finance-policy

**Clarification:** Installment **schedule** (this page) ≠ **Installment charge** flat fee (`OTHER_INSTALLMENT` on Program Fees).

---

## 2b. Per-student installment override (Registrar + Enrollment)

**Problem:** Finance Policy section 3 edits the global/term installment schedule only. Cashier applied the same split to every student.

**Solution:** Optional per-student override for the **active term**, edited on **Student Manager**.

| Layer | Storage | UI |
|-------|---------|-----|
| Global default | `term_installment_plan` (`term_id` NULL) | Finance Policy |
| Per term | `term_installment_plan` (`term_id` = N) | Finance Policy |
| **Per student** | `student_installment_plan` (`student_number`, `term_id`) | Student Manager → **Installment Plan Override** |

**Resolution order (cashier):** student rows → term plan → global default.

**Files changed:**
- `registrar/.../finance/FinancePolicyService.java` — `student_installment_plan` DDL, list/save/copy/clear, `resolveInstallmentPlanForStudent()`
- `registrar/.../portal/EnrollmentController.java` — model + POST `save-installments`, `copy-installments-from-term`, `clear-installments`
- `registrar/.../templates/admin_student_manager.html` — override card (right column, below Financial Ledger)
- `registrar/.../templates/admin_finance_policy.html` — link to Student Manager for per-student edits
- `enrollment3/.../EnrollmentSettingsService.java` — `getInstallmentPlan(termId, studentNumber)` + table DDL
- `enrollment3/.../FinancialService.java` — passes student number when loading plan

**UI:**
- Global/term: http://localhost:8083/registrar/admin/finance-policy
- Per student: http://localhost:8083/registrar/admin/student-manager?username=`<student_number>` → **Installment Plan Override**

**UAT quick check:**
1. Open a student on Student Manager → edit installment rows (e.g. 4 rows instead of 3) → **Save student override**.
2. Open Enrollment cashier for that student → installment due amounts should reflect the new row count.
3. **Clear override** → cashier reverts to term/default plan.

Table is created on first page hit (`ensureSchema`); no extra bootstrap SQL required.

---

## 3. Bootstrap hardening

**Problem:** Fresh PC could miss `program_fee_settings`, `enlistment_status`, or term-2 fee rows.

**Solution:** Extended bootstrap chain (15 steps in `.cmd`).

| Step | File | Purpose |
|------|------|---------|
| 2 | `enrollment3/.../01_enlistment_status_schema.sql` | `student_enlistments.enlistment_status` |
| 6 | `registrar/docs/business_logic/schema_migration_001.sql` | Legacy → `program_fee_settings` |
| 9b | `registrar/db/seed_block_offerings.sql` | `block_offerings` parent rows + `class_sections.block_id` links |
| 10 | `registrar/db/seed_irregular_open_sections.sql` | historical seed note for block-era special/open-section demo data |
| 12 | `handoffNew/sql_manual/11_bootstrap_materialize_active_term_fees.sql` | Term 1 S2 fallbacks + global copy → active term 2 |
| 14 | `handoffNew/sql_manual/02_verify_readiness.sql` | Readiness probes |

**Also:** `registrar/db/fix` CREATE TABLE includes `enlistment_status`; `TermFeeAdminController` guards missing tables.

**Authoritative bootstrap:** `registrar/db/run_full_uat_bootstrap.cmd` (drops/recreates `eacdb` first).

**One-off SQL patches** (already folded into bootstrap or used on live DB only):
- `sql_manual/09_seed_term1_s2_exact_fees_bscpe_bsit.sql`
- `sql_manual/10_seed_term2_bsbio_y3_y4_fees.sql`

---

## 2d. Block-first class scheduling (Registrar)

**Problem:** Block sections were created implicitly by seeding many `class_sections` rows with the same `section_code`. Operators had no parent “block” object and could accidentally open block codes from the per-course modal.

**Solution:** `block_offerings` table + **Block Sections** panel on Class Scheduling.

| Concept | Storage | UI |
|---------|---------|-----|
| Block template | `block_offerings` (term, program, year, sem, group, capacity, curriculum) | Class Scheduling → **Create Block** |
| Materialized courses | `class_sections` rows sharing `section_code`, linked via `block_id` | Block card → **Courses** / **Refresh** |
| Special section | legacy open-section rows for summer/tutorial or archived demo cases | Course Sections → **Open Special Section** |

**Flow:**
1. **Create Block** → picks program + Y/S/group → materializes all active-curriculum courses for that slot.
2. Assign faculty and schedules per course in **Course Sections** (expand course row).
3. Per-course **Open New Section** rejects block-pattern codes (`BSIT-1-2-A`); use **Block Sections** instead.

**Files added:**
- `registrar/.../academic/BlockOfferingService.java`
- `registrar/db/seed_block_offerings.sql` (bootstrap step after block section seed)

**Files changed:**
- `registrar/.../portal/AcademicController.java` — `create-block`, `rematerialize-block`; model `blocks`, `programs`
- `registrar/.../academic/AcademicGradingService.java` — block-code guard on `openSection`
- `registrar/.../academic/ClassSection.java` — `block_id`
- `registrar/.../templates/admin_class_scheduling.html` — Block Sections panel above Course Sections

**UI:** http://localhost:8083/registrar/admin/class-scheduling?termId=2

**Live DB patch (no full rebootstrap):**
```text
mysql -u root eacdb < registrar/db/seed_block_offerings.sql
```
Then restart Registrar and open Class Scheduling (auto-syncs any legacy block rows).

---

## 2c. Irregular sections vs block sections (Enrollment + Registrar)

**Rule:** This older note described a retired open-section workflow. The current canon is block-first: irregular/transferee students mix and match across seeded block sections, with special sections reserved only for explicit summer/tutorial exceptions.

| Section pattern | Example | Used by |
|-----------------|---------|---------|
| Block | `BSIT-1-2-A` | Regular → assign group → block enlist |
| Irregular / open | seeded block sections | irregular manual selection across block-owned offerings |

**Enrollment enforcement:**
- `searchIrregularOfferings` excludes block sections (`GradeOutcomeSql.BLOCK_SECTION_CODE_SQL`)
- `POST /admin/enlist-subject` rejects block section codes for irregular staging

**Bootstrap:** `registrar/db/seed_irregular_open_sections.sql` is historical context only; current demo flows should be block-first.

**Registrar UI:** Class Scheduling → **Open New Section** — use non-block codes for irregular offerings (hint on modal).

**Live DB patch (no full rebootstrap):**
```text
mysql -u root eacdb < registrar/db/seed_irregular_open_sections.sql
mysql -u root eacdb < registrar/db/seed_all_class_schedules.sql
```
(second line refreshes schedules for the demo section set)

---

## 4. Active term & fee scope math (post-retirement)

| Item | Value |
|------|-------|
| Active term | `2120242025` (`term_id = 2`) |
| Active programs | **18** (9 soft-retired: BSBA, BSCE, BSCS, BSECE, BSED, BSMATH, ABCOMM, BEED, BSMT) |
| Fee scopes per term | **144** = 18 × 4 years × 2 curriculum slots |

Do **not** use old **248** / **27 program** figures from pre-retirement Batch 3 notes.

### Bootstrap fixes (2026-06-09 validation pass)

- **`11_bootstrap_materialize_active_term_fees.sql` step 3** — copies active-term Y2 fee rows → Y3/Y4 when missing (fixes BSBio Y3–Y4 gaps → **0 unresolved** scopes).
- **`20260608_retire_empty_curriculum_blockers.sql`** — also retires ABCOMM, BEED, BSMT (no curriculum seed) so Settings readiness goes green.

After full bootstrap, term-2 fees should be **green without manual import**. Manual global import is a **fallback** only.

---

## 5. Enrollment admin surfaces (redirects)

| Enrollment sidebar | Behavior |
|--------------------|----------|
| Term Fees | Redirect → Registrar `/admin/term-fees` |
| Finance Policy (was Settings) | Redirect → Registrar `/admin/finance-policy` |
| **Course Fees** | Stays in Enrollment — `/admin/course-fees` (per-subject lab/computer add-ons) |

Enrollment orphan templates removed (`admin_term_fees.html`, `admin_enrollment_settings.html`); controllers still redirect to Registrar.

---

## 6. Config reference

```properties
# enrollment3/src/main/resources/application.properties
registrar.portal-base-url=http://localhost:8083/registrar
```

---

## 7. Validation pass (2026-06-09 execution)

| Step | Result |
|------|--------|
| `run_full_uat_bootstrap.cmd` | PASS |
| Fee readiness (active term) | **0** unresolved scopes |
| Settings readiness | **Ready for operation** |
| Quick preflight (`python _runtime_logs/run_full_preflight.py --quick`) | **7/7 PASS** (after script fixes) |

**Fixes applied during validation:**
- `11_bootstrap` step 3 — BSBio Y3–Y4 fee propagation
- Retire ABCOMM, BEED, BSMT (no curriculum) in `20260608_retire_empty_curriculum_blockers.sql`
- UAT scripts: `term2_edge_verify.py` path, `professor_grading_uat.py` → `prof.cruz`, `cross_app_verify.py` term-fees check, `transferee_shift_uat.py` TRANS-T01 legacy soft-check
- Removed dead `enrollment.fees.default-tuition`; deleted orphan enrollment admin templates

**Preflight prerequisite:** `python -m pip install pymysql`

---

## 8. Smoke test (after restart both apps)

| Check | URL |
|-------|-----|
| Settings readiness | `/admin/settings` → Ready for `2120242025` |
| Program fees | `/admin/term-fees?termId=2` → zones 1 & 2, no missing tables |
| Finance policy | `/admin/finance-policy` → gates + installments editable |
| Per-student installments | `/admin/student-manager?username=…` → Installment Plan Override |
| Enrollment redirect | `/admin/enrollment-settings` → lands on Registrar finance policy |

---

## 8. Doc sync status (this pass)

Updated to match code:
- `START_HERE_NEW_PC_HANDOFF.md` — references this file
- `MASTER_DEMO_UAT_MANUAL.md` — Part 2B = 15 steps; Part 5 finance + term fees; block-first scheduling + special-section exceptions
- `00_full_uat_bootstrap.sql` — full SOURCE chain + verify
- `sql_manual/README.md`, `fee_import/README.md`
- `setup_fresh_pc.ps1` — deprecated → use `.cmd`
- `MASTER_HANDOFF.md`, `CURRENT_STATE_MAP.md` — superseded banners

---

## 9. Still out of scope (unchanged)

- TOR PDF/OCR, course equivalency table, scheduling automation
- Production official fee rates (demo seeds only)
- Registrar Spring Security (Enrollment has it)
- Curriculum CSV **import** (export only)

---

## 11. Fresh setup folder + 2425 1st sem + readable UAT (2026-06-09)

### Single setup folder

All **fresh-PC / demo bootstrap** config lives in **`registrar/setup/`**:

| File | Purpose |
|------|---------|
| **`RUN_FRESH_SETUP.cmd`** | **Canonical one-command bootstrap** |
| `sql/01_activate_term_2425_s1.sql` | Active term **`1120242025`** |
| `sql/02_materialize_term_fees.sql` | Demo fees for term 1; pre-seed term 2 |
| `sql/03_assign_prof_cruz_demo.sql` | One professor for grading demos |
| `sql/04_verify_readiness.sql` | Post-bootstrap SQL checks |
| `fees/*.csv` | Program fee import templates |

Legacy `registrar/db/run_full_uat_bootstrap.cmd` **forwards** to `setup/RUN_FRESH_SETUP.cmd`.

### Active term default

Demo/UAT now starts on **A.Y. 2024–2025, 1st Semester** (`1120242025`, `term_id = 1`), not 2nd sem.

Y1 golden-path block: **`BSCPE-1-1-A`**. Term fees UI: `?termId=1`.

### Human UAT docs

| Doc | Use |
|-----|-----|
| **`HUMAN_UAT_CHECKLIST.md`** | Readable demo/UAT sign-off (Sessions 0–F) |
| **`THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`** | Y1→Y4 + transferee + shift + manual grades |

`MASTER_DEMO_UAT_MANUAL.md` Part 7 remains full reference; checklist is the print-friendly version.

---

## 12. Agent fresh-setup pack (2026-06-09)

| File | Purpose |
|------|---------|
| `registrar/setup/AGENT_FRESH_SETUP.md` | Agent playbook: prereqs → bootstrap → smoke |
| `registrar/setup/BOOTSTRAP_SEED_MANIFEST.md` | Full seed inventory (17 steps) |
| `registrar/setup/CHECK_PREREQUISITES.cmd` | JDK, Maven, MariaDB, project layout |
| `registrar/setup/sql/05_materialize_all_calendar_term_fees.sql` | Fees on **all** calendar terms (transition-ready) |

Bootstrap now seeds block sections, schedules, block offerings, and fees for **every calendar term** 2425–2728, not only the active term.

---

## 13. UI contrast pass (2026-06-10)

Higher-contrast alerts, cards, and page backgrounds (no layout changes).

| App | Files |
|-----|--------|
| Registrar (main admin) | `static/css/theme-eac.css` — `.alert-*`, semantic CSS vars, card borders, gray page bg |
| Registrar (Bootstrap pages) | `static/css/modern-style.css` — alert overrides |
| Enrollment | `static/css/style.css` — `.alert-success/danger/warning/info`, `.notice-card-warning` |
| Enrollment templates | `admin_payment.html`, `admin_walkin_payment.html` — removed faint inline alert styles |

**Visible on:** Settings readiness alerts, admission payment warnings, cashier error/success, forward-balance cards.

Restart apps or hard-refresh (**Ctrl+F5**) to pick up CSS.

---

## 14. Documentation & roadmap sync (2026-06-10)

| Doc | Update |
|-----|--------|
| **`PROJECT_STATUS_AND_ROADMAP.md`** | **NEW** — master status, sidetracks, UAT progress, pending work |
| `PRODUCTION_IMPLEMENTATION_PLAN.md` | Spring Security marked **deferred**; UAT in progress |
| `CURRENT_STATE_MAP.md` | Live overlay → term `1120242025` |
| `MASTER_DEMO_UAT_MANUAL.md` | Part 2B/5/8 aligned to 1st sem default where stale |
| `061026/` | Portable setup + demo manual bundle |

---

## 16. Accounting block threshold fix (2026-06-10)

**Bug:** Finance Policy saved `ACCOUNTING_BLOCK_THRESHOLD` to `system_settings`, but **Enrollment cashier** ignored it — `FinancialService` used a hardcoded **₱100**.

**Symptom:** Changing threshold to e.g. **₱102** had no effect on enlist/block on cashier. UI text always said “below ₱100”. Demo student with **₱99** forwarded (₱100 forward − ₱1 payment) looked “stuck” at 99 vs 100.

**Fix:** `FinancialService.isEnlistmentAccountingBlock()` now reads `EnrollmentSettingsService.accountingBlockThreshold()` (same DB key as Registrar Finance Policy). Cashier/walk-in/account_status templates show `${accountingBlockThreshold}`.

**Registrar** was already correct (`PolicySettings.accountingBlockThreshold`).

**Test:** `enrollment3/.../AccountingBlockThresholdTest.java`

**Restart Enrollment** after pull to pick up the fix.

---

## 17. Overpayment policy — pending carry, disposition at enlist (2026-06-10)

**Agreed direction:** Term close detects overpay → post **`PENDING_TERM_CREDIT`** on next ledger (not auto `FORWARDED_BALANCE` credit). When student returns for next term, cashier/enlist prompts: **refund as cash** or **apply as credit**.

**Docs (61026.2 bundle — overpayment in separate folder):**

- `overpayment/PROPOSAL_OVERPAYMENT_FORWARD_CREDIT_POLICY.md` — business rules, ledger model, UX
- `overpayment/IMPLEMENTATION_PLAN_OVERPAYMENT.md` — **separate** build plan: 4 phases (~3–5 dev days), file checklist, OPAY UAT cases

**Not implemented yet** — build when user approves sprint.

---

## 15. UAT progress & production decisions (2026-06-10)

| Item | Status |
|------|--------|
| User re-test (Sessions 0, A, B areas) | **Positive** — glad with results |
| Sessions C–F full sign-off | **Pending** |
| Registrar Spring Security (Phase 1A) | **Deferred** — see `PROPOSAL_REGISTRAR_SPRING_SECURITY.md` |
| Admission/downpayment pooling | **No change** (intentional) |
| Next work | User has additional inputs; doc baseline synced first |

---

## 18. Registrar overpayment — phase 1 implemented (2026-06-10)

**Scope:** Registrar only. Enrollment cashier advance overpay path still legacy until phase E (see `overpayment/IMPLEMENTATION_PLAN_OVERPAYMENT_ENROLLMENT.md`).

| Area | Change |
|------|--------|
| Term close | `oldBalance < 0` → `PENDING_TERM_CREDIT` (held; not auto `FORWARDED_BALANCE` credit) |
| Assessment | `pending_term_credit` excluded from `total_assessment` |
| Disposition | Student Manager: refund / credit / split via `OverpayDispositionService` |
| Enrollment hub | `canEnroll` gate when pending overpay unresolved |
| DB | `13_student_overpay_dispositions.sql`, `demo_overpay_pending.sql` |
| Test | `OverpayDispositionServiceTest` |

**Before UAT:** Run `13_student_overpay_dispositions.sql`, rebuild/restart Registrar.

---

## 19. Curriculum assignment — proposal & plan (2026-06-10)

**Problem:** Returning students on old catalog were silently backfilled to active default (`LEGACY_BACKFILL`).

**Docs:** `curriculum/PROPOSAL_EXPLICIT_CURRICULUM_ASSIGNMENT.md`, `curriculum/IMPLEMENTATION_PLAN_CURRICULUM_ASSIGNMENT.md` (phases C1–C4).

---

## 20. Curriculum assignment — C1–C4 implemented (2026-06-11)

**Both apps** — deploy order was C1 → C2 → C3; docs/SQL in C4.

| Phase | App | Delivered |
|-------|-----|-----------|
| **C1** | Registrar | Assign Curriculum UI + POST; no `LEGACY_BACKFILL`; read-only resolve at offerings/deficiencies |
| **C2** | Enrollment | No active-catalog fallback; `curriculumUnassigned` banner; finalize blocked |
| **C3** | Enrollment | Block sections use assigned `curriculum_id` |
| **C4** | Docs/SQL | `demo_returning_student_old_curriculum.sql`; RET-CURR UAT rows; unit/smoke tests |

**UAT seed (optional):** `mvn test -Dtest=RetCurrUatSeedTest` → `2026-RET-UNASS` (unassigned), `2026-RET-LEGACY` (legacy assign).

**Human UAT:** RET-CURR-01–04 in `HUMAN_UAT_CHECKLIST.md` — **not signed off** yet.

---

## 21. Bundle 61126 session wrap (2026-06-11)

| Item | Note |
|------|------|
| Handoff bundle | **`61126/`** — snapshot of docs + setup; supersedes `61026.2/` for current status |
| Build fix | `EnrollmentSettingsService` — `accountingBlockThreshold`, `admissionMinPayment`, `getInstallmentPlan(termId, sn)` |
| Runtime | Kill stale JVM on `:8083`/`:8082` before Eclipse start (`taskkill` after `netstat`) |
| Next work | User returns for **RET-CURR browser UAT** + Sessions C–F; Enrollment overpayment optional |

See **`NEXT_AGENT_HANDOFF_20260611.md`** for full agent entry.

---

## 10. For the next handoff agent

1. Start with **`NEXT_AGENT_HANDOFF_20260611.md`** → **`PROJECT_STATUS_AND_ROADMAP.md`** → **`COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`**.
2. Bootstrap only via **`registrar/setup/RUN_FRESH_SETUP.cmd`** (runs prereq check first).
3. Fee amounts → **Program Fees** (`termId=1`); payment policy → **Finance Policy**; CSV fallback → **`registrar/setup/fees/`**.
4. When adding handoff notes, **append to this file** or create `HANDOFF_UPDATES_YYYYMMDD.md` — do not resurrect stale 248-scope / Batch-1-in-progress language.
