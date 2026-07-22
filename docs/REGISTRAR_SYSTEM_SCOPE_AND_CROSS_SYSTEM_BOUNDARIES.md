# Registrar System Scope and Cross-System Boundaries

Last verified: 2026-07-21  
Registrar codebase: `D:\registrarCanon_canon`  
Admission reference: `D:\Latest\Admitmoko1\admission`  
Enrollment reference: `D:\Latest\Enrollmoko1\enrollment3`

## Purpose

This document describes what the current Registrar application does, what it owns, and how it relates to Admission and Enrollment. It is a code-backed operating guide, not a database migration guide or an authorization to change cross-system behavior.

The three applications are separate Spring applications that currently coordinate primarily through the shared MariaDB schema, `eacdb`. This means a screen in one application can affect data read by another application without an HTTP call between them. Ownership rules are therefore essential.

## Runtime topology

| Application | Default runtime configuration | Primary purpose |
| --- | --- | --- |
| Registrar | `http://localhost:8083/registrar` | Academic authority, official records, schedules, registrar actions, and academic governance |
| Admission | `http://localhost:8081` | Applicant intake, documents, qualification, applicant-facing workflow, and admission-side pre-registration support |
| Enrollment | `ENROLLMENT_BASE_URL` | Pre-advising, cashier/accounting, fee assessment, enrollment finalization, and active student financial workflow |
| Shared resources | MariaDB `eacdb`; configured shared upload roots | Cross-system identity, academic, pre-registration, enlistment, and finance handoffs |

Registrar configuration is in `src/main/resources/application.properties`. It uses MariaDB with `spring.jpa.hibernate.ddl-auto=none`, reads Admission uploads from `registrar.admission.upload-dir`, and has an Admission base URL setting for linked resources. Admission and Enrollment also point to `eacdb`.

## Registrar mission and authority

Registrar is the academic system of record. Its current responsibilities are:

- maintaining academic master data and the enforceable term context;
- maintaining programs, courses, curricula, course placement, block offerings, sections, schedules, rooms, and faculty assignment;
- governing official academic enrollment actions, including academic load checks, curriculum assignment, credit posting, grade governance, and withdrawals;
- maintaining official academic history, registration-form versions, document trail, archive custody, and printable academic documents;
- providing registrar-facing student profile, enrollment, schedule, grading, fee-policy, scholarship, and audit screens;
- exposing the academic state needed by the other two applications through the shared schema.

Registrar is not the owner of applicant self-service intake, payment collection, cashier finalization, or admission document upload processing.

## Current Registrar feature inventory

| Area | Current code ownership | User-facing scope |
| --- | --- | --- |
| Admission acceptance | `controller/AdmissionController`, `service/admission/*` | Search qualified applicants, review payment/document context, admit applicants, create/align student state, and view pre-registration snapshots |
| Student profile and enrollment workspace | `controller/EnrollmentController`, `service/support/StudentProfileService`, `service/integration/JaypeeIntegrationService` | Student Manager, academic enrollment decisions, subject actions, program shift, curriculum assignment, academic credit, official forms, document trail, and profile actions |
| Academic terms and policy | `controller/AcademicController`, `service/support/GlobalTermService`, `RegistrarTermService`, `YearLevelLoadPolicyService` | Current term, term creation, global settings, academic readiness, load policy, INC expiration, and governance controls |
| Programs, courses, and curricula | `controller/ProgramController`, `CourseCatalogController`, `CurriculumController`, `service/curriculum/*` | Program lifecycle, course catalog, curriculum drafts/current offerings, course relationships, placement, curriculum export, and controlled uploads/seeding |
| Class scheduling and monitoring | `controller/AcademicController`, `service/academic/BlockOfferingService`, `ScheduleConflictValidator`, `RoomMonitoringService`, `SlotMonitoringService` | Block/section scheduling, schedule conflict control, faculty assignment, irregular access, room monitoring, and capacity/slot monitoring |
| Faculty and grade governance | `controller/AcademicController`, `FacultyLoadController`, `FacultyIrregularAdvisingController`, `service/academic/*` | Faculty load, class submission/approval, grading windows, grade changes/extensions, grade record review, and official grade handling |
| Finance policy and term fees | `controller/FinancePolicyController`, `TermFeeAdminController`, `service/finance/*` | Academic fee-policy inputs, term fee templates, readiness/import/export, year-level load gates, and overpayment disposition support |
| Scholarships | `controller/ScholarshipController`, `ScholarController`, `service/scholarship/*` | Scholarship policies, grants, academic review/approval/posting, scholar enrollment support, and scholar cashier-facing views |
| Withdrawal and archives | `controller/WithdrawalController`, `service/withdrawal/WithdrawalService` | Subject drops, full withdrawal, shift load clearing, historical withdrawal reporting, identity archive/custody, and document-release guards |
| Official forms and documents | `service/forms/*` | Registration-form events and versions, PDF generation, document trail, archive custody, and snapshot failure auditing |
| Security and administration | `config/SecurityConfig`, `security/*`, `controller/PortalController`, `AcademicController` | Login/session control, role-based portal views, users, password reset, and admin configuration |

## Registrar route groups

The route list is intentionally grouped by function. The controller classes remain the detailed source of truth.

| Route group | Controller | Examples |
| --- | --- | --- |
| Academic administration | `AcademicController` | `/admin/settings`, `/admin/terms/*`, `/admin/classes`, `/admin/class-scheduling`, `/admin/slot-monitoring`, `/admin/room-monitoring`, `/admin/grade-records` |
| Student academic workspace | `EnrollmentController` | `/admin/student-manager`, `/admin/enrollment`, `/admin/enroll`, curriculum assignment, credit approval, program shift, enrollment/drop, COR/COG/TOR printing |
| Admission handoff | `AdmissionController` | `/admin/admission-acceptance`, `/admin/approve-admission`, `/admin/pre-reg/{refNo}/snapshot` |
| Curriculum catalog | `ProgramController`, `CourseCatalogController`, `CurriculumController` | `/admin/programs`, `/admin/courses`, `/admin/curriculum` |
| Finance and scholarships | `FinancePolicyController`, `TermFeeAdminController`, `ScholarshipController`, `ScholarController` | `/admin/finance-policy`, `/admin/term-fees`, `/admin/scholarships`, `/admin/scholar-*` |
| Faculty workflows | `FacultyLoadController`, `FacultyIrregularAdvisingController` | `/admin/faculty-load`, `/faculty/irregular-advising`, `/faculty/pre-reg/*` |
| Withdrawal | `WithdrawalController` | `/admin/withdrawals`, subject drop, full withdrawal, shift load clearing |
| Student portal | `PortalController` | `/`, `/login`, `/enrollment`, `/my-grades`, `/student/finance`, `/my-load` |

## Data and workflow ownership

The shared database is a technical integration surface, not proof that every application may freely write every table. The following is the operating ownership model reflected in current code and the current-state handoff.

| Business concern | Primary owner | Registrar role | Admission role | Enrollment role |
| --- | --- | --- | --- | --- |
| Applicant intake, requirements, qualification | Admission | Reads qualified applicant context; performs registrar admission decision | Creates and maintains applicant workflow and qualification | Reads applicant/pre-admission context when beginning enrollment workflows |
| Applicant status handoff | Shared lifecycle with explicit writers | Marks qualified applicant as `ADMITTED`; mirrors official enrollment state from student identity | Owns applicant workflow states before registrar admission | Finalization/cashier workflow may promote the official enrollment state when gates are met |
| Student number and live enrollment identity | Enrollment | Uses and protects the academic student identity; keeps withdrawn identity history | Links applicant to the shared identity | Owns live student-number issuance and cashier/finalization lifecycle |
| Academic term | Registrar | Authoritative term/calendar/current-term policy | Syncs current term from Registrar | Reads/uses term context for enrollment operations |
| Programs, courses, curricula, sections, schedules | Registrar | Sole academic authority and operational writer | Reads programs; admission-side status/display is downstream only | Reads academic offerings and uses them for pre-advising/enlistment |
| Pre-advising and irregular/transferee draft load | Enrollment | Provides readiness visibility and downstream academic approval/posting | May read readiness/pre-registration data for applicant workflow | Owns irregular/continuing pre-advising, dean review, draft generation, and enrollment-side pre-registration |
| Pre-registration snapshots | Shared contract | Can produce Registrar snapshots; reads snapshot for admission/enrollment decisions | Reads shared snapshots and supports applicant-facing pre-reg forms | Reads snapshot lines for cashier/finalization; may produce Enrollment pre-advising snapshot data |
| Transfer/accreditation credit | Split approval workflow | Owns official approval/rejection and academic posting to records | Provides applicant context where applicable | Creates Registrar-facing transfer-credit requests after dean/accreditation workflow |
| Program fee settings | Registrar | Sole writer for `program_fee_settings` | Reads only when needed for applicant/pre-reg presentation | Read-only consumer for assessment and cashier calculations |
| Payments, cashier, ledger, accounting clearance | Enrollment | Reads balances/holds for academic/document decisions; must not mutate ledger on a read path | Displays admission-payment context where needed | Owns cashier/payment/finalization and financial lifecycle |
| Grades and official records | Registrar | Official grade governance, approval, history, and documents | No authoritative grade ownership | Uses Registrar grade outcome semantics for eligibility/continuation checks |
| Withdrawal, archive, official document release | Registrar | Owns academic withdrawal record, archive/custody, and release restrictions | Treats withdrawn identity as non-active | May accept authorized archive settlement; must not revive withdrawn/inactive students |

## Core cross-system lifecycles

### 1. Applicant to admitted student

1. Admission creates and qualifies the applicant in `applicants`.
2. Registrar Admission Acceptance reads applicants in `QUALIFIED FOR ENROLLMENT` state.
3. Registrar admits the applicant, creates/aligns student state, and moves the applicant handoff to `ADMITTED`.
4. Enrollment issues/uses the live student identity, runs pre-advising and financial gates, then finalizes the enrollment workflow when allowed.
5. Registrar's `ApplicantStatusSyncService` mirrors the official enrolled outcome to shared applicant and user records when enrollment is committed.

### 2. Irregular/transferee pre-advising and credit

1. Enrollment owns the irregular/transferee pre-advising draft, dean review, and enrollment-side fee authoring.
2. Shared pre-registration snapshots and subject lines expose the selected academic load across applications.
3. Enrollment creates Registrar-facing `transfer_credit_requests` after dean/accreditation review.
4. Registrar reviews, approves/rejects, and posts the official academic credit. Registration-form and document-trail services preserve the official history.
5. Enrollment completes its financial/finalization responsibilities only after the relevant gates are satisfied.

### 3. Continuing-student enrollment

1. Registrar maintains the current academic term, curriculum, offerings, schedules, room capacity, and academic load rules.
2. Enrollment prepares/commits the enrollment-side load and payment/finalization state.
3. Registrar uses the committed academic state for grade history, official forms, document controls, academic enforcement, and future-term governance.

### 4. Withdrawal and settlement

1. Registrar records subject drop/full withdrawal/shift load-clearing actions, preserves archive history, and applies document-release holds where applicable.
2. Enrollment may process legitimate cashier settlement for an archived balance, but read-only ledger inspection must not mutate identity or enrollment status.
3. Withdrawn student numbers are permanent historical identities. Do not re-enable release/reuse behavior without an explicit owner decision and a new implementation plan.

## Shared-contract tables and records

Representative shared contracts include:

- `applicants`, `students`, and `sys_users` for applicant/student identity and lifecycle state;
- `academic_terms`, programs, courses, curricula, class sections, class schedules, rooms, and faculty-assignment data for academic operations;
- `student_enlistments` for current load/commit state;
- `applicant_pre_reg_snapshots`, subject lines, credit lines, and installment lines for pre-registration exchange;
- `transfer_credit_requests` and linked accreditation/credit records for dean-to-registrar approval;
- `program_fee_settings` as Registrar-authored fee configuration;
- grade, registration-form, document-trail, withdrawal, custody, and archive records for official academic history.

The exact column contract can evolve, so changes must be coordinated across all three applications and verified against the active database before deployment.

## Important operational rules

- Treat Registrar as the academic authority even where Enrollment has a shared-schema write path.
- Treat Enrollment cashier/finalization as an explicit write workflow. Ledger and other inspection routes must remain read-only.
- Do not use a screen visit as a repair mechanism for applicant, enrollment, payment, or identity state.
- Preserve `ADMITTED`, `ENROLLED`, `WITHDRAWN`, and pre-registration status semantics across all three systems.
- Enforce current-term scope, committed-load requirements, and academic grade outcome semantics consistently.
- Keep Admission uploads on the configured shared file root; do not copy applicant documents into uncontrolled locations.
- For high-risk actions—admission approval, enrollment commit, program shift, credit posting, grade changes, withdrawal, document release, and fee policy—require explicit user intent and retain audit/history records.

## Registrar source map

The Registrar production structure now follows the project-wide layered layout:

```text
com.iuims.registrar
  config/                 application and web/security configuration
  security/               authenticated-user and session support
  controller/             HTTP/UI entry points
  service/
    academic/             grades, schedules, room/slot monitoring, rollover
    admission/            applicant handoff and pre-reg reads
    curriculum/           course, program, curriculum, transfer-credit logic
    faculty/              faculty-load support
    finance/              fee-policy and overpayment support
    forms/                registration forms, PDF, trail, custody
    integration/          shared enrollment-schema integration
    scholarship/          scholarship workflow
    support/              terms, profiles, audit, identity guards
    withdrawal/           drop, withdrawal, archive workflow
  repository/             persistence repositories
  entity/                 JPA entities
  dto/                    transfer/view data
  domain/                 narrow cross-layer contracts and domain constants
  support/                shared SQL/policy helpers
```

`LayeredArchitectureTests` enforces that controllers do not depend directly on persistence, entities do not depend on web/services, and repositories do not depend on controllers.

## Verification and change protocol

Before changing any cross-system rule:

1. Identify the owning application and every shared table/record affected.
2. Confirm whether the action is a read, an explicit write workflow, or a historical/reporting view.
3. Preserve current route behavior unless the owner approves a workflow change.
4. Test the full lifecycle in order: Admission applicant state, Enrollment pre-advising/cashier/finalization, then Registrar academic/record outcome.
5. Verify both database state and browser-visible state. For registrar actions, also verify registration-form/document trail and audit records.

Current structural verification on 2026-07-21: Registrar compiles, packages into a WAR, and its three architecture rules pass. The full legacy suite still has finance/policy/credit-grade/program-shift integration failures requiring a separate functional triage; they were not changed as part of the package-architecture migration.

## Key references

- Registrar routes: `src/main/java/com/iuims/registrar/controller/`
- Registrar services: `src/main/java/com/iuims/registrar/service/`
- Registrar runtime configuration: `src/main/resources/application.properties`
- Current-state archive: `D:\registrarCanon_canon_SUPPORT_ARCHIVE_20260720\handoffNew\CURRENT_STATE_MAP.md`
- Enrollment ledger boundary: `D:\registrarCanon_canon_SUPPORT_ARCHIVE_20260720\handoffNew\2026-06-30_ENROLLMENT_LEDGER_BOUNDARY_NOTE.md`
