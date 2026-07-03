# 2026-07-02 Fresh Post-Fix Three-System Rerun

## Purpose

Record one brand-new applicant run performed **after** the latest D Enrollment walk-in status-heal fix, so we can prove the bug is not only repairable on an old student but also avoided on a fresh real-process identity.

## Canon Roots Used

- Registrar: `E:\registrarCanon_canon`
- Admission: `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Admitmoko1\admission`
- Enrollment: `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

## Live Ports

- Admission: `http://localhost:8081`
- Enrollment: `http://localhost:8082`
- Registrar: `http://localhost:8083/registrar`

## Accounts Used

- Admission admin: `admin-adms / adminadms`
- Enrollment admin: `admin / 1234`
- Registrar admin: `admin / 1234`

## Fresh Live Identity

- Applicant reference: `24A00002`
- Issued student number: `24-1-00002`
- Applicant email: `autoflow.1783006002@example.com`
- Program: `BSIT`

## Real Flow Performed

### 1. Public Admission wizard

The applicant was created through the real public Admission wizard.

Posted live wizard path:

1. `POST /admissions/start`
2. `POST /personal-information`
3. `POST /contact-info`
4. `POST /family-info`
5. `POST /educational-bg`
6. `POST /requirements/upload` for required slots `1, 2, 3, 4`
7. `POST /requirements`
8. `POST /health-info`
9. `POST /submit-enrollment`

Result:

- success page returned `ref=24A00002`
- applicant row created in `applicants`
- four required uploaded files persisted in `student_requirement_files`

### 2. Admission admin qualification

Performed through live Admission admin endpoints:

- verified required requirement files `reqFileId = 5, 6, 7, 8`
- finalized program with `choice=FIRST`
- approved the applicant for enrollment

Result:

- `applicants.reference_number = 24A00002`
- `applicants.final_program_code = BSIT`
- `applicants.applicant_status = ENROLLED` after downstream payment finalization

### 3. Enrollment cashier walk-in payment

Performed through the live Enrollment cashier flow:

- opened `/admin/walkin-payment?keyword=24A00002`
- posted `/admin/process-walkin`
- payment used:
  - `amountPaid = 3000`
  - `amountTendered = 3000`
  - `paymentType = CASH`
  - `remarks = Tuition Fee`

Result:

- redirected to `/admin/walkin-payment?keyword=24-1-00002`
- student number issued: `24-1-00002`

## Shared DB State After Fresh Rerun

Confirmed from the live shared schema:

- `applicants.reference_number = 24A00002`
- `applicants.applicant_status = ENROLLED`
- `applicants.final_program_code = BSIT`
- `sys_users.username = 24-1-00002`
- `sys_users.admission_status = ENROLLED`
- `sys_users.status = ACTIVE`
- `sys_users.enrollment_blocked = 0`
- `sys_users.enrollment_status_type = Regular`
- `students.student_number = 24-1-00002`
- `students.admission_status = ENROLLED`
- `students.status = ACTIVE`
- `students.enrollment_blocked = 0`
- `student_enlistments` has `16` rows for `24-1-00002`
- all `16` rows are `COMMITTED`
- committed load totals `42` units
- one payment row exists for the fresh flow

## Registrar Read Result

Verified through live Registrar login and Student Profile:

- opened `/registrar/admin/student-manager?username=24-1-00002`
- page rendered successfully
- `24-1-00002` present in page output
- `ENROLLED` present in page output
- `Admission Snapshot` present
- `Applicant Documents` present
- `Active subject load found` present

This confirms Registrar can read the newly created upstream student correctly after the patch.

## Follow-Up Closed Same Day

The only small mirror seam found during the first rerun pass:

- `sys_users.enrollment_status_type = Regular`
- `students.enrollment_status_type = NULL`

has now been fixed in the latest D Enrollment canon.

See:

- `2026-07-02_ENROLLMENT_STATUS_TYPE_MIRROR_FIX.md`

Latest verified state for `24-1-00002`:

- `sys_users.enrollment_status_type = Regular`
- `students.enrollment_status_type = Regular`

## Practical Conclusion

The latest D Enrollment walk-in status-heal fix is now validated in two ways:

1. it healed the earlier live student `24-1-00001`
2. it did **not** reintroduce the same status downgrade on the fresh post-fix student `24-1-00002`

That means the current three-system baseline is materially stronger for continued cross-system UAT:

- real applicant creation works
- real staff qualification works
- real cashier issuance works
- Registrar reads the fresh student truthfully
- the old `ENROLLED -> PENDING` walk-in mutation did not recur on this rerun
