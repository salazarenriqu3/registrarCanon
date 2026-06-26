# Registrar System Specification

Date: 2026-06-25
Workspace: `D:\registrarCanon_canon`
Application URL: `http://localhost:8083/registrar`
Database: `eacdb`

## 1. Purpose

This document is the current front-door specification for the Registrar system.

It is meant to give a human or agent enough context to answer four questions quickly:

1. What this Registrar system is responsible for.
2. How its major workflows and modules fit together.
3. What is intentionally out of scope or dormant.
4. What constraints must be preserved when changing it.

Use this as the orientation document before editing code, data, or demo flows.

## 2. Current system position

The current Registrar build is:

- ready for a controlled demo and continued UAT
- actively used as the academic-system-of-record side of the local IUIMS/CAPSS stack
- not approved for production deployment

Production is still blocked by security hardening, operational maturity, official business data, and incomplete cross-app sign-off.

## 3. Core mission and business scope

Registrar is the canonical owner of:

- programs, courses, curricula, academic terms
- class sections, block offerings, open sections, schedules, rooms, faculty assignment
- student academic profile and curriculum assignment
- transfer crediting and program shifting
- grades, grade changes, grading windows, academic records
- direct withdrawal execution and registrar-side academic audit trail
- scholarship review and posting workflow
- registrar-facing fee configuration and term readiness

Registrar is not the canonical owner of:

- applicant intake and admission decision
- cashier payment capture
- payment-triggered enrollment finalization
- student-number issuance after payment
- production financial policy sign-off

In practice, Registrar owns the academic structures and some policy/configuration, while Enrollment/Cashier consumes those structures to assess, collect payment, and finalize official committed enrollment.

## 4. Scope boundaries

These boundaries are non-negotiable unless the user explicitly reopens them.

### In scope

- academic master data management
- registrar-side student academic operations
- schedule integrity and faculty load enforcement
- grade encoding and registrar review surfaces
- read-only admission visibility from registrar
- registrar-side document trail and registration-form history

### Out of scope

- resurrecting the retired dean irregular new-enrollee advising bridge
- moving regular applicant pre-registration into Registrar
- moving payment collection into Registrar
- treating `STAGED` enlistments as official enrollment
- restoring legacy fee-fallback shortcuts for live assessment
- automatic schedule optimization/generation
- OCR/TOR automation or production transcript digitization

### Dormant but still present in code

- `FacultyIrregularAdvisingController` and related dean pre-reg snapshot routes still exist, but the controller hard-flags the flow as dormant and redirects away from it. Treat it as historical code, not live canon.

## 5. Supported actors and role model

The current app uses Spring Security plus an older session `currentUser` bridge for some legacy flows.

Primary roles:

- `ADMIN`
- `REGISTRAR`
- `FACULTY`
- `DEAN`
- `STUDENT`
- `ADMISSION` for selected admission-facing routes

High-level route boundaries:

- `/admin/**` is primarily `ADMIN` and `REGISTRAR`
- `/grades/**` is `FACULTY`, `DEAN`, `ADMIN`, `REGISTRAR`
- `/faculty/**` exists, but the live dean advising flow is dormant
- `/enrollment`, `/my-grades`, `/my-load`, `/student/**` are student-facing

Important reality: the app has role separation, but the security model is still demo-grade, not production-grade.

## 6. Runtime architecture

### Components

| Component | Role |
|---|---|
| Registrar app | Spring Boot app on port `8083`, context `/registrar` |
| Enrollment app | Separate app on port `8082` |
| Admission app | External or separate source system; Registrar consumes its data indirectly |
| Shared database | `eacdb` on local MySQL/MariaDB |

### Integration model

The applications mostly integrate through shared database tables, not a versioned service API.

That means:

- schema changes are high-risk across app boundaries
- a compile-clean Registrar change can still break Enrollment or Admission-linked flows
- term identity, student identity, and fee scope must remain aligned across systems

## 7. Module map

Top-level code packages under [src/main/java/com/iuims/registrar](D:\registrarCanon_canon\src\main\java\com\iuims\registrar):

| Package | Responsibility |
|---|---|
| `academic` | grading, class info, scheduling validation, slot monitoring, block offerings |
| `admission` | read-only applicant bridge, document resolution, status sync helpers |
| `config` | security and app configuration |
| `core` | setup, term utilities, policy helpers, student profile helpers |
| `curriculum` | program builder, course catalog, curriculum builder, student curriculum assignment |
| `faculty` | faculty load calculations, faculty-load audit, dormant advising bridge |
| `finance` | term fee admin, finance policy, overpayment disposition |
| `forms` | reg-form events and unified document trail |
| `jaypee` | cross-system student load and subject-enrollment integration layer |
| `portal` | main controllers for admin, student, grading, and profile surfaces |
| `scholarship` | scholarship workflow and related financial effects |
| `security` | user-details service and session bridge |
| `withdrawal` | direct withdrawal execution, policy enforcement, and audit history |

## 8. Main UI surfaces

Primary Thymeleaf templates live in [src/main/resources/templates](D:\registrarCanon_canon\src\main\resources\templates).

Current important surfaces:

- `admin_programs.html`
- `admin_course_catalog.html`
- `admin_curriculum.html`
- `admin_class_scheduling.html`
- `admin_slot_monitoring.html`
- `admin_settings.html`
- `admin_term_fees.html`
- `admin_finance_policy.html`
- `admin_student_manager.html`
- `admin_enrollment.html`
- `admin_document_trail.html`
- `admin_reg_form_history.html`
- `admin_scholarships.html`
- `admin_faculty_load.html`
- `admin_approvals.html`
- `grades_menu.html`
- `grades_sheet.html`
- `print_cor.html`
- `print_cog.html`
- `print_tor.html`
- `student_finance.html`
- `student_grades.html`
- `student_cor.html`

## 9. Canonical business chain

The academic chain should be understood in this order:

1. Program Builder defines the program master.
2. Course Catalog defines reusable course records.
3. Curriculum Builder maps courses into program/year/semester structure.
4. Academic Terms define the active scheduling and fee context.
5. Block offerings and open sections materialize class sections from curriculum.
6. Class Scheduling assigns days, times, rooms, and faculty.
7. Slot Monitoring tracks capacity and committed occupancy.
8. Enrollment/Cashier stages or commits students against Registrar-owned sections.
9. Student Profile, grading, records, withdrawal, and printing operate on the resulting academic state.

If a change breaks an upstream layer, downstream layers become untrustworthy.

## 10. Core data contracts

These are the most important live contracts in the system:

| Contract | Meaning |
|---|---|
| `system_settings.CURRENT_ACADEMIC_TERM` | Current active term code |
| `academic_terms.term_id = 1` | Current demo term id |
| `1120242025` | Current demo term code |
| `student_number` | Cross-flow student transaction identity |
| `reference_number` | Pre-student-number applicant identity |
| `student_enlistments.enlistment_status` | `STAGED` is provisional, `COMMITTED` is official |
| `program_fee_settings` exact scope | official fee source by term + program + year + semester |
| `student_curriculum_assignments.is_current = 1` | explicit current curriculum assignment |
| assigned curriculum term load | live max-unit source for enrollment, scholarship load checks, and offering analysis |
| `student_document_events` + related sources | audit trail backbone |

Never casually weaken or reinterpret these contracts.

## 11. Current functional capabilities

### 11.1 Program Builder

Current owner files:

- [ProgramController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\ProgramController.java)
- [ProgramService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\ProgramService.java)

Purpose:

- maintain registrar-owned master program records
- manage program code, name, department ownership, duration, and active status
- inspect whether a program is in use before destructive changes

### 11.2 Course Catalog

Current owner files:

- [CourseCatalogController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\CourseCatalogController.java)
- [CourseCatalogService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\CourseCatalogService.java)

Purpose:

- manage shared course records
- preserve lecture/laboratory unit breakdown
- expose course usage before edits or retirement

### 11.3 Curriculum Builder

Current owner file:

- [CurriculumController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\CurriculumController.java)

Purpose:

- maintain curriculum versions per program
- support inactive draft creation, clone, finalize, upload/preview/export
- support explicit curriculum assignment to students

Key behavior:

- curricula are not inferred silently for important registrar operations
- explicit assignment is preferred and must be preserved
- live student max units are computed from the student's assigned curriculum, current year level, and current semester
- missing assignment or missing curriculum rows are data errors, not reasons to fall back to legacy global caps
- graduating students keep the explicit `+6` overload allowance on top of their curriculum term load

### 11.4 Class Scheduling and block offerings

Current owner files:

- [AcademicController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\AcademicController.java)
- [BlockOfferingService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\academic\BlockOfferingService.java)
- [ScheduleConflictValidator.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\academic\ScheduleConflictValidator.java)

Purpose:

- materialize block sections from curricula
- create open `IRREG-A` style sections
- assign schedules and faculty
- provide filters and conflict previews

Current hardening rules:

- same-section overlap is blocked
- room conflicts within the same term are blocked
- faculty schedule conflicts within the same term are blocked
- faculty max-load cap is enforced before assignment
- new schedule saves require a concrete active room; historical missing-room rows are monitored as exceptions
- inactive terms should not keep stale seeded faculty assignments
- if older data already violates those rules, the Class Scheduling warning banner exposes a registrar repair action to normalize the term before further scheduling

Important nuance:

- the fresh demo database now seeds concrete rooms for production/demo review
- Room Monitoring is the registrar surface for room inventory, utilization, conflict rooms, and historical missing-room exceptions

### 11.5 Slot Monitoring

Current owner file:

- [SlotMonitoringService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\academic\SlotMonitoringService.java)

Purpose:

- show committed count, staged count, max capacity, and remaining slots
- allow capacity edits
- close individual or bulk sections using current canon rules

### 11.6 Faculty load and schedule integrity audit

Current owner files:

- [FacultyLoadService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\faculty\FacultyLoadService.java)
- [FacultyLoadController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\faculty\FacultyLoadController.java)

Purpose:

- calculate faculty load by effective units
- detect overload risk before assignment
- expose department/all-faculty summaries
- flag suspicious term-wide assignment concentration
- provide a guarded repair action when data looks corrupted

This is not just a reporting feature. It is part of the live scheduling integrity model.

### 11.7 Student Profile and registrar-side academic operations

Current owner file:

- [EnrollmentController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\EnrollmentController.java)

Purpose:

- search and open student profiles
- edit registrar-owned profile data
- show current load, curriculum, deficiencies, academic history, finance summary, ledger
- manage add-subject, transfer credit, program shift, curriculum assignment, withdrawal actions, installment overrides, and overpayment disposition

Important live behavior:

- manual subject-add should use open sections for irregular workflows, not block sections
- transfer-credit requests, approvals, rejections, and final postings are expected to leave clear audit artifacts
- direct subject removal from Student Profile now executes through the registrar withdrawal flow and is fully audited

### 11.8 Admission bridge and applicant document viewing

Current owner files:

- [ApplicantDocumentReadService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\admission\ApplicantDocumentReadService.java)
- [AdmissionController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\admission\AdmissionController.java)
- [EnrollmentController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\EnrollmentController.java)

Purpose:

- show admission snapshot data on registrar-side student profile
- list applicant documents
- allow inline viewing of linked applicant uploads

Boundary:

- this is a read-only visibility bridge for registrar use
- registrar does not become the owner of applicant documents or admission decisions

Upload resolution:

- the app resolves applicant file paths relative to `APP_UPLOAD_DIR` or the configured admission upload root
- the current demo startup helper sets this automatically for the seeded demo package

### 11.9 Registration forms, TOR, COG, and printing

Current owner file:

- [EnrollmentController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\EnrollmentController.java)

Purpose:

- print registration form, COG, and TOR
- show reg-form history
- create document trail events for print actions

Compatibility note:

- legacy route naming such as `/admin/print-cor` remains for compatibility even when the visible label is "Registration Form"

### 11.10 Unified document trail and reg-form history

Current owner files:

- [StudentDocumentTrailService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\forms\StudentDocumentTrailService.java)
- [RegFormEventService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\forms\RegFormEventService.java)

Purpose:

- keep a unified registrar-facing audit trail
- provide a focused reg-form event history and a wider document trail

Document trail sources currently unified:

- `student_document_events`
- `student_reg_form_events`
- `eac_application_logs`
- `student_withdrawal_requests`
- `grade_change_requests`

This is a key part of the system's explainability during demo and UAT.

### 11.11 Withdrawal workflow

Current owner files:

- [WithdrawalController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\withdrawal\WithdrawalController.java)
- [WithdrawalService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\withdrawal\WithdrawalService.java)

Purpose:

- execute registrar withdrawals immediately from Student Profile
- enforce timing/penalty policy
- archive the completed action and update trails and reg-form history

Key business rules:

- withdrawal reasons are controlled
- charge percent is computed from days enrolled and policy settings
- withdrawals can be blocked after the midterm deadline
- direct registrar processing records the completed action, request line archive, and audit output

Important scope point:

- this is the live registrar withdrawal workflow
- there is no higher-authority approval step in the active UI
- it is distinct from the retired dean irregular advising bridge

### 11.12 Scholarship workflow

Current owner files:

- [ScholarshipController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\scholarship\ScholarshipController.java)
- [ScholarEnrollmentService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\scholarship\ScholarEnrollmentService.java)

Purpose:

- manage academic scholarship policy
- evaluate student eligibility from official seeded/imported grade rows
- support `PENDING -> APPROVED -> POSTED`
- allow reject and revoke paths

Current important rule:

- Registrar grants academic scholarship only; manual/non-academic scholarship types are retired from the registrar UI.
- the selected term controls candidate evaluation, while the scholarship policy values remain global registrar settings for now
- scholarship financial effect starts at `POSTED`, not merely `APPROVED`
- `POSTED` writes the canonical student scholarship fields consumed by finance; `APPROVED` is review-only

### 11.13 Finance policy, fees, and readiness

Current owner files:

- [TermFeeAdminController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminController.java)
- [TermFeeAdminService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminService.java)
- [FinancePolicyController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\FinancePolicyController.java)
- [FinancePolicyService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\FinancePolicyService.java)

Purpose:

- maintain exact term fee settings by program/year/semester
- prepare or import term fee data
- expose readiness summaries and exports
- manage load gates and installment policy configuration

Boundary:

- Registrar configures fee structures and related academic policy surfaces
- Enrollment/Cashier still owns payment capture and transactional posting

### 11.14 Faculty grading and approvals

Current owner files:

- [AcademicController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\AcademicController.java)
- [AcademicGradingService.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\academic\AcademicGradingService.java)

Purpose:

- faculty class list and grade sheets
- autosave and submission
- grade change requests
- VPAA/deadline extension requests
- admin approval/review surfaces

Current rule:

- grading operates on real assigned sections and committed student membership
- full production-grade finalization policy is still deferred

### 11.15 Student-facing portal surfaces

Current owner file:

- [PortalController.java](D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\PortalController.java)

Purpose:

- login and dashboard routing
- student enrollment page
- student grade view
- student finance page
- student official load / COR page

These are still part of the app, but the current canon is registrar-first rather than broad student-portal productization.

## 12. How current cross-system enrollment works

At a high level:

1. Registrar defines terms, curricula, sections, schedules, rooms, and faculty.
2. Enrollment/Cashier consumes those records.
3. Student rows may be staged provisionally.
4. Only committed enlistments count as official academic membership.
5. Registrar views and academic records should reflect committed membership only.

When debugging anything enrollment-related, first check:

- active term alignment
- exact fee scope
- whether the row is `STAGED` or `COMMITTED`
- whether the student identity matches across tables

## 13. Data and demo model

### Baseline bootstrap

Main setup files live in [setup](D:\registrarCanon_canon\setup).

Canonical baseline command:

```cmd
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
```

Baseline bootstrap creates the academic master dataset and leaves the system in the active demo term.

### Full registrar feature overlay

Current full demo overlay commands:

```cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
setup\START_REGISTRAR_DEMO.cmd
```

Purpose:

- overlay curated demo students
- assign real rooms to the demo blocks/open sections
- seed Maria's admission bridge and inline-viewable applicant files
- make the demo story repeatable after reset

Primary demo script:

- [REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md](D:\registrarCanon_canon\handoffNew\REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md)

## 14. Current demo/UAT dataset meaning

Important seeded students:

| Student | Intended use |
|---|---|
| `2026-1001` | profile, admission docs, reg form, trail, history |
| `ADDCLS-2026-001` | add subject |
| `TTRNS-2026-001` | transfer credit, COG, TOR |
| `TSHFT-2026-001` | program shift |
| `SPRINT-DEMO-2026-001` | withdrawal |
| `OVRPAY-2026-001` | overpayment disposition |
| `SCH-UAT-ELIGIBLE` | scholarship positive case |
| `SCH-UAT-LOWUNITS` | scholarship negative case |

This dataset exists to exercise registrar features comprehensively, not to model a production-quality student body.

## 15. Known architectural and operational debt

Current important debt areas:

- production security hardening is incomplete
- cross-app schema versioning is not formalized
- backup/restore, HTTPS, CI/CD, monitoring, and secrets handling are not complete
- official production fees and policies are not signed off
- some Enrollment-side warnings, including `RESERVED`-related issues, still require attention
- `ModulithTests` still report package-cycle debt

These are real limitations. Do not hide them in documentation or demos.

## 16. Current agent guidance

When changing this system:

1. Preserve registrar-first scope.
2. Compare against the currently active repo state before merging ideas from older copies.
3. Treat scheduling integrity rules as release-critical.
4. Treat explicit curriculum assignment as intentional, not optional noise.
5. Do not revive dormant dean advising flows unless explicitly asked.
6. If a change affects setup, demo data, or handoff, update the docs in the same pass.

## 17. Recommended reading order

1. This specification
2. [FINAL_SYSTEM_DOCUMENTATION_20260618.md](D:\registrarCanon_canon\handoffNew\FINAL_SYSTEM_DOCUMENTATION_20260618.md)
3. [FINAL_DEMO_AND_TEST_MANUAL_20260618.md](D:\registrarCanon_canon\handoffNew\FINAL_DEMO_AND_TEST_MANUAL_20260618.md)
4. [FINAL_HANDOVER_20260618.md](D:\registrarCanon_canon\handoffNew\FINAL_HANDOVER_20260618.md)
5. [REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md](D:\registrarCanon_canon\handoffNew\REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md)
6. [BUSINESS_LOGIC_MASTER.md](D:\registrarCanon_canon\docs\business_logic\BUSINESS_LOGIC_MASTER.md)

## 18. Short version

If you need the shortest truthful summary:

- Registrar owns the academic system of record.
- Enrollment/Cashier owns payment capture and official financial posting.
- Admission owns intake and applicant decisioning.
- The current live canon centers on academic builders, scheduling, student academic profile, grading, withdrawal, scholarships, and document/audit visibility.
- The retired dean irregular advising bridge still exists in code but is not part of current scope.
- Schedule hardening, explicit curriculum assignment, committed-versus-staged enlistment semantics, and exact term fee scope are the most important rules to preserve.
