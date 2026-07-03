# 2026-06-29 Three-App Runtime Follow-Up

## Scope

Live integrated check across:

- `E:\registrarCanon_canon`
- `E:\EnrollLatest\enrollment3`
- `E:\AdmitLatest\admission`

## Fixes Applied

### Registrar applicant document fallback

- File: `src/main/java/com/iuims/registrar/admission/ApplicantDocumentReadService.java`
- Change:
  - Registrar now falls back to the legacy applicant document columns when the normalized admission requirement definitions exist but no `student_requirement_files` rows were created yet.
- Why:
  - In the shared demo database, applicant `DEMO-SANTOS-001` had valid legacy file paths on `applicants.*_path`, but Registrar was preferring normalized requirement definitions with zero uploaded file rows and therefore showed every document as `Not submitted`.

### Admission wording cleanup

- Files:
  - `E:\AdmitLatest\admission\src\main\resources\templates\confirmation.html`
  - `E:\AdmitLatest\admission\src\main\java\com\example\enrollment\service\EmailService.java`
- Change:
  - Remaining qualified-stage wording now points applicants to `Enrollment` instead of `Registrar`.

## Runtime Notes

### Admission startup

- Admission needed explicit MySQL dialect at runtime during this session to start cleanly against the shared database.
- The app also aligned several missing `applicants` columns on boot, including `enrollment_type`.
- A manual schema patch was applied in `eacdb` for:
  - `applicants.qualification_expires_at`

### Enrollment term clearance

- Integrated cashier testing showed term-level accounting clearance was still empty for `term_id = 1`, leaving regular sections at `PENDING_FEE`.
- For this live runtime, the following environment-level data alignment was applied:
  - `term_accounting_clearances.term_id = 1` -> `CLEARED`
  - `class_sections.accounting_status = 'CLEARED'` for regular sections in `term_id = 1`

This removed the `Accounting — Term Not Cleared` blocker from the cashier page.

## Remaining Runtime Gap

### Enrollment cashier assessment mismatch

Student `2026-1001` still shows:

- officially enrolled / locked
- block rows present
- but `Current Tuition Fee (0 Units)` and `₱0.00` assessment values on the cashier screen

This appears separate from the term-clearance blocker and still needs a focused Enrollment-side trace.

## Verified Demo Anchors

- Admission applicant: `DEMO-SANTOS-001`
- Registrar student: `2026-1001`
- Scholarship demos: `SCH-UAT-ELIGIBLE`, `SCH-UAT-LOWUNITS`
- Shift demo: `TSHFT-2026-001`
- Transfer demo: `TTRNS-2026-001`
- Withdrawal demo: `SPRINT-DEMO-2026-001`
