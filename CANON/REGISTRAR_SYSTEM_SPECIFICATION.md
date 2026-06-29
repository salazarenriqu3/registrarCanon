# Registrar System Specification

Canonical workspace: `E:\registrarCanon_canon`

## Scope

Registrar is the academic authority and downstream records system.

Registrar owns:

- academic master data
- departments, programs, curricula, courses
- schedules, sections, rooms, and faculty assignment enforcement
- official student academic records
- withdrawal governance
- TOR / transfer credit approval
- scholarship review and eligibility enforcement
- grade accreditation approval from enrollment/dean submissions

Registrar does not own:

- applicant intake
- pre-registration generation
- cashier/payment processing
- fee authoring
- student-number issuance

## Source of Truth

- Active term comes from the shared term authority, not ad hoc local fallbacks
- Curriculum assignment governs max load
- Explicit curriculum assignment is required for subject placement
- Room, faculty, and section conflicts are hard-checked

## Current Behavior to Preserve

- shifted students remain active unless they are explicitly withdrawn from school
- shifting clears current-term load but should not destroy the assigned curriculum context
- withdrawal from school must block further academic actions
- registrar only approves grade accreditation submitted through enrollment context

## Read Next

- `CANON/CANONICAL_HANDOFF.md`
- `CANON/PROJECT_STATE_MAP.md`
- `CANON/SOURCE_DOC_MAP.md`
