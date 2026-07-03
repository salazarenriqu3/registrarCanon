# 2026-07-02 Live Admission -> Enrollment -> Registrar Demo

## Purpose

This note records one real end-to-end student creation run using the actual Admission flow, the actual Enrollment cashier flow, and the live Registrar profile read. No SQL shortcut was used to create the applicant or issue the student number.

Use this note when we need a concrete runtime proof case for cross-system alignment.

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

## Real Demo Identity

- Applicant reference: `24A00001`
- Issued student number: `24-1-00001`
- Applicant email: `jordan.demoflow.20260702194011@example.com`
- Program: `BSIT`

## Real Flow Performed

### 1. Admission applicant creation

The applicant was created through the actual Admission wizard, including the required document uploads and the wizard timing gate.

Important runtime detail:

- Admission portal gate accepted the live dummy Turnstile token:
  - `XXXX.DUMMY.TOKEN.XXXX`

Required uploads that worked in this run:

- `emblem.png`
- `eac-grad.jpg`

Applicant result after submit:

- `applicants.reference_number = 24A00001`
- initial applicant status after public submit: `PENDING`

### 2. Admission staff processing

Admission admin actions performed on the real applicant:

- verified all 4 submitted requirement files
- finalized final program using `FIRST`
- approved / qualified the applicant

Result after Admission staff processing:

- `applicants.applicant_status = QUALIFIED FOR ENROLLMENT`
- `applicants.final_program_code = BSIT`
- `applicant_pre_reg_snapshots` row created

### 3. Enrollment cashier walk-in payment

Enrollment cashier search used the applicant reference:

- `/admin/walkin-payment?keyword=24A00001`

Observed pre-reg assessment:

- total assessment: `88360.00`
- required downpayment: `3000.00`

Payment posted through the real cashier path:

- amount paid: `3000.00`
- payment type: `CASH`

Result:

- student number issued: `24-1-00001`
- cashier redirected to the live student number

### 4. Shared DB state immediately after issuance

Confirmed positives:

- `applicants.applicant_status = ENROLLED`
- `payments` row exists for `24-1-00001`
- `applicant_payments.status = PROCESSED`
- `student_enlistments` contains 16 `COMMITTED` rows for `24-1-00001`
- `student_curriculum_assignments` contains the default BSIT assignment

## Registrar Read Result

Opening:

- `/registrar/admin/student-manager?username=24-1-00001`

Registrar successfully showed:

- student profile
- admission snapshot
- applicant documents with live view/download links
- committed current subject load
- current curriculum assignment
- mini ledger snapshot
- program shift panel
- withdrawal / shift cleanup controls

This proves the registrar-side applicant document bridge and admission snapshot bridge are functioning for a real student created through the actual upstream process.

## Important Runtime Mismatch Found

The same live student displayed contradictory states across the shared system:

- Registrar profile header state: `PENDING`
- Registrar profile state card: `PENDING`
- Admission snapshot inside Registrar: `ENROLLED`
- current subject load inside Registrar: populated, 42 units
- shared `applicants` row: `ENROLLED`

Direct DB read at the time of this pass:

- `applicants.applicant_status = ENROLLED`
- `sys_users.admission_status = PENDING`
- `students.admission_status = PENDING`

## Root Cause Found

The latest D Enrollment cashier still has a repair-on-view mutation path on walk-in load.

Relevant file:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3\src\main\java\com\example\enrollment\controller\AdminController.java`

Relevant behavior:

- on walk-in page load, the code recomputes `officiallyFinalized`
- if that flag is not true, it can set `student.applicantStatus = PENDING`
- `financialService.onTransitionToPending(...)` then persists the student back to the DB

Relevant method:

- `FinancialService.onTransitionToPending(...)`

Persistence path:

- `resetEnrollmentSessionTimer(student)`
- `studentRepository.saveAndFlush(student)`
- `studentProfileService.syncStudentRowFromSysUser(student)`

Practical meaning:

- opening the cashier/walk-in surface can still downgrade a truly enrolled student back to `PENDING`
- this is a real cross-system bug, not just a registrar label issue

## Why This Matters To Registrar

Registrar is currently reading truthful downstream academic facts:

- committed load exists
- curriculum assignment exists
- admission snapshot shows enrolled applicant state

But the registrar profile header still trusts the shared `students/sys_users.admission_status`, so the page presents the student as `PENDING` even after real enrollment.

That means:

- registrar display can become misleading
- later registrar actions that key off status may be incorrectly enabled or disabled
- any downstream test involving active-vs-pending state can be contaminated after cashier view load

## Current Go / No-Go Reading

### Confirmed working

- real applicant creation through Admission
- real document upload through Admission
- real qualification / program finalization through Admission
- real pre-reg snapshot generation
- real cashier payment through Enrollment
- real student-number issuance
- real committed enlistment creation
- real curriculum assignment creation
- registrar read of admission snapshot
- registrar read of applicant documents
- registrar read of committed subject load

### Not yet aligned

- shared live status propagation after enrollment finalization
- cashier / walk-in page must stop mutating enrolled students back to `PENDING`
- registrar should not rely on a drifted status field when stronger downstream signals already show official enrollment

## Recommended Next Patch Order

1. Fix the Enrollment walk-in mutation first.
2. Re-run the exact same real flow with a fresh applicant.
3. Re-open the same student in Registrar and verify:
   - `students.admission_status = ENROLLED`
   - `sys_users.admission_status = ENROLLED`
   - registrar profile header shows `ENROLLED`
   - current load still remains committed
4. Only after that continue broader three-system UAT, because this drift can poison later tests.
