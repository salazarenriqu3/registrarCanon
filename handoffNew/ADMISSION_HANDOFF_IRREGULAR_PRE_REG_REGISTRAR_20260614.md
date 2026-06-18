# Admission Handoff: Retired Registrar Irregular Pre-Reg Bridge

Date: 2026-06-14  
Updated: 2026-06-17
Status: Dormant / historical only

## Summary

This document used to describe a registrar-owned bridge where Dean / Faculty users performed irregular new-enrollee advising inside Registrar and produced pre-registration snapshots for Admission.

That bridge is no longer part of the active registrar canon.

Registrar should not be treated as the live owner of:

- irregular applicant advising before admission
- registrar-authored pre-registration snapshots for new enrollees
- applicant intake gating for Admission
- regular applicant pre-registration, sectioning, payment, enrollment, or student-number issuance

## Current Canon

For current scope:

- regular applicant admission, pre-registration, section assignment, payment, enrollment, and student-number issuance remain outside Registrar
- irregular/transferee new-enrollee intake should also be treated as external to Registrar until a future business decision explicitly revives that bridge
- Registrar begins once there is an actual student record to manage academically
- Registrar remains the canonical home for curriculum, courses, schedules, sections, Student Manager, TOR / transfer crediting, program shift, grading, approvals, and reporting

## What To Ignore From Older Notes

Do not use this file as an active contract for:

- `/registrar/faculty/irregular-advising`
- `/registrar/admin/admission-acceptance` as an intake gate
- `applicant_pre_reg_snapshots`
- `applicant_pre_reg_subject_lines`
- `applicant_pre_reg_credit_lines`

Those routes and tables may still exist in code or schema, but they are not active acceptance targets for the current registrar-centric scope.

## Historical References

If this bridge is ever revived later, the previous implementation lived around:

```text
src/main/java/com/iuims/registrar/admission/ApplicantPreRegSnapshotService.java
src/main/java/com/iuims/registrar/admission/AdmissionController.java
src/main/java/com/iuims/registrar/faculty/FacultyIrregularAdvisingController.java
src/main/resources/templates/faculty_irregular_advising.html
src/main/resources/templates/admin_admission_acceptance.html
src/main/resources/templates/grades_menu.html
db/demo_scripts/15_seed_irregular_admission_alignment.sql
```

Treat those as historical references only until the scope is explicitly restored.
