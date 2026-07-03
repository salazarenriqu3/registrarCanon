# Testing Storyboard

This storyboard is meant for a real three-system browser pass after the database is prepared.

## Test 0 - Startup And Readiness

Systems:

- Admission: `http://localhost:8081/`
- Enrollment: `http://localhost:8082`
- Registrar: `http://localhost:8083/registrar`

Expected:

- All apps start without fatal exceptions.
- All apps point to the same `eacdb`.
- `sql\03_verify_three_system_demo_ready.sql` returns rows for active term, smoke applicant, LEC/LAB sample, governance tables, and pre-reg `sort_order`.
- `sql\04_verify_lec_lab_family_migration.sql` confirms there are no legacy mixed lecture/lab rows left and that split families are present.

## Test 1 - Admission Applicant Intake

System owner: Admission.

Use:

- Existing seeded applicant from the registrar/admission demo data, or `3SYS-SMOKE-0001` from the SQL pack.

Expected:

- Applicant is visible in Admission.
- Applicant status can reach or already shows `QUALIFIED FOR ENROLLMENT`.
- Uploaded document metadata remains Admission-owned but must later be readable from Registrar student profile once a student identity exists.

Do not expect:

- Admission to issue a student number.
- Admission to commit official Registrar load.

## Test 2 - Enrollment Cashier And Finalization

System owner: Enrollment.

Expected:

- Enrollment consumes qualified applicant context.
- Cashier/pre-registration path resolves to real Registrar course and section rows.
- Student number issuance follows Enrollment format and may consume released numbers only when `student_number_release_registry.release_status = 'AVAILABLE'`.
- Official enrollment only counts after payment/finalization and committed `student_enlistments` rows exist.

Watch for:

- Ledger pages must not mutate enrolled students back to pending.
- No student should become `ENROLLED` with zero committed current-term enlistments.
- Withdrawn students must be blocked from cashier/enlistment/finalization except explicitly allowed historical ledger access.

## Test 3 - Registrar Student Profile

System owner: Registrar.

Expected:

- Student profile can read identity, current load, finance summary, applicant snapshot, applicant documents, withdrawal history, reg-form history, and document trail.
- Editable profile is Registrar-owned for student record correction.
- Applicant documents are viewable/downloadable when paths exist and every document view/download action is trailed.

Watch for:

- Profile search for withdrawn records should use archive key or name when live student number has been released.
- Released student numbers must not reopen the old withdrawn profile after reissue.

## Test 4 - Curriculum And LEC/LAB Components

System owner: Registrar.

Expected:

- Course Catalog shows component badges: `Lecture component`, `Laboratory component`, or `Single course`.
- Creating a mixed lecture/laboratory course creates separate `CODE-LEC` and `CODE-LAB` rows.
- Curriculum manual add also creates separate rows for mixed courses.
- Existing already-used mixed rows are not auto-split without a deliberate migration.

Verify with:

```sql
SELECT course_code, credit_units, lec_units, lab_units, component_type, course_family_code
FROM courses
WHERE course_family_code = 'DEMOCP'
ORDER BY component_type;
```

Also verify the migration health check:

```sql
SELECT
    'LEC_LAB_MIGRATION_HEALTH' AS check_name,
    (SELECT COUNT(*)
     FROM courses
     WHERE COALESCE(lec_units, 0) > 0
       AND COALESCE(lab_units, 0) > 0
       AND COALESCE(component_type, 'SINGLE') NOT IN ('LEC', 'LAB', 'LEGACY')
    ) AS legacy_mixed_rows_remaining,
    (SELECT COUNT(*)
     FROM courses
     WHERE component_type = 'LEGACY'
       AND COALESCE(lec_units, 0) > 0
       AND COALESCE(lab_units, 0) > 0
    ) AS archived_legacy_rows,
    (SELECT COUNT(*) FROM courses WHERE component_type = 'LEC') AS lec_rows,
    (SELECT COUNT(*) FROM courses WHERE component_type = 'LAB') AS lab_rows,
    (SELECT COUNT(DISTINCT course_family_code)
     FROM courses
     WHERE component_type IN ('LEC', 'LAB')
    ) AS split_family_count;
```

## Test 5 - Scheduling And Room Constraints

System owner: Registrar.

Expected:

- Class scheduling requires concrete room assignment.
- Same-term room overlap is blocked.
- Same-term faculty overlap is blocked.
- Same-section schedule overlap is blocked.
- Room Monitoring shows room utilization and conflict status separately from Slot Monitoring.

## Test 6 - Program Shift

System owner: Registrar for enrolled students.

Expected:

- Pre-enrollment shift is a quick target program/curriculum/year/semester assignment.
- Post-enrollment shift clears all current-term subjects as `SHIFT_PROGRAM_CLEANUP`, without withdrawing the student from school.
- The student can temporarily have zero current-term load and still remain shiftable/addable if current curriculum assignment exists.
- Destination curricula are filtered by target program only.

## Test 7 - Withdrawal Governance

System owner: Registrar.

Expected:

- Subject withdrawal applies class-line history and charges where applicable.
- Full school withdrawal archives the student, blocks future active actions, and optionally releases the student number.
- Official document release is blocked while a withdrawn student has outstanding balance.
- Historical records remain searchable through archive key/name.

## Test 8 - Grade Governance And Scholarship

System owner: Registrar.

Expected:

- Registrar does not revive a full grading system.
- SQL-fed official grades are visible for student records and scholarship checks.
- Grade approval/rejection/change events are tracked in `grade_record_events`.
- Academic scholarship evaluates GWA, period grade caps, assigned curriculum load, and PE/NSTP disqualification in 3rd/4th year.

## Test 9 - Accreditation

System ownership:

- Enrollment Dean authors/submits accreditation.
- Registrar approves/rejects and posts official credit.

Expected:

- Registrar sees pending accreditation/credit requests from Enrollment-origin tables.
- Approved credits become official Registrar-readable academic records.
- This can support transferees and shifted students when a dean judges equivalent courses as accreditable.
