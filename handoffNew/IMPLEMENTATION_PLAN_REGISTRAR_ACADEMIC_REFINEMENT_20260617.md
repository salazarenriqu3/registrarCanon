# Implementation Plan - Registrar Academic Refinement

Last updated: 2026-06-17

Status: Planning / ready for execution after user go-ahead  
Source: `DOC_TO_CODE_STARTER_MAP.md` and `interview 2 clear documentation.docx`  
Apps: Registrar first; Enrollment only when a shared workflow requires verification  
Scope rule: Do not revive the retired admission/pre-reg/dean bridge.
Current delta artifact: `CODE_TO_DOC_DELTA_REGISTRAR_ACADEMIC_20260617.md`

## Goal

Turn the useful parts of the interview document into a controlled registrar-centered improvement track.

This plan focuses on the academic spine:

- policy windows
- student profile and curriculum history
- class scheduling and section control
- withdrawals and program shifts
- academic records, TOR, COG, and evaluation
- scholarships and grading policy

## Non-Goals

- no new applicant intake flow in Registrar
- no retired irregular dean advising bridge
- no production security implementation yet
- no official fee invention without accounting/registrar-approved values
- no broad redesign of admission or enrollment apps

## Dependency Position

This plan sits under the current UAT phase.

Roadmap anchors:

- active demo term stays `1120242025`
- Phase UAT is still open for Sessions C-F
- production security remains deferred
- official production fee rates remain business-data input

This means execution should prefer small, verifiable registrar patches and UAT-friendly improvements.

## Phase A - Confirm Existing Behavior

Goal: Separate what is already implemented from what only exists in the interview document.

### A1. Policy windows and settings audit

Check:

- active term display and resolver
- enrollment period configuration
- class scheduling period configuration
- grading windows and overrides
- add/drop or withdrawal deadline configuration
- late enrollment policy settings

Output:

- mark each as `done`, `partial`, or `missing`
- identify which values are demo defaults versus true configurable policy

Exit criteria:

- one short code-to-doc delta list exists
- no code changes unless a tiny label/readability fix is discovered

### A2. Student Profile audit

Check:

- Student Manager naming versus Student Profile naming
- editable personal / academic fields
- curriculum year assignment and override
- old/new curriculum history preservation
- deficiency and account-block fields
- current curriculum assignment display

Exit criteria:

- clear list of fields that are canonical
- clear list of profile fields that are read-only versus editable

### A3. Scheduling audit

Check:

- `Section` terminology
- block section creation
- room assignment requirements
- subject code plus subject name display
- enrolled/pre-reg count behavior
- close section behavior
- faculty assignment / faculty-load visibility
- regular/tutorial/petition course tagging

Exit criteria:

- class scheduling gaps are ranked by operational risk

## Phase B - Low-Risk Alignment Patches

Goal: Fix naming, visibility, and guardrails that do not disturb shared enrollment behavior.

### B1. Student Profile polish

Candidate changes:

- finish visible rename from `Student Manager` to `Student Profile` where appropriate
- make current curriculum assignment more prominent
- make old/new curriculum history easier to inspect
- show deficiency/account-block state clearly

Acceptance:

- admin can search a student and understand program, curriculum, status, deficiencies, and academic history without opening multiple pages

### B2. Scheduling polish

Candidate changes:

- remove remaining `Group` wording where it means section
- ensure subject name appears beside code in slot/section views
- make room assignment visibly required where schedules are created
- mark block sections, irregular sections, tutorial, and petition sections distinctly

Acceptance:

- class scheduling page clearly separates section type, course identity, room, schedule, and capacity state

### B3. Document output terminology

Candidate changes:

- use `Registration Form` on visible registrar and student screens
- preserve legacy route and template identifiers for compatibility
- ensure printed forms include room assignments where data exists

Acceptance:

- UAT can print an enrolled student's form and see course, section, schedule, and room

Status: implemented; focused print UAT remains.

## Phase C - Workflow Guardrails

Goal: Tighten workflows that can affect official student records.

### C1. Withdrawal workflow

Check and improve:

- use `Withdraw` wording consistently in user-facing screens
- keep reason dropdown / reason reporting visible
- preserve Dean review before Registrar final approval
- confirm withdrawal deadline behavior
- confirm 50 percent / 100 percent tuition charge policy is configurable or clearly marked pending accounting

Acceptance:

- withdrawal flow records reason, status, approver path, and resulting load/ledger effect

### C2. Program shift workflow

Check and improve:

- program shift logs actor, timestamp, from/to program, from/to curriculum
- current-term staged enlistments are cleared or preserved according to explicit rule
- carried/orphan/required curriculum summary is visible
- registration document trail receives a shift event

Acceptance:

- a shifted student shows correct program/curriculum state and a readable audit trail

### C3. Registration document trail

Check and improve:

- which actions write trail events
- search and filters by student, event type, date, actor
- export or print support if already present

Acceptance:

- key academic changes are traceable from Student Profile or Reg Form History

## Phase D - Academic Records And Evaluation

Goal: Preserve and expose academic history cleanly.

### D1. Curriculum evaluation matrix

Check and improve:

- passed, failed, in-progress, missing, and credited subjects
- old/new curriculum separation
- deficiencies against current curriculum
- color coding and legends

Acceptance:

- registrar can inspect a student's standing without manual SQL

### D2. Student grade file

Check and improve:

- TOR view
- COG view
- certification placeholders if present
- editable TOR remarks
- access separation between Registrar, Dean, and Faculty

Acceptance:

- Registrar-only grade file can generate official academic documents and keep evaluation access separate

### D3. Dean evaluation access

Check and improve:

- whether Dean evaluation access exists
- whether Registrar can grant or revoke that access
- what Dean can see versus edit

Acceptance:

- Dean can evaluate academic standing where granted, without gaining registrar-only grade-file permissions

## Phase E - Scholarship And Grading Policy

Goal: Make policy explicit and testable.

### E1. Scholarship criteria

Check and improve:

- label `Max GWA` versus `Lowest Acceptable GWA`
- `Minimum Subjects` versus `Minimum Units`
- configurable completed-unit threshold, current demo/default value `27`
- highest/worst grade display
- registrar endorsement and cashier posting boundary
- no full grade-finalization subsystem is required for the scholarship demo; seeded official grade rows are enough for eligibility UAT

Acceptance:

- scholarship page can explain eligibility from official grades without hidden assumptions
- approval does not affect assessment until an explicit posting action
- each review records status, actor, timestamp, and optional decision note

Status: implemented as a registrar-owned `Pending -> Approved -> Posted` workflow, with Reject and Revoke paths. Enrollment/Cashier code remains untouched.

### E2. Grade configuration

Check and improve:

- prelim, midterm, finals support
- class standing / examination component setup
- per-subject component customization
- faculty access level: no access, view only, encode
- global and per-faculty encoding windows
- dean approval before finalization

Acceptance:

- grading policy is inspectable and aligns with the faculty grade sheet and admin approvals

## Phase F - UAT And Documentation Closure

Goal: Make the improvements testable and keep the docs honest.

Tasks:

- update `HUMAN_UAT_CHECKLIST.md` with only the active scope
- update `MASTER_DEMO_UAT_MANUAL.md` if new UAT steps are added
- append a changelog note to `HANDOFF_UPDATES_20260609.md` or a dated successor
- run targeted compile/tests for touched areas
- run browser or HTTP smoke checks for changed pages

Acceptance:

- `mvn -q -DskipTests compile` passes
- targeted tests for changed services pass where available
- UAT checklist has clear pass/fail steps

## Recommended Execution Order

1. Phase A audit
2. Phase B low-risk alignment patches
3. Phase C workflow guardrails
4. Phase D academic records and evaluation
5. Phase E scholarship and grading policy
6. Phase F UAT and docs

## Must Do Now

- Phase A audit
- Student Profile visibility checks
- Scheduling visibility checks
- Withdrawal/program-shift audit trail checks

## Can Wait

- production security
- official production fee values
- full scholarship cashier posting redesign
- TOR OCR or uploaded TOR parsing
- automated scheduling / room conflict engine
- admission or new-enrollee pre-registration changes

## First Execution Slice

Recommended first slice:

1. Audit Student Profile, Class Scheduling, Withdrawals, Program Shift, TOR/COG, Scholarships, and Grading pages.
2. Produce a code-to-doc delta table.
3. Patch only the smallest high-confidence gaps from that table.
4. Verify compile and targeted smoke checks.
