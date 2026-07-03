# Transfer Credit Dean Gate Note

Date: `2026-06-29`

## Rule

Registrar does not originate TOR or transfer-credit accreditation requests.

The allowed workflow is:

1. Enrollment Dean submits the accreditation request.
2. Registrar reviews the pending request.
3. Registrar approves or rejects it.
4. Only approved requests are posted into `grades`.

Enrollment3 is now the canonical upstream writer for those requests.

## Code Impact

- `transfer_credit_requests` now stores `requested_by_role`.
- `transfer_credit_requests` now also stores `source_system`, `source_table`, and `source_row_id`.
- `CreditGradeService` rejects new requests unless the submitter role is Dean.
- `CreditGradeService` rejects approval or rejection unless the reviewer role is Registrar or Admin.
- Registrar approval now also checks that the pending request was dean-originated.
- Registrar approval/rejection now syncs a decision back to the upstream Enrollment3 source row when that request came from Enrollment3.
- Student Manager now presents TOR accreditation in registrar mode as a review queue, not a registrar-authored request form.

## Enrollment3 Contract

Enrollment3 now bridges dean-approved accreditation into registrar through:

- `ApplicantDeanAccreditationService`
- `PreAdmissionDraftCreditService`
- `RegistrarTransferCreditRequestBridgeService`

The supported Enrollment3 source queues are:

- `applicant_credit_accreditation_lines`
- `tentative_credited_subject`

Requests written from Enrollment3 should carry:

- `requested_by_role = 'Dean'`
- `source_system = 'ENROLLMENT3'`
- `source_table = 'applicant_credit_accreditation_lines'` or `source_table = 'tentative_credited_subject'`
- `source_row_id = <upstream primary key>`

## Compatibility

- If an upstream Enrollment flow writes directly into `transfer_credit_requests`, it should populate the full dean-origin metadata above.
- If `requested_by_role` is missing, registrar approval falls back to resolving the submitter role from `sys_users`.
- Requests created earlier by registrar users will no longer qualify for approval under the new rule.

## Verification

- Covered by `CreditGradeServiceApprovalWorkflowTest`.
- Covered by `EnrollmentControllerStudentManagerTest` for registrar-side visibility.
- Covered by Enrollment3 `RegistrarTransferCreditRequestBridgeServiceTest`.
- Covered by Enrollment3 `PreAdmissionDraftCreditServiceTest` after bridge injection wiring.
