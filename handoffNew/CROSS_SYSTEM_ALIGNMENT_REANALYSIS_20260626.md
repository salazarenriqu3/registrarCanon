# Cross-System Alignment Reanalysis

Last updated: 2026-06-26

This document supersedes older cross-system wording that treated Registrar as the canonical owner of:

- irregular/transferee pre-advising and pre-registration generation
- official fee authoring in `program_fee_settings`

The corrected target model below reflects the current intended business ownership for the CAPSS suite.

## Executive Summary

The three systems should be aligned under this ownership model:

- Admission owns applicant intake, review, qualification, archive, and regular-applicant pre-registration.
- Enrollment3 owns irregular/transferee pre-advising, dean review inside Enrollment, irregular pre-registration generation, cashier/accounting, official fee authoring, payment posting, and enrollment finalization.
- Registrar owns academic master data, curriculum, schedules, sections, downstream academic records, and official approval/posting of transfer or TOR credit outcomes that need registrar authority.

This means:

- Registrar should no longer be treated as the canonical home of irregular applicant pre-registration generation.
- Registrar should no longer be treated as the canonical fee authoring application.
- Older registrar irregular-advising and fee-admin implementations now represent historical or transitional code, not the intended steady-state architecture.

## Corrected Ownership Model

### Admission

Admission remains the canonical owner of:

- applicant records in `applicants`
- applicant document intake and review
- reference number generation
- application tracks and school-term intake settings
- qualification workflow through `QUALIFIED FOR ENROLLMENT`
- regular applicant pre-registration snapshots
- applicant-facing PDF/view/export behavior for admission-stage records

Admission is a reader of:

- downstream student linkage via `students.reference_number`
- irregular/transferee pre-registration snapshots produced outside Admission
- shared academic/program structures needed to display applicant outcomes

### Enrollment3

Enrollment3 is the canonical owner of:

- irregular/transferee pre-advising workflow
- dean review and dean-side credit evaluation within the Enrollment workflow
- irregular/transferee pre-registration generation
- cashier and accounting actions
- official fee authoring and maintenance
- payment capture and ledger/accounting execution
- downpayment gates and enrollment finalization
- student number issuance

Enrollment3 is therefore the intended writer for:

- the irregular evaluation snapshot path used by Admission for irregular qualification
- live fee administration paths used by assessment and accounting

Current code evidence already points in this direction:

- `C:\enrollment3\src\main\java\com\example\enrollment\controller\FacultyController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\service\AdmissionPreRegWalkinService.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\service\PreAdmissionDraftCreditService.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\TermFeeAdminController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\service\TermFeeAdminService.java`

### Registrar

Registrar remains the canonical owner of:

- programs, courses, curriculum, and academic term authority
- schedules, faculty loads, sections, slot monitoring, and room monitoring
- student academic records and curriculum assignment
- grading, transfer/TOR credit posting, and academic document outputs
- downstream academic enforcement after enrollment becomes official

Registrar should be treated as:

- a downstream reader of irregular/transferee pre-registration results
- a downstream reader of official fee readiness for academic-term readiness checks
- the authority that approves or posts academic credit outcomes that become part of the official student academic record

Registrar should not be treated as:

- the working application where irregular pre-advising is authored
- the working application where official term/program fees are authored

## Irregular / Transferee Workflow Canon

The intended live path for irregular applicants is:

1. Admission receives the applicant and maintains the intake record.
2. Enrollment3 faculty/dean performs pre-advising and tentative credit evaluation.
3. Enrollment3 generates the irregular/transferee pre-registration snapshot.
4. Admission reads that snapshot and blocks qualification until the irregular evaluation is ready.
5. Cashier/accounting in Enrollment3 handles fees, payments, and enrollment gates.
6. Registrar handles downstream official academic record actions, especially credit posting/approval where registrar authority is required.

Important boundary:

- Dean crediting inside Enrollment3 is workflow preparation and evaluation context.
- Registrar approval/posting is what makes credit academically official when it must land in the formal student record.

## Fee Ownership Canon

The intended live path for fees is:

- Enrollment3 Accounting/Cashier owns fee authoring and fee maintenance.
- Enrollment3 remains the operational home for assessment, term fee management, course fee management, and finance policy.
- Registrar reads fee readiness and fee presence only to determine academic readiness, downstream visibility, and dependent academic workflows.

This means Registrar fee screens and services should be considered transitional until they are converted to read-only or retired.

Current code still shows registrar-side fee authoring paths:

- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminController.java`
- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminService.java`
- `D:\registrarCanon_canon\src\main\resources\templates\admin_term_fees.html`

At the same time, Enrollment3 already exposes accounting-owned fee administration:

- `C:\enrollment3\src\main\java\com\example\enrollment\controller\TermFeeAdminController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\service\TermFeeAdminService.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\CourseFeeAdminController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\FinancePolicyController.java`

So the intended ownership and the current codebase are not fully aligned yet.

## Current Code Reality vs Intended Architecture

### What already aligns with the intended model

- Admission already reads irregular evaluation snapshots from shared tables instead of owning the irregular academic decision itself.
- Admission already supports Enrollment-side irregular snapshot sources through `ENR_PRE_ADVISE`.
- Enrollment3 already has faculty/dean pre-advising and draft-credit workflow infrastructure.
- Enrollment3 already has accounting-facing fee administration controllers and services.

### What still reflects the older model

- Admission docs still contain multiple statements that irregular snapshots are registrar-owned.
- Registrar still ships faculty irregular-advising controllers and snapshot services.
- Registrar still ships active fee-admin services and templates that can author `program_fee_settings`.
- Enrollment3 docs still describe Registrar as the sole writer of `program_fee_settings`.

## Shared Contract Corrections

### Snapshot ownership

Old assumption:

- irregular snapshot is registrar-owned by default

Corrected target:

- irregular snapshot is Enrollment3 pre-advising/dean-owned
- Admission reads it as a qualify gate
- Registrar may read it and may approve/post official academic credit outcomes, but is not the working authoring home

### Fee ownership

Old assumption:

- Registrar is the sole writer of `program_fee_settings`

Corrected target:

- Enrollment3 Accounting/Cashier owns official fee authoring
- Registrar reads fee rows for readiness, assessment visibility, and academic dependency checks

### Registrar role in irregular workflow

Old assumption:

- Registrar owns irregular advising and snapshot generation

Corrected target:

- Registrar owns only the official academic side of credit recognition and downstream student-record effects
- Enrollment3 owns the operational irregular pre-advising workspace

## Affected Documentation That Is Now Stale

The following should be treated as partially stale until updated:

- `D:\registrarCanon_canon\handoffNew\REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
- `D:\registrarCanon_canon\handoffNew\CURRENT_STATE_MAP.md`
- `C:\enrollment3\docs\EAC-Enrollment3-Technical-Manual.md`
- `C:\enrollment3\docs\EAC-Enrollment3-User-and-Technical-Manual.md`
- `C:\admission\docs\HANDOVER.md`
- `C:\admission\docs\TECHNICAL_DOCUMENTATION.md`
- `C:\admission\docs\IRREGULAR_ENROLLMENT_HANDOVER.md`

## Architectural Consequence

The real problem is no longer "which system can technically do this today."

The real problem is:

- several systems can still do parts of the same workflow
- the docs do not consistently name the same canonical owner
- registrar still contains transitional authoring paths that should become read-only or be retired

This creates risk in:

- staff training
- future coding passes
- shared-table schema changes
- demo/UAT assumptions

## Recommended Canon Going Forward

Use this boundary model for future work unless the user explicitly changes it:

- Admission: applicant intake and qualification
- Enrollment3: irregular pre-advising, dean review, fee ownership, cashier/accounting, student number issuance, finalization
- Registrar: academic master data, official academic record authority, downstream approval/posting where registrar authority is required

This document should be read together with:

- `D:\registrarCanon_canon\handoffNew\IMPLEMENTATION_PLAN_CROSS_SYSTEM_REALIGNMENT_20260626.md`

