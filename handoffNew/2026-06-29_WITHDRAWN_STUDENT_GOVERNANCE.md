# Withdrawn Student Governance Pass

Date: 2026-06-29

## Canon Rule

Withdrawn students are historical registrar records, not active enrollment subjects.

Readable history remains available:

- Student Profile
- withdrawal history
- registration form history
- document trail
- archive and custody tracking
- financial ledger visibility

Active actions are blocked:

- registrar subject add
- registrar bulk add
- registrar program shift
- registrar curriculum reassignment
- enrollment cashier assessment/finalization
- walk-in student payment/finalization
- official document release when a withdrawn student still has an outstanding balance

## Implemented In This Pass

Registrar:

- Full-student withdrawal now stamps `student_archive_files.archive_status` as `WITHDRAWN_FILE` and keeps retention as `PERMANENT`.
- Registrar now generates and persists an immutable `archive_key` for withdrawn students so historical lookup can survive later student-number reuse work.
- Registrar now snapshots withdrawn live-student identity into `student_identity_archive`, preserving the archived student number, reference number, name, program, and term fields under the immutable archive key.
- Document trail, reg-form events, custody events, and withdrawal requests now store the archive key alongside the live student number.
- Student Profile hides the external enrollment action for withdrawn students and shows a lockout notice.
- Print Registration Form, COG, and TOR now block release for withdrawn students with an open balance.
- A blocked document release is written to the document trail as `DOCUMENT_RELEASE_BLOCKED`.
- Student Profile now exposes an explicit **Release Student Number** action for withdrawn records.
- Releasing a withdrawn identity migrates the registrar-owned historical joins from the former live `student_number` onto the immutable `archive_key`, then registers the released number in `student_number_release_registry` as `AVAILABLE`.

EnrollmentLatest working copy:

- Cashier terminal blocks withdrawn/inactive students before timeout checks, pre-registration finalization, financial modeling, or enlistment modeling.
- Walk-in payment screen blocks withdrawn/inactive students before term correction, pre-registration finalization, payment reconciliation, or payment posting.
- Walk-in payment POST rejects withdrawn/inactive student payments.
- Enrollment finalization rejects withdrawn/inactive students.
- Canonical enrollment status sync refuses to reactivate a withdrawn/inactive student.
- Automatic student-number issuance now checks `student_number_release_registry` first, claims an `AVAILABLE` released number when present, and marks it `REISSUED` after successful issuance.

## Explicit Release / Reissue Workflow

Student-number reuse is no longer a blind future idea. It is now controlled by an explicit registrar release step plus an enrollment-side reissue step.

Rules:

- Do not release a student number automatically on withdrawal.
- Release is registrar-owned and available only for already-withdrawn students.
- Historical visibility stays on the archive record and archive key after release.
- Reissue is enrollment-owned at the point of new student-number issuance.
- Do not clear or overwrite live identities outside this workflow, or historical rows may merge into a future enrollee.

## Validation

Commands run:

- `mvn -q -DskipTests compile` in `E:\registrarCanon_canon` - pass
- `mvn -q -DskipTests compile` in `E:\EnrollLatest\enrollment3` - pass
- `mvn -q -DskipTests test-compile` in `E:\registrarCanon_canon` - pass
- `mvn -q -DskipTests test-compile` in `E:\EnrollLatest\enrollment3` - pass
- `mvn -q "-Dtest=WithdrawalServiceDirectDropTest,StudentArchiveCustodyServiceTest,CreditGradeServiceApprovalWorkflowTest" test` in `E:\registrarCanon_canon` - pass
- `mvn -q "-Dtest=WithdrawalServiceDirectDropTest,EnrollmentControllerStudentManagerTest" test` in `E:\registrarCanon_canon` - pass
- `mvn -q "-Dtest=StudentIdentityReleaseServiceTest,WithdrawalServiceDirectDropTest,WithdrawalControllerTest,EnrollmentControllerStudentManagerTest,EnrollmentControllerPrintCorPdfTest" test` in `E:\registrarCanon_canon` - pass
- `mvn -q "-Dtest=ApplicantStudentNumberServiceTest" test` in `E:\EnrollLatest\enrollment3` - pass

Package note:

- `mvn -q -DskipTests package` in registrar reached Spring Boot repackage but failed to rename the WAR artifact because `target\registrar-0.0.1-SNAPSHOT.war` was locked. Compile validation passed after switching away from the locked packaging step.
