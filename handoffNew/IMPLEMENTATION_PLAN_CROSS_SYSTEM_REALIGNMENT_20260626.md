# Implementation Plan - Cross-System Realignment

Last updated: 2026-06-26

This plan converts the corrected ownership model into an implementation sequence.

Use this plan when the target architecture is:

- Admission owns intake and qualification
- Enrollment3 owns irregular/transferee and continuing-student pre-advising, drafts, fees, payment gates, and finalization
- Registrar owns downstream official academic authority

## Goal

Make code, UI, and documentation agree on two corrected boundaries:

1. irregular/transferee and continuing-student pre-advising, draft generation, and finalization belong to Enrollment3, not Registrar
2. official fee authoring belongs to Enrollment3 Accounting/Cashier, while Registrar reads fee readiness and performs downstream academic checks or posting only

## Success Criteria

The realignment is complete when:

- Admission qualify gates refer to Enrollment3-owned irregular evaluation, not registrar-owned authoring
- Enrollment3 is the only active fee authoring surface
- Registrar fee tooling is read-only or retired
- Registrar irregular authoring flows are historical only and should not be treated as the active workflow home
- shared docs across all three repos describe the same ownership model

## Phase 1 - Documentation and Contract Freeze

### Objective

Stop future agents from coding against the wrong ownership model.

### Actions

- Update Registrar handoff docs to state that registrar irregular authoring and registrar fee ownership are historical/transitional.
- Update Enrollment3 docs to state:
  - Enrollment3 owns irregular/transferee and continuing-student pre-advising
  - Enrollment3 owns official fee authoring
  - Registrar is no longer the fee authoring source of truth
- Update Admission docs to state:
  - irregular qualification depends on Enrollment3 pre-advising output
  - registrar is not the default irregular snapshot author

### Deliverables

- corrected handoff docs in Registrar
- corrected technical/user docs in Enrollment3
- corrected handoff/technical docs in Admission

## Phase 2 - Irregular Workflow Contract Realignment

### Objective

Make Admission, Enrollment3, and Registrar agree that the irregular working flow starts and lives in Enrollment3.

### Actions

- Confirm one canonical snapshot source code for Enrollment-owned irregular evaluation.
  - Recommended: keep `ENR_PRE_ADVISE` if already widely used.
- Update Admission text, labels, and validation messages to say:
  - waiting for Enrollment3 pre-advising / dean evaluation
  - not waiting for Registrar to author the pre-reg
- Update Admission reader/service terminology so `RegistrarSnapshotReader` becomes neutral in naming.
  - Possible rename target: `IrregularPreRegSnapshotReader`
- Audit all user-facing links in Admission that still point staff toward Registrar for irregular authoring.
- Preserve Registrar's downstream read/approval role where official posting is needed.

### Likely files

- `C:\admission\src\main\java\com\example\enrollment\service\IrregularEnrollmentService.java`
- `C:\admission\src\main\java\com\example\enrollment\service\prereg\RegistrarSnapshotReader.java`
- `C:\admission\docs\HANDOVER.md`
- `C:\admission\docs\IRREGULAR_ENROLLMENT_HANDOVER.md`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\FacultyController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\service\PreAdmissionDraftCreditService.java`

### Acceptance checks

- An irregular applicant can reach qualification using Enrollment3-owned pre-advising output only.
- Admission UI/messages consistently point staff to Enrollment3 for incomplete irregular evaluation.
- Registrar is no longer presented as the authoring home of that workflow.

## Phase 3 - Fee Ownership Migration

### Objective

Move fee ownership fully to Enrollment3 and make Registrar read-only for fee readiness.

### Actions

- Keep Enrollment3 `TermFeeAdminController`, `CourseFeeAdminController`, and finance policy screens as the active authoring surface.
- Convert Registrar fee admin to one of these modes:
  - preferred: read-only readiness dashboard
  - acceptable transition: hidden from navigation except for historical/admin fallback
- Remove or block Registrar write paths for:
  - `saveFeeRate`
  - fee imports that create official term rows
  - CSV upload/import that mutates official fee rows
- Replace Registrar fee wording from "official fee entry/import" to "fee readiness visibility" where applicable.
- Preserve fee-readiness summaries in Registrar because academic workflows still depend on them.

### Likely files

- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminController.java`
- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\finance\TermFeeAdminService.java`
- `D:\registrarCanon_canon\src\main\resources\templates\admin_term_fees.html`
- `D:\registrarCanon_canon\src\main\resources\templates\admin_finance_policy.html`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\TermFeeAdminController.java`
- `C:\enrollment3\src\main\java\com\example\enrollment\controller\CourseFeeAdminController.java`

### Acceptance checks

- Staff can author and maintain official fees only in Enrollment3.
- Registrar can still inspect readiness and missing scopes without mutating fee data.
- No active handoff doc repeats the old Registrar-only fee writer claim.

## Phase 4 - Registrar Irregular Tooling Cleanup

### Objective

Retire or neutralize registrar-side irregular authoring code that conflicts with the new canon.

### Actions

- Reclassify registrar irregular tools as one of:
  - historical/remnant
  - read-only viewer
  - downstream approval/posting tool only
- If keeping any registrar-side credit approval tool:
  - scope it narrowly to official academic posting
  - avoid duplicating the Enrollment3 authoring workspace
- Remove or hide registrar UI language that suggests registrar is the primary home of irregular pre-reg generation.

### Likely files

- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\faculty\FacultyIrregularAdvisingController.java`
- `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\admission\ApplicantPreRegSnapshotService.java`
- `D:\registrarCanon_canon\src\main\resources\templates\faculty_irregular_advising.html`
- `D:\registrarCanon_canon\src\main\resources\templates\admin_admission_acceptance.html`

### Acceptance checks

- Registrar no longer looks like the main authoring app for irregular pre-registration.
- If registrar still touches this domain, the UI clearly says it is an approval/posting or historical surface.

## Phase 5 - Shared Naming and Terminology Cleanup

### Objective

Reduce confusion caused by legacy names.

### Actions

- Rename neutral readers/services where practical.
  - `RegistrarSnapshotReader` -> `IrregularPreRegSnapshotReader`
- Update message copy:
  - legacy source-owner messages -> "waiting for Enrollment3 pre-advising"
  - "registrar fee setup" -> "fee readiness from enrollment accounting"
- Update demo/UAT instructions across repos.

### Acceptance checks

- Staff-facing copy matches the real owning app.
- New agents can infer the correct ownership from file names and docs without needing oral history.

## Phase 6 - UAT and Regression Pass

### Objective

Prove the corrected boundary works end to end.

### UAT flows

1. Regular applicant
   - Admission intake
   - Admission qualification
   - Enrollment3 payment
   - student number issuance
   - enlistment finalization
   - Registrar downstream visibility

2. Irregular/transferee applicant
   - Admission intake
   - Enrollment3 faculty/dean pre-advising
   - Enrollment3 irregular pre-reg generation
   - Admission qualification gate clears
   - Enrollment3 payment/finalization
   - Registrar official credit/posting or downstream academic visibility

3. Fee ownership
   - fee updated in Enrollment3
   - Registrar readiness view reflects change
   - no registrar-side write path needed

## Implementation Notes

### Important current mismatch

Current code still contains registrar-side fee authoring and registrar-side irregular snapshot authoring.

This means the realignment is not a pure documentation pass. It is a controlled migration from:

- dual-capable or overlapping code

to:

- one canonical author per workflow

### Risk control

Do not remove registrar-side code blindly.

Instead:

- first correct docs and user-facing labels
- then convert registrar write paths to read-only or hidden
- then run shared-db smoke tests

## Recommended Execution Order

1. Documentation correction across all three repos
2. Admission irregular message/link cleanup
3. Registrar fee UI conversion to read-only
4. Registrar irregular authoring UI retirement or narrowing
5. Shared UAT and handoff refresh

## Resulting Canon

After this plan is completed, future agents should assume:

- Admission starts the applicant lifecycle
- Enrollment3 operationalizes irregular advising and finance
- Registrar approves or posts the official academic consequences after Enrollment3 finalization
