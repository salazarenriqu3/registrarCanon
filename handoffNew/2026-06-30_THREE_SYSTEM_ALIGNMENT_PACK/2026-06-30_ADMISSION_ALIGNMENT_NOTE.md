# Admission Alignment Note

Date: 2026-06-30

This note is the admission-side companion to the Enrollment-Registrar alignment pack.

## Admission Owns

- Applicant intake and qualification
- Applicant document uploads and validation
- Admission-facing application status before enrollment handoff
- Admission-side read of Registrar active term and school-term sync

## Admission Does Not Own

- Official student number issuance
- Payment collection and cashier settlement
- Official enrollment finalization
- Registrar curriculum, section, room, faculty, or grade authority

## Required Alignment Points

| Point | Admission behavior | Registrar/Enrollment dependency |
| --- | --- | --- |
| Active term | Read Registrar operational term from shared settings | Use `system_settings.CURRENT_ACADEMIC_TERM` or synced term tables |
| Applicant snapshot | Keep applicant records complete and readable | Enrollment needs a stable reference number and document set |
| Documents | Maintain original applicant document trail | Registrar should read and display the document trail after handoff |
| Qualification | Gate applicant eligibility before enrollment | Enrollment should only consume qualified applicants |
| Pre-reg handoff | Provide applicant context to Enrollment | Enrollment owns pre-advising, payment, student number, and finalization |

## Current Admission-Side Integration Hints

- Admission already has a term sync service that reads Registrar active term from `system_settings`.
- Admission should not try to own Registrar academic load or official student records.
- If Admission needs to show an enrollment outcome, it should read the official enrolled state after Enrollment finalization, not recalculate it locally.

## Companion References

1. `D:\registrarCanon_canon\handoffNew\ADMISSION_HANDOFF_IRREGULAR_PRE_REG_REGISTRAR_20260614.md`
1. `D:\registrarCanon_canon\handoffNew\CROSS_SYSTEM_ALIGNMENT_REANALYSIS_20260626.md`
1. `D:\AdmitLatest\admission\src\main\java\com\example\enrollment\service\AdmissionRegistrarTermSyncService.java`

## Short Rule For Future Agents

Admission is the intake gate, Enrollment is the pre-reg and cashier/finalization gate, and Registrar is the official academic gate. Do not let any one of those roles silently absorb the others.
