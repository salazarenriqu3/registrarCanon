# Business Logic Improvements — Master Reference Document

> **Purpose**: This is a living document. Every business logic improvement, its rationale, its design decisions, and its final implemented state are recorded here. It is intended to serve as the single source of truth for this phase of the project — usable by developers, agents, and reviewers alike.

---

## Document Index

| # | Area | Status | Section |
|---|------|--------|---------|
| 1 | Fee Settings Simplification | ✅ Completed | [Jump →](#phase-6-improvement-1-fee-settings-simplification) |
| 2 | Explicit Term-to-Term Fee Imports | ✅ Completed | [Jump →](#phase-6-improvement-2-explicit-term-to-term-fee-imports) |
| 3 | Fresh Database Verification | ✅ Completed | [Jump →](#phase-6-improvement-3-fresh-database-verification) |
| 4 | Term Fee UX Locks and Scoped Imports | ✅ Completed | [Jump →](#phase-6-improvement-4-term-fee-ux-locks-and-scoped-imports) |
| 5 | Subsystem Scope Reduction (Basic Ed & Payments) | ✅ Completed | [Jump →](#phase-6-improvement-5-subsystem-scope-reduction) |
| 6 | Premium UI Standardization | ✅ Completed | [Jump →](#phase-7-premium-ui-standardization) |

---

---

# REGISTRAR: Curriculum-Based Student Load Policy

**Date Completed**: 2026-06-26

Registrar max-unit checks now resolve from the student's explicit current curriculum assignment. The live rule is:

```text
base max units = sum of credit_units for assigned curriculum_id + current year_level + current semester
effective max units = base max units
graduating effective max units = base max units + 6
```

This applies to enrollment display, scholarship-side load-cap calls, and Jaypee offering/section analysis. The old year-level/global policy table remains historical reference only and must not override live registrar decisions.

Missing student curriculum assignment or missing curriculum rows for the student's current year/semester is a data error. Do not fall back to `max_units_regular`, `max_units_graduating_bonus`, or the year-level load table for live load enforcement.

Registrar manual subject add follows the same assignment rule. In Student Manager and Jaypee-backed offering analysis:

```text
eligible course = assigned curriculum + student's current semester
year-level filter = not required for manual irregular add
must still pass = prerequisite, schedule conflict, section capacity, max-unit checks
not allowed = courses mapped only to other semesters of the assigned curriculum
```

Implementation anchor: `com.iuims.registrar.curriculum.CurriculumLoadPolicyService`.

---

# PHASE 6, IMPROVEMENT 1: Fee Settings Simplification

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

The system previously used two separate tables to store fee configuration:

| Table | Role | Problem |
|---|---|---|
| `program_general_fees` | Wide table for core academic rates (tuition, RLE) | Required complex fallback SQL to find the right row per term |
| `program_specific_fees` | EAV (Entity-Attribute-Value) rows for misc/other fees | Aggregation required a correlated `NOT EXISTS` anti-join; hard to read, hard to update |

**The consequence**: Every service that needed to calculate a student's fees (`ScholarEnrollmentService`, readiness checks, etc.) had to replicate multi-dozen-line raw JDBC queries with nested subqueries. The logic was duplicated, fragile, and untestable.

### User's Original Request
> "I figured since we are setting this fee per term per year per semester, we can unify it to a single table... 0 means it is not required for the program and will not display, value detected means it is required and will be displayed and computed."

---

## 2. Design Decisions

### Decision A — No `studentledger_id` in This Table

**User asked**: Should the ledger ID be tagged in this table?  
**Decision**: No. Approved by user.

**Rationale**:  
This table is a **template**, not a transaction. It defines *rules* (e.g., "All Year 1 BSIT students in Term 10 pay ₱1,500 tuition per unit"). The `student_ledger` is *transactional* — it records what an individual student actually owes.

If ledger IDs were embedded here, every fee setting row would be duplicated per student, completely destroying the template concept and introducing a 1:1 join that is both logically wrong and a database footprint multiplier.

**Correct Flow**:
```
[program_fee_settings] ──(read at enrollment)──▶ [student_ledger] ──(billing)──▶ [student]
```

### Decision B — Wide Table vs. EAV

**User asked**: Is the rigidity of a wide table acceptable?  
**Decision**: Wide table. Approved by user.

**Rationale**:

| Criteria | EAV (old) | Wide Table (new) |
|---|---|---|
| Query complexity | High (correlated anti-join) | Minimal (`SELECT * ... LIMIT 1`) |
| Adding a new fee type | One `INSERT` row | `ALTER TABLE` + code update |
| DB row count | Grows per fee type × per scope | Fixed: one row per scope |
| Type safety | None (all stored as `VARCHAR` or `DECIMAL`) | Explicit `DECIMAL(10,2)` per column |
| Readability | Low | High |

For this system, the university's fee *types* are relatively stable and fixed. The trade-off of requiring a schema migration to add a new fee type is far outweighed by the massive reduction in query complexity and maintenance burden.

**Maximum row estimate**: ~20 programs × 4 year levels × 2 semesters = **160 rows max**. No overload risk.

---

## 3. Final Schema

### `program_fee_settings` (New Unified Table)

```sql
CREATE TABLE program_fee_settings (
    fee_setting_id       INT AUTO_INCREMENT PRIMARY KEY,

    -- Scope / Key
    program_id           INT NOT NULL,
    term_id              INT NULL,          -- NULL = fallback template for all terms
    year_level           INT NULL,          -- 1..4
    semester_number      INT NULL,          -- 1 or 2

    -- Core Per-Unit Academic Rates
    fee_tuition_per_unit  DECIMAL(10,2) DEFAULT 0.00,
    fee_lec_per_unit      DECIMAL(10,2) DEFAULT 0.00,  -- often mirrors tuition
    fee_lab_per_unit      DECIMAL(10,2) DEFAULT 0.00,
    fee_comp_per_unit     DECIMAL(10,2) DEFAULT 0.00,
    fee_rle_per_unit      DECIMAL(10,2) DEFAULT 0.00,  -- BSN/clinical programs only

    -- Flat Miscellaneous Charges
    fee_misc_registration DECIMAL(10,2) DEFAULT 0.00,
    fee_misc_library      DECIMAL(10,2) DEFAULT 0.00,
    fee_misc_medical      DECIMAL(10,2) DEFAULT 0.00,
    fee_misc_id           DECIMAL(10,2) DEFAULT 0.00,
    fee_misc_athletic     DECIMAL(10,2) DEFAULT 0.00,

    -- Other Flat Charges
    fee_other_late_enrollment DECIMAL(10,2) DEFAULT 0.00,
    fee_other_add_drop        DECIMAL(10,2) DEFAULT 0.00,
    fee_other_installment     DECIMAL(10,2) DEFAULT 0.00,

    is_active            TINYINT NOT NULL DEFAULT 1
);
```

**Semantics**:
- A column value of `0.00` means the fee does not apply for this scope and will not be displayed or computed.
- A column value `> 0` means the fee applies and will be shown and computed against the student's enrolled units or as a flat charge.
- `term_id = NULL` creates a **fallback template** — the fee rates that apply when no specific term override exists.
- `term_id = <id>` creates a **term-specific override** — the exact fee rates for that particular academic term.

---

## 4. Fallback Resolution Logic

The system uses a **priority-ordered single query** to resolve fees. The rule is:

> **Exact term match wins over fallback. Most recent entry wins on ties.**

```sql
SELECT * FROM program_fee_settings
WHERE program_id = ?
  AND year_level = ?
  AND semester_number = ?
  AND is_active = 1
  AND (term_id = ? OR term_id IS NULL)
ORDER BY
  CASE WHEN term_id = ? THEN 0 ELSE 1 END,  -- exact term = priority 0, fallback = priority 1
  fee_setting_id DESC                         -- latest entry wins on ties
LIMIT 1
```

### Visual Example

```
Setup:
  Row A: program=BSIT, term=NULL,  year=1, sem=1, tuition=1500  ← fallback
  Row B: program=BSIT, term=10,    year=1, sem=1, tuition=1600  ← term override

Query: "What does BSIT Y1S1 pay in Term 10?"
  → Finds Row B (exact match) → tuition = 1600

Query: "What does BSIT Y1S1 pay in Term 11 (no config yet)?"
  → Finds Row A (fallback)    → tuition = 1500
```

---

## 5. Code Architecture

### 5.1 — `ProgramFeeSetting.java` (JPA Entity)
**Package**: `com.iuims.registrar.finance`  
**File**: `src/main/java/com/iuims/registrar/finance/ProgramFeeSetting.java`

- Maps to `program_fee_settings` table.
- Each fee type is a `BigDecimal` field (precision 10, scale 2).
- Provides `getFee(String feeCode)` and `setFee(String feeCode, double amount)` for generic, code-driven access. This is critical — it allows the service layer to iterate over `KNOWN_FEES` without needing to reflect on individual fields.
- Supports two aliases: `RLE_RATE_PER_HOUR` and `RLE_FEE_PER_UNIT` both map to `feeRlePerUnit` for backward compatibility.

### 5.2 — `ProgramFeeSettingRepository.java` (Spring Data JPA Repository)
**Package**: `com.iuims.registrar.finance`  
**File**: `src/main/java/com/iuims/registrar/finance/ProgramFeeSettingRepository.java`

Two key query methods:

| Method | Purpose |
|---|---|
| `findBestMatch(programId, yearLevel, semester, termId)` | Read path — resolves the best applicable fee setting, exact or fallback. |
| `findActiveForScopeOrFallback(programId, yearLevel, semester, termId)` | Write path — checks before creating a new row to avoid orphan duplicates. |

Both use the same priority-ordered `CASE WHEN` native SQL query (see Section 4).

### 5.3 — `TermFeeAdminService.java` (Refactored)
**Package**: `com.iuims.registrar.finance`  
**File**: `src/main/java/com/iuims/registrar/finance/TermFeeAdminService.java`

**Key public methods**:

| Method | What it does |
|---|---|
| `getFeeRatesForScope(programId, termId, yearLevel, semester)` | Returns `Map<String, Double>` of all fee codes with non-zero values for the scope |
| `getFeeRateSourcesForScope(...)` | Same, but returns `Map<String, String>` indicating `"EXACT_TERM"` or `"FALLBACK"` per code — used by the admin UI to display fallback warnings |
| `saveFeeRate(programId, termId, yearLevel, semester, feeCode, amount)` | Upserts a fee value. If no exact-term row exists, creates one (copying from fallback if present). If `TUITION_PER_UNIT` is set and `LEC_FEE_PER_UNIT` is 0, it auto-mirrors the tuition value to lec. |
| `prepareTermFees(termId)` | For all programs and scopes, seeds exact-term rows from fallback templates if none exist. Admin-triggered. |
| `copyImportedProgramFeeTemplates(termId)` | Seeds fee settings for "derived" programs (e.g., BSBAFIN copies from BSBA) using a defined source map. |
| `buildTermReadinessSummary(termId)` | Produces a full diagnostic of which scopes are missing fees, which are on fallback, and whether the term is ready. |

**Static fee code registry** (`KNOWN_FEES` list):
```
TUITION_PER_UNIT, LEC_FEE_PER_UNIT, LAB_FEE_PER_UNIT, COMP_FEE_PER_UNIT, RLE_FEE_PER_UNIT,
MISC_REGISTRATION, MISC_LIBRARY, MISC_MEDICAL, MISC_ID, MISC_ATHLETIC,
OTHER_LATE_ENROLLMENT, OTHER_ADD_DROP, OTHER_INSTALLMENT
```

### 5.4 — `ScholarEnrollmentService.java` (Refactored)
**Package**: `com.iuims.registrar.scholarship`  
**File**: `src/main/java/com/iuims/registrar/scholarship/ScholarEnrollmentService.java`

**Before**: Contained two private methods (`coreTuitionRate`, `coreFeeSum`) that executed their own raw JDBC queries against the old split tables. This duplicated the fallback logic from `TermFeeAdminService` and violated the encapsulation boundary.

**After**: Both methods now delegate to `TermFeeAdminService.getFeeRatesForScope(...)`.

```java
// Before (duplicated 50+ lines of raw JDBC):
private double coreTuitionRate(Integer programId, ...) { /* raw SQL */ }

// After (single delegating call):
private double coreTuitionRate(Integer programId, Integer termId, int yearLevel, int semester) {
    Map<String, Double> rates = termFeeAdminService.getFeeRatesForScope(programId, termId, yearLevel, semester);
    return rates.getOrDefault("TUITION_PER_UNIT", 0.0);
}
```

`TermFeeAdminService` is injected via **constructor injection** to comply with Spring Modulith's architectural boundary rules (no field injection across module boundaries).

### 5.5 — `TermFeeAdminController.java` (Unchanged)
The HTTP controller was not modified. It continues to receive the same `Map<String, Double>` and `Map<String, String>` structures from the service layer, meaning there is **zero frontend breakage** from this backend overhaul.

---

## 6. Old Tables — Migration & Deprecation

The old `program_general_fees` and `program_specific_fees` tables are **no longer read or written to** by any service. They remain in the database for now (data migration buffer period) but are considered deprecated.

> **Action Required (Future)**: Once data from the old tables has been migrated to `program_fee_settings` and verified, a DBA should drop `program_general_fees` and `program_specific_fees`.

Data migration script needed:
```sql
-- Migrate core general fees into program_fee_settings
INSERT INTO program_fee_settings
    (program_id, term_id, year_level, semester_number,
     fee_tuition_per_unit, fee_rle_per_unit, is_active)
SELECT
    program_id, term_id, year_level, semester_number,
    tuition_per_unit, rle_fee_per_unit, is_active
FROM program_general_fees
WHERE is_active = 1
ON DUPLICATE KEY UPDATE
    fee_tuition_per_unit = VALUES(fee_tuition_per_unit),
    fee_rle_per_unit     = VALUES(fee_rle_per_unit);

-- Migrate specific fees (MISC group) — example for known codes
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON pfs.program_id = psf.program_id
   AND pfs.year_level = psf.year_level
   AND pfs.semester_number = psf.semester_number
   AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
SET
    pfs.fee_misc_registration = IF(psf.fee_code = 'REGISTRATION', psf.amount, pfs.fee_misc_registration),
    pfs.fee_misc_library      = IF(psf.fee_code = 'LIBRARY',      psf.amount, pfs.fee_misc_library)
    -- ... etc
WHERE psf.is_active = 1;
```

---

## 7. Test Coverage

### `TermFeeAdminServiceTest`
**Class**: `com.iuims.registrar.finance.TermFeeAdminServiceTest`  
**Type**: `@DataJpaTest` (H2 in-memory, MySQL mode)

| Test | Verifies |
|---|---|
| `saveFeeRate_createsNewFallbackSetting` | A save to a new scope creates the row and reads back correctly |
| `exactTermFeeWinsOverFallback` | Exact term row is returned over fallback when both exist |
| `fallbackFeeSourcesCorrectly` | Source map returns `"FALLBACK"` when no exact term row exists |

### `AcademicGradingServiceTermTransitionTest`
Updated to use `program_fee_settings` schema in H2 setup — previously seeded `program_general_fees` directly.

### `GradeOutcomeSemanticsTest`
Updated constructor call to include the new `TermFeeAdminService` argument.

### `ModulithTests`
Passes fully — verifies there are no circular dependencies and no field injection across module boundaries.

---

## 8. Lessons Learned / Design Notes for Future Agents

1. **The `KNOWN_FEES` list is the registry.** Any code that iterates over fee types uses this constant. If a new fee column is ever added to the DB schema, `KNOWN_FEES` in `TermFeeAdminService` must be updated, as must `getFee()`/`setFee()` in `ProgramFeeSetting`, and the DDL.

2. **Do not add fee queries in other services.** All fee reads must go through `TermFeeAdminService.getFeeRatesForScope()`. Any service that directly queries `program_fee_settings` is violating the encapsulation and will duplicate fallback logic.

3. **Fallback (term_id = NULL) is the template. Exact rows override it.** Never delete fallback rows — they are the base. Override rows are created per term as admin prepares each semester.

4. **`saveFeeRate` has a side effect**: if `TUITION_PER_UNIT` is being set and `LEC_FEE_PER_UNIT` is still zero, it auto-mirrors the tuition value to the lecture fee. This is intentional — most programs bill the same rate for both, and admin should only need to override lecture if it differs.

---

*— End of Phase 6, Improvement 1 —*

---

---

> **For future improvements**: Append a new top-level section following the same structure as Improvement 1. Update the Document Index table at the top.

---

# PHASE 6, IMPROVEMENT 2: Explicit Term-to-Term Fee Imports

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

Administrators needed a way to explicitly automate the process of copying fee configurations from a previous academic term to a new term. This eliminates manual data entry while giving the administrator complete control over *when* the import happens and *which* term serves as the source.

## 2. Design Decisions

Because of the migration to the unified wide-table architecture (`program_fee_settings`), the database intrinsically acts as a backup. Every fee template is hard-linked to a specific `term_id`. When a new term starts, the old term's fees are safely preserved.

We simply needed to provide an interface that reads from the `sourceTermId` and duplicates those rows into the `targetTermId`.

## 3. Implementation

- **Data Access**: Added `findByTermIdAndIsActiveTrue` and `findByProgramIdAndYearLevelAndSemesterNumberAndTermId` to `ProgramFeeSettingRepository.java`.
- **Business Logic**: Implemented `importFeesFromSpecificTerm` in `TermFeeAdminService.java` which iterates through all rows of the source term, checks for existing target rows, and either creates or updates them using the `KNOWN_FEES` registry.
- **Controller Endpoint**: Exposed `@PostMapping("/import-from-term")` in `TermFeeAdminController.java`.

---

# PHASE 6, IMPROVEMENT 3: Fresh Database Verification

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

To rigorously test the new `program_fee_settings` business logic and prove that the application no longer has any hidden dependencies on the deprecated legacy EAV tables, we needed to run the system on a fresh database.

## 2. Execution

1. **Schema Cloning & Eradication**: Exported the schema of the `eacdb` database. Manually dropped `program_general_fees` and `program_specific_fees` from the schema.
2. **Fresh DB Creation**: Created a new `eacdb_fresh` database and imported the purified schema along with lookup tables (`programs`, `academic_terms`).
3. **Integration Testing**: Created a Spring Boot Integration Test (`TermFeeFreshDatabaseIntegrationTest.java`) explicitly wired to `eacdb_fresh` instead of the default H2 database.
4. **Validation**: The test successfully seeded fallback templates, prepared a term, and executed an explicit term-to-term import, passing with zero legacy table dependencies.

---

# PHASE 6, IMPROVEMENT 4: Term Fee UX Locks and Scoped Imports

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

Two usability issues were identified after completing the initial Explicit Term Imports:
1. **Contradictory State Risk**: Admins could select an Academic Term (e.g., "A.Y. 2025-2026 2nd Semester") from a dropdown, but could simultaneously select an opposing Semester (e.g., "1st") from a separate dropdown. This created illogical "1st semester subjects in a 2nd semester calendar term" mapping configurations.
2. **Import Granularity**: The explicit term import was a blunt "all-or-nothing" instrument. Admins needed the ability to surgically import fees for *just* a specific Program/Year, while retaining the ability to bulk-import the entire university.

## 2. Implementation

### 2.1 Dynamic Semester Toggle
- **UI Logic**: We modified `admin_term_fees.html`. The `<select>` options for Academic Terms were injected with a `data-semester` HTML5 attribute.
- **Lock Mechanism**: Using vanilla JS, when a specific calendar term is selected, the Semester dropdown instantly snaps to match the actual semester of the term, and the `disabled` property is applied. 
- **Global Fallback Unlocking**: We explicitly added a `-- Global Fallback Templates --` option (representing `term_id = -1`). Selecting this instantly unlocks the Semester toggle, acknowledging that Global Fallbacks are calendar-agnostic.

### 2.2 Scoped vs. Bulk Imports
- **Backend**: We added a scoped query to `ProgramFeeSettingRepository` (`findByTermIdAndProgramIdAndYearLevelAndSemesterNumberAndIsActiveTrue`) and refactored `TermFeeAdminService.importFeesFromSpecificTerm` to conditionally use this targeted query if `programId`, `yearLevel`, and `semester` are provided.
- **Frontend Override**: The UI "Import from Source" button now defaults to a targeted import (using whatever filters the admin currently has active on the screen). We added a checkbox `[x] Bulk import for ALL programs & years`. If checked, the HTML form injects `importScope=all`, signaling the controller to pass `null` for the scoping variables, triggering the classic full-university mass import override.

---

# PHASE 6, IMPROVEMENT 5: Subsystem Scope Reduction

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

The Registrar subsystem previously contained dead code and UI elements for features that are no longer handled by this domain:
1. **Basic Education**: High School, Junior High, and Senior High program codes were cluttering the rosters and course offerings. This system only manages College students.
2. **Payments**: The Registrar dashboard was capturing internal payment flows, which have now been deprecated in favor of an external Open Enrollment / Cashier Gateway.

## 2. Implementation

### 2.1 Basic Education Filtering
- **Backend Logic**: Updated `JaypeeIntegrationService.java` and `AcademicGradingService.java` to aggressively filter out all basic education records. Added explicit WHERE clauses `p.program_code NOT IN ('HS', 'JHS', 'SHS')` to all native queries and JPA filters.
- **UI Impact**: Removed Basic Ed options from dropdowns in `admin_classes.html` and `dashboard.html`. The roster now exclusively displays college programs, courses, and classes.

### 2.2 Payment Gateway Delegation
- **Deprecation**: Stripped the internal payment capture views and logics. The Registrar subsystem's responsibility is strictly to **set** fees (`/admin/term-fees`), not collect them.
- **External Redirect**: Rewired the `admin_student_manager.html` to direct financial transactions via an `Open Enrollment System (External)` button, pointing seamlessly to the external gateway URL configured in the backend model.

---

# PHASE 7: Premium UI Standardization

**Date Completed**: 2026-06-05  
**Author**: Antigravity (AI Coding Agent) + User (sune)  
**Status**: `COMPLETED — APPROVED`

---

## 1. Problem Statement

Following the massive `theme-eac.css` layout update on the Dashboard and Term Fees pages, several legacy pages remained stuck in an older design era. They lacked sidebars, used outdated Bootstrap tables, and suffered from nested `<head>` tags breaking the layout wrappers.

## 2. Execution

We meticulously migrated all remaining pages to strictly adhere to the new premium `.app > .body-wrapper > .main` architecture:

1. **User Management (`/admin/users`)**: 
   - Replaced legacy Bootstrap modals with native HTML5 `<dialog>` tags.
   - Restyled the System Users table with `table-eac` spacing, modern pill badges, and aligned action buttons.
   - Fixed a strict SpEL typing issue where `u.is_active` (Integer) threw 500 errors when evaluated as a raw boolean.
2. **Classes (`/admin/classes`)**: 
   - Refactored the Curriculum Offerings view. Added sleek filter pills and properly formatted the search bar to match the new visual identity.
3. **Legacy Template Repairs**: 
   - Pages like `admin_settings.html`, `admin_scholarships.html`, `admin_approvals.html`, and `admin_admission_acceptance.html` were repaired by replacing malformed nested `<th:block th:replace="~{fragments/layout :: head}">` injections with the proper root `<head th:replace="~{fragments/layout :: head}">` inclusion.
   - Fixed the `class_schedules` backend entity mapping bug that previously crashed the Approvals page.

The entire Registrar portal now boasts a cohesive, high-end, premium aesthetic across all routes.
