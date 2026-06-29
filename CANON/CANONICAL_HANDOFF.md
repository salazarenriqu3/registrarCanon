# Canonical Cross-System Handoff

Last updated: 2026-06-29

This document is the clean handoff for a new agent working the current registrar canon.

Canonical workspace:

- Registrar: `E:\registrarCanon_canon`
- Enrollment: `E:\EnrollLatest\enrollment3`
- Admission: `E:\AdmitLatest\admission`

## 1. Ownership Model

### Admission

- Owns applicant intake
- Owns qualification and admission decisions
- Owns applicant document submission context

### Enrollment

- Owns pre-registration
- Owns irregular / continuing pre-advising
- Owns fee authoring and payment processing
- Owns student-number issuance after payment/enrollment finalization
- Owns dean-side enrollment workflow and enrollment accounting visibility

### Registrar

- Owns academic master data
- Owns programs, curriculum governance, courses, sections, schedules, rooms, and faculty rules
- Owns official academic approval and posting
- Owns withdrawal governance
- Owns TOR and transfer-credit approval
- Owns scholarship review and eligibility enforcement
- Reads fee readiness but does not author the fee side anymore

## 2. What Is Current Canon

- Curriculum-based max load is the source of truth
- Year level and semester are resolved from the assigned curriculum
- Active term overlays the academic graph
- Schedule conflicts must be enforced for room, faculty, and section
- Room monitoring is distinct from slot monitoring
- Shifting clears current-term load without pretending the student has been withdrawn from school
- A shifted student must still be able to bulk-add eligible curriculum subjects
- Withdrawal from school must hard-stop post-withdrawal academic actions
- Registrar only approves grade accreditation submitted from enrollment/dean context

## 3. Recent Changelog Themes

- Cross-system ownership was corrected away from registrar-first irregular and fee language
- Withdrawal and shift flows were hardened
- Curriculum-based max-unit logic replaced global fallback logic
- Scholarship logic was aligned to program, year level, semester, and curriculum assignment
- Grade accreditation approval now expects dean-originated submissions from enrollment
- Transfer-credit and accreditation collation issues were normalized
- Walk-in payment and student lookup edge cases were fixed around reference number mapping
- Demo-ready seed and UAT docs were added for the three-app workflow

## 4. Current Backlog

- Clean up remaining transitional wording in registrar docs and UI
- Keep aligning registrar, enrollment, and admission around the actual ownership model
- Improve demo-safe seed data for rooms, schedules, and academic cases
- Continue hardening withdrawal, shift, and academic approval boundaries
- Keep browser verification in the loop for cross-app changes

## 5. Safe Working Rules

- Do not reintroduce registrar ownership of intake or payments
- Do not assume irregular enrollment is a registrar-owned registration bridge
- Do not use old `C:\...` paths as the canonical workspace
- Do not treat legacy fallback logic as source of truth when explicit curriculum assignment exists
- Preserve audit trail behavior for withdrawals, shift cleanup, grades, and accreditation

## 6. Source Docs

Read these next if you need the fuller context:

- `E:\registrarCanon_canon\handoffNew\CURRENT_STATE_MAP.md`
- `E:\registrarCanon_canon\handoffNew\REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
- `E:\registrarCanon_canon\handoffNew\IMPLEMENTATION_PLAN_CROSS_SYSTEM_REALIGNMENT_20260626.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-29_TRANSFER_CREDIT_DEAN_GATE_NOTE.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-27_PROGRAM_SHIFT_AND_LOAD_CLEANUP_HANDOFF.md`
- `E:\registrarCanon_canon\handoffNew\2026-06-27_REGISTRAR_DEPENDENCY_MATRIX.md`
