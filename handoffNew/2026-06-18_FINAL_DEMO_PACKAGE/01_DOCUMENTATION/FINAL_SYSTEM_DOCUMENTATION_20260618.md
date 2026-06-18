# Registrar System - Final Current-State Documentation

Date: 2026-06-18  
Canonical workspace: `C:\newer\new`  
Registrar project: `C:\newer\new\registrar`

## 1. Purpose and release position

This document is the authoritative description of the Registrar build at handover.

The current build is:

- ready for a controlled demonstration and continued UAT
- packageable as a WAR
- not approved for production deployment

Production approval remains blocked by security, operations, official business data, and incomplete human sign-off. A successful build is not a production sign-off.

## 2. Canonical system ownership

| Capability | Canonical owner |
|---|---|
| Applicant intake and admission decision | Admission |
| Regular new-enrollee pre-registration and automatic section assignment | Admission / Enrollment |
| Cashier payment, assessment, and payment-triggered enrollment | Enrollment |
| Student-number issuance after payment | Enrollment |
| Courses, programs, curricula, academic terms | Registrar |
| Sections, class offerings, schedules, rooms, faculty assignment | Registrar |
| Student academic profile and curriculum assignment | Registrar |
| Transfer crediting and program shift | Registrar |
| Grades, grade changes, grading windows, academic records | Registrar |
| Withdrawal approval and academic document trail | Registrar |
| Scholarship eligibility review and posting | Registrar, with Enrollment consuming posted status |
| Fee amounts and payment transactions | Enrollment / Accounting, using Registrar-managed term fee configuration in the current shared design |

### Retired scope

The Registrar Dean/Faculty irregular new-enrollee advising and pre-registration bridge is dormant and outside the accepted scope. Its routes, tables, and old documents are historical remnants. Do not demo, extend, or treat them as current workflow canon.

## 3. Runtime architecture

| Component | Location | Runtime |
|---|---|---|
| Registrar | `C:\newer\new\registrar` | Spring Boot 3.2.1, Java 17 target, port 8083, context `/registrar` |
| Enrollment | `C:\newer\new\enrollment3` | Spring Boot application, port 8082 |
| Admission | `C:\newer\new\admission` | External intake system; not required for the Registrar-only demo |
| Database | `eacdb` on `127.0.0.1:3306` | Shared MySQL/MariaDB schema |

The applications currently integrate primarily through shared database tables. There is no formal versioned API contract between the applications.

## 4. Canonical identifiers and contracts

| Contract | Rule |
|---|---|
| Active term | `system_settings.CURRENT_ACADEMIC_TERM`; demo value `1120242025`, `term_id = 1` |
| Student identity | `student_number` is the transaction identity shared across active flows |
| Applicant correlation | `reference_number` links intake records before a student number exists |
| Enlistment state | `student_enlistments.enlistment_status`; `STAGED` is provisional, `COMMITTED` is official |
| Official fee scope | Exact `program_fee_settings.term_id + program + year + semester` |
| Scholarship activation | Financial effect begins at `POSTED`, not merely `APPROVED` |
| Room assignment | A schedule may remain tentative/TBA and receive a room later |

Do not restore `NULL` enlistment status as official enrollment. Do not restore legacy fee fallback behavior for live assessment.

## 5. Academic builder dependency chain

The builders are not independent islands. Their expected dependency order is:

1. Program Builder defines the program.
2. Course Builder defines reusable course catalog records.
3. Curriculum Builder maps courses to a program, curriculum version, year, and semester.
4. Academic Term configuration supplies the active term.
5. Class Scheduling creates block/course sections from curriculum and term data.
6. Schedule slots assign day, time, faculty, and optional room.
7. Enrollment stages or commits students against those Registrar-owned sections.
8. Faculty grading and Registrar records operate on committed class membership.

Deleting or changing an upstream record can invalidate downstream scheduling and student records. Use soft retirement or a new curriculum version for historical data whenever possible.

## 6. Implemented Registrar capabilities

- program, course, curriculum, term, section, and schedule administration
- blank curriculum creation as an inactive editable draft, separate from readiness placeholders
- block sections and irregular/open course sections
- class-scheduling filters for block attributes and server-side course/section/faculty/schedule/day/room criteria
- committed-only official class counts and rosters
- Student Profile with registrar-editable data, curriculum status, alerts, history, and ledger visibility
- explicit curriculum assignment and program-shift support
- TOR/transfer course crediting support
- Registration Form generation and history; legacy `COR` route names remain internally for compatibility
- withdrawal request, Dean review, Registrar queue, report, and document-trail integration
- faculty grade entry, grading windows, grade-change requests, and approval views
- configurable scholarship policy using completed units; demo default is 27 units
- scholarship workflow: `PENDING -> APPROVED -> POSTED`, plus reject/revoke paths
- exact-term fee readiness, CSV import/export, and finance-policy administration
- term-transition preparation and readiness checks

## 7. Known boundaries and deferred work

| Item | Current position |
|---|---|
| Registrar Spring Security hardening | Deferred; demo authentication must not be treated as production security |
| Official production fee values | Business-owned and not supplied; demo rates only |
| Full grade finalization policy | Deferred pending business confirmation |
| Dean versus Admin/VPAA grade approval semantics | Pending decision |
| Faculty permission levels: none/view/encode | Pending role/access audit |
| TOR PDF upload/OCR and automated equivalency | Out of scope |
| Automatic schedule generation/optimization | Out of scope; manually entered room and faculty overlaps are blocked and existing conflicts are shown |
| CI/CD, secrets, HTTPS, backups, monitoring | Not implemented |
| Cross-application schema versioning | Not implemented |
| Enrollment `RESERVED` schema warning | Enrollment-side investigation required before hard finance UAT |

## 8. Current verification evidence

Executed on 2026-06-18:

| Check | Result |
|---|---|
| Registrar `mvn -q -DskipTests package` | PASS |
| Registrar WAR | `target/registrar-0.0.1-SNAPSHOT.war` |
| Enrollment `.\mvnw.cmd -q -DskipTests package` | PASS |
| Enrollment WAR | `target/enrollment.war` |
| Registrar functional tests | 42 passed, 1 skipped |
| Registrar full `mvn test` | 1 structural error: existing Modulith package cycles |
| Focused Registrar browser UAT | Core pages passed; scholarship workflow implemented and seeded |
| Human cross-app UAT | Sessions C-E still require final transaction/sign-off evidence |

The Modulith error is not a functional test failure, but it is a real architecture debt item and must be resolved or formally excluded before a production quality gate can be green.

## 9. Readiness decision

### Controlled demo: GO with conditions

- use the seeded demo database
- keep the active term at `1120242025`
- run the pre-demo gate in the final demo manual
- avoid the retired irregular-advising bridge
- identify demo fee values as non-production data

### Production deployment: NO-GO

Production requires completed Sessions C-E, official fee/policy confirmation, authentication and authorization hardening, secrets management, backup/restore proof, HTTPS, operational monitoring, and disposition of the Modulith architecture test.

## 10. Canonical documents

1. `FINAL_SYSTEM_DOCUMENTATION_20260618.md` - current system and scope.
2. `FINAL_DEMO_AND_TEST_MANUAL_20260618.md` - setup, demo script, and acceptance tests.
3. `FINAL_HANDOVER_20260618.md` - successor instructions, risks, and release gates.

Older documents under `handoffNew` are supporting history. The three files above take precedence where statements conflict.
