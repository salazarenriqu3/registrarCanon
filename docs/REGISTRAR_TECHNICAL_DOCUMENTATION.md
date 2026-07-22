# Registrar Technical Documentation

Last verified: 2026-07-21

Project root: `D:\registrarCanon_canon`

Related boundary document: `docs/REGISTRAR_SYSTEM_SCOPE_AND_CROSS_SYSTEM_BOUNDARIES.md`

## 1. Purpose

This document is the technical operating guide for the Registrar application. It describes how the current codebase is structured, how it runs, which features it owns, how it integrates with Admission and Enrollment, and what must be verified before changing high-risk registrar behavior.

The companion scope document defines business ownership and cross-system boundaries. This document focuses on implementation shape and developer-facing operation.

## 2. System Summary

Registrar is a Java 17 Spring Boot application packaged as a WAR. It serves registrar-facing and student-facing Thymeleaf pages under the `/registrar` context path and coordinates with Admission and Enrollment mainly through the shared MariaDB schema `eacdb`.

Registrar is the academic authority in the three-system setup. It owns academic master data, term policy, curricula, schedules, room/slot monitoring, registrar student academic actions, grade governance, official forms, document trail, withdrawal/archive custody, and academic records.

Registrar does not own applicant intake, regular applicant self-service, cashier payment collection, or enrollment finalization. Those responsibilities belong to Admission and Enrollment according to the current cross-system boundary.

## 3. Runtime Topology

| System | Local URL | Main role |
| --- | --- | --- |
| Registrar | `http://localhost:8083/registrar` | Academic authority, official records, registrar actions |
| Admission | `http://localhost:8081` | Applicant intake, qualification, documents |
| Enrollment | `ENROLLMENT_BASE_URL` | Pre-advising, cashier, fee workflow, finalization |
| Database | MariaDB `eacdb` | Shared identity, academic, enrollment, finance, and document contracts |

Registrar runtime settings are in `src/main/resources/application.properties`.

Important active settings:

| Setting | Current value or behavior |
| --- | --- |
| Application name | `RegistrarSubsystem` |
| Server port | `8083` |
| Servlet context path | `/registrar` |
| Database URL | `jdbc:mariadb://127.0.0.1:3306/eacdb?createDatabaseIfNotExist=true` |
| Hibernate DDL | `spring.jpa.hibernate.ddl-auto=none` |
| Open Session in View | `spring.jpa.open-in-view=false` |
| Template engine | Thymeleaf |
| Upload roots | Admission upload root and Registrar shared upload root are configurable |
| Session tracking | Cookie based |
| Error exposure | Messages and binding errors enabled; stack traces disabled |

## 4. Technology Stack

| Layer | Current implementation |
| --- | --- |
| Language/runtime | Java 17 |
| Framework | Spring Boot 3.2.1 |
| Packaging | WAR |
| Web/UI | Spring MVC + Thymeleaf |
| Security | Spring Security with custom authenticated-user/session support |
| Persistence | Spring Data JPA + Spring JDBC |
| Database | MariaDB using `mariadb-java-client` |
| Documents | Apache PDFBox for PDF output, Apache POI for spreadsheet/import/export paths |
| Mapping | MapStruct dependency and annotation processor |
| Tests | JUnit/Spring Boot Test, H2 for selected tests, Spring Security Test, ArchUnit |
| Additional runtime | Spring AI MCP WebMVC server dependency is enabled |

## 5. Source Layout

The production Java package is `com.iuims.registrar`.

```text
src/main/java/com/iuims/registrar
  RegistrarApplication.java
  config/
  security/
  controller/
  service/
    academic/
    admission/
    curriculum/
    faculty/
    finance/
    forms/
    integration/
    scholarship/
    support/
    withdrawal/
  repository/
  entity/
  dto/
  domain/
    academic/
    curriculum/
    finance/
    forms/
  support/
```

Layer intent:

| Package | Responsibility |
| --- | --- |
| `config` | Spring MVC, security, error handling, and app configuration |
| `security` | Current-user and session support |
| `controller` | HTTP endpoints and Thymeleaf model assembly |
| `service` | Business workflows and shared-schema orchestration |
| `repository` | Spring Data repositories |
| `entity` | JPA mapped records |
| `dto` | Request, response, and view models |
| `domain` | Narrow domain constants/contracts that are shared across services |
| `support` | Shared SQL and utility helpers |

The architecture test `src/test/java/com/iuims/registrar/LayeredArchitectureTests.java` enforces the current layered rule set:

- controllers must not depend directly on repositories;
- entities must not depend on controllers or services;
- repositories must not depend on controllers.

## 6. Main Controllers and Route Areas

Registrar currently has 14 controller classes.

| Controller | Primary area |
| --- | --- |
| `PortalController` | Root dashboard, login/logout, student portal pages |
| `AcademicController` | Settings, terms, users, classes, scheduling, room/slot monitoring, grading |
| `AdmissionController` | Admission acceptance and pre-registration snapshot review |
| `EnrollmentController` | Student Manager, Student Enrollment, add/drop, shift, documents, COR/COG/TOR |
| `WithdrawalController` | Drops, full withdrawal, shift load clearing, withdrawal reporting |
| `ProgramController` | Program builder |
| `CourseCatalogController` | Course catalog and course relationship management |
| `CurriculumController` | Curriculum builder, draft/current lifecycle, import/export |
| `FinancePolicyController` | Finance policy and academic fee-rule inputs |
| `TermFeeAdminController` | Term fee readiness, templates, import/export |
| `ScholarshipController` | Scholarship policies, grants, academic approval/posting |
| `ScholarController` | Scholar walk-in/cashier support screens |
| `FacultyLoadController` | Faculty load and schedule assignment support |
| `FacultyIrregularAdvisingController` | Historical/transitional faculty irregular pre-reg routes |

Representative route groups:

| Route group | Examples |
| --- | --- |
| Portal | `/`, `/login`, `/logout`, `/enrollment`, `/my-grades`, `/student/finance`, `/my-load` |
| Academic settings | `/admin/settings`, `/admin/settings/readiness`, `/admin/save-settings`, `/admin/update-global-term`, `/admin/terms/add` |
| Users | `/admin/users`, `/create-user`, `/admin/update-user`, `/admin/delete-user`, `/admin/reset-password` |
| Classes and scheduling | `/admin/classes`, `/admin/class-scheduling`, `/admin/class-scheduling/create-block`, `/admin/class-scheduling/add-schedule`, `/admin/class-scheduling/assign-faculty` |
| Room and slot monitoring | `/admin/room-monitoring`, `/admin/room-monitoring/add-room`, `/admin/slot-monitoring`, `/admin/slot-monitoring/update-capacity`, `/admin/slot-monitoring/bulk-close` |
| Grades | `/grades`, `/admin/approvals`, `/admin/grade-records`, `/admin/demo-grades`, `/faculty/submit-class`, `/admin/approve-class`, `/admin/approve-change` |
| Admission handoff | `/admin/admission-acceptance`, `/api/search-applicants`, `/admin/approve-admission`, `/admin/pre-reg/{refNo}/snapshot` |
| Student workspace | `/admin/student-manager`, `/admin/enrollment`, `/api/search-students`, `/admin/enroll`, `/admin/process-enrollment` |
| Registrar actions | `/admin/student-manager/shift-program`, `/admin/student-manager/assign-curriculum`, `/admin/drop`, `/admin/block-enroll`, `/admin/force-enroll`, `/admin/enrollment-drop` |
| Records and documents | `/admin/reg-form-history`, `/admin/document-trail`, `/admin/print-cor`, `/admin/print-cog`, `/admin/print-tor` |
| Withdrawal | `/admin/withdrawals`, `/admin/withdrawals/drop-subject`, `/admin/withdrawals/drop-student`, `/admin/withdrawals/clear-load-for-shift` |
| Curriculum and catalog | `/admin/programs`, `/admin/courses`, `/admin/curriculum`, `/admin/curriculum/course-search`, `/admin/curriculum/export/{curriculumId}` |
| Fees and scholarships | `/admin/finance-policy`, `/admin/term-fees`, `/admin/scholarships`, `/admin/scholar-walkin`, `/admin/scholar-cashier` |

## 7. UI Templates

Thymeleaf templates live in `src/main/resources/templates`.

Important template groups:

| Template area | Files |
| --- | --- |
| Shared layout/security | `fragments/layout.html`, `fragments/csrf.html`, `error.html` |
| Authentication and portal | `login.html`, `dashboard.html`, `enrollment.html`, `student_grades.html`, `student_finance.html` |
| Admin academic | `admin_settings.html`, `admin_classes.html`, `admin_class_scheduling.html`, `admin_room_monitoring.html`, `admin_slot_monitoring.html` |
| Programs/courses/curriculum | `admin_programs.html`, `admin_course_catalog.html`, `admin_curriculum.html` |
| Student and records | `admin_student_manager.html`, `admin_enrollment.html`, `admin_reg_form_history.html`, `admin_document_trail.html` |
| Official documents | `student_cor.html`, `print_cog.html`, `print_tor.html` |
| Grades | `grades_menu.html`, `grades.html`, `grades_sheet.html`, `admin_grade_records.html`, `admin_demo_grades.html`, `admin_approvals.html` |
| Finance/scholarship | `admin_finance_policy.html`, `admin_term_fees.html`, `admin_scholarships.html`, `admin_scholar_walkin.html`, `admin_scholar_cashier.html`, `admin_scholar_ledger.html` |
| Admission/withdrawal/faculty | `admin_admission_acceptance.html`, `withdrawal_queue.html`, `faculty_irregular_advising.html`, `admin_faculty_load.html` |

## 8. Service Areas

The service layer is the best entry point for understanding feature behavior.

| Service package | Main responsibilities |
| --- | --- |
| `service.admission` | Applicant payment/document reads, admission approval status sync, pre-reg snapshot compatibility |
| `service.academic` | Class scheduling, schedules/conflicts, room and slot monitoring, grading, INC expiration, term rollover disposition |
| `service.curriculum` | Programs, course catalog, curriculum drafts/current lifecycle, seeding/import, student curriculum assignment, transfer-credit approval |
| `service.faculty` | Faculty load aggregation and suspicious-load repair support |
| `service.finance` | Finance policy settings, term fee templates/readiness, overpayment disposition |
| `service.forms` | Registration form events/versions/PDFs, document trail, archive custody, snapshot failure audit |
| `service.integration` | Shared enrollment-schema bridge used by Student Enrollment and registrar academic actions |
| `service.scholarship` | Scholarship workflow and scholar enrollment guards |
| `service.support` | Database setup guards, terms, audit trail, student profile, identity release guard, load policy |
| `service.withdrawal` | Subject drop, full withdrawal, shift load cleanup, withdrawal report/archive behavior |

## 9. Persistence and Shared Database Contracts

Registrar uses both repositories and JDBC-backed services against the shared MariaDB database.

Repository-backed entities include academic terms, policies, programs, courses, curricula, class sections, class schedules, departments, grades, users, students, grading windows, grade-change requests, VPAA extensions, system settings, term transition audit, and program fee settings.

Representative shared tables and records used by the Registrar workflows:

| Contract | Registrar usage |
| --- | --- |
| `applicants` | Reads qualified applicants and updates admission/enrollment handoff state |
| `students` | Reads and governs shared student identity, curriculum, enrollment, archive, and academic status |
| `sys_users` | Authenticated user and lifecycle mirroring |
| `academic_terms` | Registrar current-term authority and term-sensitive filters |
| programs/courses/curricula/sections/schedules | Registrar-owned academic master and operational schedule data |
| `student_enlistments` | Current-term load, add/drop, committed/enrolled subject rows |
| pre-registration snapshot tables | Cross-system applicant/irregular/transferee handoff |
| `transfer_credit_requests` | Enrollment-to-Registrar accreditation/credit approval bridge |
| `program_fee_settings` | Registrar-authored fee configuration consumed downstream |
| grades and grade event records | Official grade governance and history |
| registration-form/document/archive records | Official documents, auditability, custody, and withdrawal history |

Because the database is shared, changing a table, status value, or write path can affect all three systems even if only Registrar code is edited.

## 10. Core Workflows

### Admission acceptance

Admission qualifies applicants. Registrar reads qualified applicants, reviews supporting payment/document/pre-reg context, admits the applicant, aligns student state, and mirrors the applicant lifecycle through `ApplicantStatusSyncService`.

Implementation entry points:

- `AdmissionController`
- `FinanceAdmissionService`
- `ApplicantStatusSyncService`
- `ApplicantPreRegSnapshotService`
- `ApplicantDocumentReadService`

### Student academic workspace

Student Manager and Student Enrollment expose the registrar academic workspace for student lookup, load inspection, curriculum assignment, add/drop, program shift, credit posting, document history, and official print routes.

Implementation entry points:

- `EnrollmentController`
- `JaypeeIntegrationService`
- `StudentProfileService`
- `StudentCurriculumService`
- `CreditGradeService`
- `RegFormEventService`
- `RegFormVersionService`

### Curriculum and academic master data

Program Builder, Course Catalog, and Curriculum Management are registrar-owned. Curriculum should stay draft-first, with explicit publish/current lifecycle behavior. Course and program edits must preserve downstream usage visibility.

Implementation entry points:

- `ProgramController`, `CourseCatalogController`, `CurriculumController`
- `ProgramService`
- `CourseCatalogService`
- `CurriculumSeederService`
- `StudentCurriculumService`
- `CurriculumLoadPolicyService`

### Class scheduling and monitoring

Registrar owns block offerings, class sections, schedules, faculty assignment, conflict validation, room monitoring, and slot monitoring. Class Scheduling is current-term scoped and includes irregular-access controls for block-managed offerings.

Implementation entry points:

- `AcademicController`
- `BlockOfferingService`
- `ScheduleConflictValidator`
- `RoomMonitoringService`
- `SlotMonitoringService`
- `FacultyLoadService`

### Grades and official records

Registrar owns grade governance, grading windows, approval/rejection behavior, official grade history, and grade event ledgers. The standalone grading app is retired; registrar grade workflows remain in this application.

Implementation entry points:

- `AcademicController`
- `AcademicGradingService`
- `DemoGradeWorkspaceService`
- `GradeRecordEventService`
- `IncExpirationScheduler`

### Withdrawal and archive custody

Registrar owns subject drops, full withdrawal, shift load clearing, withdrawal reports, archive keys, custody, document-trail history, and document-release restrictions. Withdrawn student numbers remain permanent historical identities and must not be reused without explicit owner approval.

Implementation entry points:

- `WithdrawalController`
- `WithdrawalService`
- `StudentArchiveCustodyService`
- `StudentDocumentTrailService`
- `StudentIdentityReleaseService`

### Finance and scholarships

Registrar owns academic fee-policy inputs and term-fee readiness/template configuration. Enrollment owns cashier payment collection and financial finalization. Registrar scholarship handling is academic-only and should not become a cashier/payment owner.

Implementation entry points:

- `FinancePolicyController`
- `TermFeeAdminController`
- `ScholarshipController`
- `ScholarController`
- `FinancePolicyService`
- `TermFeeAdminService`
- `OverpayDispositionService`
- `ScholarEnrollmentService`

## 11. Cross-System Boundaries

Current ownership rules:

| Concern | Owner |
| --- | --- |
| Applicant intake and document upload | Admission |
| Applicant qualification | Admission |
| Admission acceptance decision | Registrar |
| Student number issuance and enrollment finalization | Enrollment |
| Payment collection, cashier, ledger mutation | Enrollment |
| Academic master data | Registrar |
| Term authority | Registrar |
| Curriculum and schedule authority | Registrar |
| Registrar academic actions | Registrar |
| Official grade records | Registrar |
| Withdrawal/archive/document custody | Registrar |

High-risk cross-system changes must be treated as architectural changes if they touch:

- applicant status values;
- student identity fields;
- enrollment status fields;
- current term resolution;
- student enlistment writes;
- cashier/payment/ledger tables;
- curriculum assignment;
- transfer-credit approval;
- grade posting;
- withdrawal/archive state;
- registration-form history;
- document release.

## 12. Security and Access Notes

Registrar uses Spring Security and cookie-based sessions. The application has administrative and student-facing routes, with authentication and authorization behavior defined in `config/SecurityConfig` and related security support classes.

Before penetration testing or vulnerability testing, keep the active project folder focused on runnable application files and the technical documentation needed by the tester. Support archives, historical dumps, one-off handoffs, and retired SQL packs should remain outside the active codebase unless they are required for the agreed deployment or test setup.

For security-sensitive registrar actions, the UI should require explicit user intent and backend workflows should preserve audit/history where applicable. Examples include admission approval, enrollment/add/drop, program shift, curriculum assignment, credit posting, grade approval/change, withdrawal, document release, fee-policy updates, and user/password administration.

## 13. Build, Test, and Run

Prerequisites:

- JDK 17
- Maven or project Maven wrapper
- MariaDB running locally
- `eacdb` schema/data compatible with the current three-system setup

Common commands from `D:\registrarCanon_canon`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -Dtest=LayeredArchitectureTests test
.\mvnw.cmd -DskipTests package
.\mvnw.cmd spring-boot:run
```

If the current shell does not inherit Java configuration, set `JAVA_HOME` for the command session before running Maven.

```powershell
$env:JAVA_HOME='C:\Users\meg\Downloads\jdk-17.0.12_windows-x64_bin\jdk-17.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Open the application at:

```text
http://localhost:8083/registrar
```

## 14. Test Coverage

The test tree currently includes:

- architecture tests;
- controller tests for scheduling, Student Manager, registration-form history, withdrawal, scholar workflows, COR PDF output;
- service tests for forms, curriculum, withdrawal, faculty load, finance, integration bridges, scholarship, academic grading, scheduling, room/slot monitoring, admission compatibility, and student profile support.

Current known status from the package-architecture verification pass:

- `mvnw.cmd -DskipTests compile` passed.
- `mvnw.cmd -Dtest=LayeredArchitectureTests test` passed.
- WAR packaging completed and produced `target\registrar-0.0.1-SNAPSHOT.war`.
- The full legacy suite still had finance/policy/credit-grade/program-shift integration failures requiring separate functional triage. Those failures were not resolved by the package-architecture cleanup and should not be assumed to be documentation issues.

## 15. Verification Checklist for Changes

For ordinary code changes:

- compile the app;
- run the targeted test class or service test;
- check affected templates for CSRF and form field alignment;
- verify affected routes in the browser if the UI changed.

For registrar high-risk changes:

- identify the exact controller, service, repository/entity, template, and table set;
- confirm which system owns the write;
- verify database before and after the action;
- verify the browser outcome and flash/error handling;
- verify audit, document trail, registration-form version, grade event, or withdrawal history where applicable;
- run at least one end-to-end path across Admission, Enrollment, and Registrar if the change affects shared lifecycle state.

For database/index changes:

- capture the slow query or target query;
- run `EXPLAIN`;
- apply only the index or migration needed for the proven slow path;
- verify the query result remains identical;
- verify Admission and Enrollment do not break on the changed table/index.

## 16. Documentation Maintenance Rule

Update this document when a change alters:

- runtime port/context/database configuration;
- package layout;
- controller route groups;
- major service ownership;
- cross-system ownership;
- shared table contracts;
- build/test status;
- penetration-test preparation requirements;
- official registrar workflows.

For deeper business scope and ownership rules, update `docs/REGISTRAR_SYSTEM_SCOPE_AND_CROSS_SYSTEM_BOUNDARIES.md` together with this document.

## 17. Key Local References

| Reference | Path |
| --- | --- |
| Active Registrar codebase | `D:\registrarCanon_canon` |
| Scope and boundaries | `D:\registrarCanon_canon\docs\REGISTRAR_SYSTEM_SCOPE_AND_CROSS_SYSTEM_BOUNDARIES.md` |
| Runtime configuration | `D:\registrarCanon_canon\src\main\resources\application.properties` |
| Controllers | `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\controller` |
| Services | `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\service` |
| Templates | `D:\registrarCanon_canon\src\main\resources\templates` |
| Tests | `D:\registrarCanon_canon\src\test` |
| Support archive | `D:\registrarCanon_canon_SUPPORT_ARCHIVE_20260720` |
| Admission reference | `D:\Latest\Admitmoko1\admission` |
| Enrollment reference | `D:\Latest\Enrollmoko1\enrollment3` |
