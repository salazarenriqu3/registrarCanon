# Registrar Master Implementation Plan

Last updated: **2026-06-14**  
Scope: **Registrar app only** (`C:\newer\new\registrar`)  
Cross-system note: Admission may read registrar-owned shared tables for irregular applicants, but this plan does **not** include Enrollment-side implementation work.

---

## 1. Purpose

This plan consolidates:

- the current Registrar codebase state
- the latest registrar handoff and roadmap docs
- the irregular/transferee shared-DB workflow notes
- the FRD registrar requirements

It is intended to be the working execution plan for all registrar-side work that is still missing, incomplete, risky, or only partially aligned with business rules.

---

## 2. Ground truth used for this plan

### Current registrar baseline

- Demo/stabilization work is largely complete per `PROJECT_STATUS_AND_ROADMAP.md`.
- Existing registrar modules already include:
  - Student Manager
  - class scheduling with block sections and special-section exceptions
  - TOR / transfer crediting tools
  - program shift
  - scholarship controls
  - finance policy and term fees
  - prior-term overpayment disposition phase 1
- Human UAT is still incomplete for some tracks.

### New registrar obligations from irregular handoff

- Registrar must own the irregular applicant advising / manual enlist workflow.
- Registrar must write finalized pre-reg data into:
  - `applicant_pre_reg_snapshots`
  - `applicant_pre_reg_subject_lines`
- Admission reads registrar-written data only.
- Irregular readiness is driven by registrar-owned subject lines, not by block curriculum fallback.

### New registrar obligations from FRD

- Formal Withdrawal workflow with Dean then Registrar approval
- Student Manager renamed/reframed as Student Profile
- Registrar editing of profile details including curriculum year
- Dean + Registrar support for irregular/manual selection
- special/custom/make-up sections
- section closure, schedule control, slot monitoring, and student list visibility
- Student Evaluation shared with Dean
- Student Grade File restricted to Registrar
- reporting and auditability improvements

---

## 3. Current-state assessment

### What is already usable

- `admin_student_manager.html` already acts as a registrar operations hub.
- `EnrollmentController` already supports:
  - student search and roster
  - current load viewing
  - add/drop subject
  - TOR crediting
  - program shift
  - installment override
  - overpayment disposition
- `AcademicController` + `admin_class_scheduling.html` already support:
  - block creation
  - per-course section opening
  - faculty assignment
  - schedule slot creation/removal
  - section closing
- `JaypeeIntegrationService` already has cross-system add/drop primitives.

### What is partially implemented but not aligned yet

- Dean-side student manager exists as an older lightweight screen, but not as the registrar-owned irregular advising workflow required by the handoff.
- Student Manager can already do manual add/drop for enrolled students, but not applicant pre-reg snapshot authoring for irregular applicants.
- Program shift and curriculum tools exist, but the FRD’s broader “Student Profile” concept is not yet reflected in naming, permissions, workflow, or data structure.
- Class scheduling supports blocks and special-section exceptions, but not the full slot-monitoring/reporting operations described in the FRD.
- Current “drop” behavior is operational subject removal, not the FRD’s formal Withdrawal process.

### Critical gaps / risks found in code

- Security is still pilot-grade:
  - multiple state-changing registrar endpoints are session-light or effectively open
  - boot-time password reset still exists
- Student curriculum behavior appears inconsistent with the latest docs:
  - `StudentCurriculumService` still contains `LEGACY_BACKFILL` behavior in read paths
- Dashboard counts likely misread enrollment status because term/status conventions differ across code paths.
- No registrar-side implementation was found yet for:
  - `applicant_pre_reg_snapshots`
  - `applicant_pre_reg_subject_lines`
  - irregular applicant readiness authoring
  - registrar-owned irregular pre-reg PDF source

---

## 4. Implementation principles

1. Registrar remains the source of truth for registrar-owned data.
2. Irregular applicant workflow must be explicit, not inferred from block curriculum behavior.
3. New workflows must preserve existing demo-ready modules unless intentionally replaced.
4. Rename/reframe before expanding operator usage where terminology is misleading.
5. Security hardening must happen before broadening role-based registrar/dean workflows.
6. Every registrar workflow should leave an audit trail.

---

## 5. Workstreams

## Workstream A. Foundation and production-safety fixes

### Goal

Stabilize registrar internals before adding more workflow surface area.

### Deliverables

- implement Spring Security per the existing registrar proposal
- remove boot-time forced password resets
- define role matrix for:
  - Head Registrar
  - Registrar Staff
  - Dean
  - Faculty
  - Read-only / reporting roles if needed
- secure all state-changing endpoints
- add basic audit logging for registrar actions
- fix curriculum fallback behavior to match current handoff intent
- fix dashboard enrollment counters

### Backend tasks

- Introduce security configuration, login protection, CSRF strategy, route authorization, and password encoding.
- Move current session checks to consistent authorization rules.
- Lock down:
  - Student Manager mutations
  - class scheduling mutations
  - user admin
  - grading admin
  - finance and policy updates
- Remove or feature-flag the `CommandLineRunner` password reset behavior.
- Refactor curriculum reads so they do not silently create `LEGACY_BACKFILL` assignments.
- Add service-level validation so curriculum assignment / shift cannot create program-mismatched assignments.
- Reconcile portal dashboard counters with actual status fields used by registrar/admission sync.

### Data / schema tasks

- add audit tables or audit columns for registrar-controlled writes
- optionally add role/permission seed updates if existing user model is too coarse

### Exit criteria

- no registrar mutation endpoint is publicly writable without authorization
- no boot process resets credentials in normal runtime
- curriculum reads are side-effect free
- dashboard counts are verified against seeded test cases

---

## Workstream B. Irregular applicant advising and registrar-owned pre-reg snapshots

### Goal

Build the registrar workflow required by the irregular/transferee handoff, using shared DB tables as the registrar-owned source of truth for irregular applicant pre-registration.

### Deliverables

- registrar UI for dean/registrar advising of irregular applicants
- applicant search/open by `applicant_id` and/or `reference_number`
- snapshot header creation/update in `applicant_pre_reg_snapshots`
- subject-line authoring in `applicant_pre_reg_subject_lines`
- explicit finalization flow for registrar-owned irregular pre-reg
- readiness indicator based on registrar-owned subject lines
- registrar-owned PDF source selection for irregular applicants

### Functional rules

- Only irregular applicants use this workflow.
- Regular applicants continue using the existing block-first path.
- Registrar writes finalized subject selection to shared tables.
- Readiness for irregular applicants must be explicit and visible.
- Admission should not need to scrape Enrollment or derive irregular load elsewhere.

### Recommended shared-table contract

- snapshot header linked by applicant identity
- `snapshot_source = 'REGISTRAR'`
- `program_code` aligned to the applicant’s approved/final program
- header contains enough fee/assessment metadata for irregular PDF generation
- subject lines are the minimum readiness proof
- optional `evaluation_finalized_at` timestamp for explicit finalization

### Backend tasks

- create a registrar service layer for applicant pre-reg snapshots
- add CRUD/finalize methods for snapshot header and subject lines
- validate:
  - applicant exists
  - applicant is irregular
  - program_code is set
  - subject lines are non-empty before finalization
- add query methods for:
  - open advisory cases
  - finalized cases
  - cases missing subject lines
  - cases with program mismatch
- expose registrar-only controllers/pages for this workflow

### UI tasks

- add an irregular applicant workspace, likely separate from current Student Manager
- show:
  - applicant identity
  - irregular badge
  - target program
  - manual subject selection grid
  - assessment/header fields
  - draft vs finalized status
  - warnings for empty subject lines or mismatched program
- support add/remove/reorder subject lines
- include finalize / reopen actions

### Data tasks

- confirm physical schema of:
  - `applicant_pre_reg_snapshots`
  - `applicant_pre_reg_subject_lines`
- add indexes if missing:
  - `applicant_id`
  - `reference_number`
  - finalized status / source fields
- add audit columns if absent:
  - created_by
  - updated_by
  - finalized_by
  - finalized_at

### Testing

- irregular applicant with zero lines stays not ready
- irregular applicant with lines and registrar finalization becomes ready
- regular applicant is excluded from this workflow
- program mismatch blocks finalization
- reopening/finalizing preserves line integrity

### Exit criteria

- registrar can fully author irregular pre-reg without touching enrollment-side logic
- shared snapshot tables contain authoritative registrar output
- readiness rule is explicit and demonstrable in UI and DB

---

## Workstream C. Student Manager to Student Profile conversion

### Goal

Turn the current operations page into the FRD-aligned registrar workspace.

### Deliverables

- rename UI and navigation from **Student Manager** to **Student Profile**
- preserve current operational capabilities
- add profile-centric sections and cleaner registrar semantics
- enable curriculum-year and profile-field editing with guardrails

### Functional scope

- identity and contact details
- academic attributes
  - program
  - year level
  - curriculum / curriculum year
  - student type / regularity
- finance summary
- current load
- academic history
- TOR crediting
- program shift
- registrar actions / audit history

### Backend tasks

- identify which fields are safe for direct registrar edits vs derived fields
- add dedicated update endpoints instead of overloading unrelated flows
- separate:
  - profile edits
  - curriculum assignment
  - regularity classification
  - program shift

### UI tasks

- rename page title, route labels, and references
- group profile data into:
  - identity
  - academic placement
  - current term load
  - finance
  - transfer/crediting
  - history
- add visible indicators for:
  - regular vs irregular
  - assigned curriculum
  - pending financial holds
  - pending overpayment disposition

### Data tasks

- confirm whether curriculum year needs a new explicit field or can be derived from assigned curriculum
- add change-audit rows for registrar profile edits

### Exit criteria

- operators use a true Student Profile workspace
- FRD language is reflected in the registrar UI
- curriculum-related edits are explicit and auditable

---

## Workstream D. Formal Withdrawal module

### Goal

Replace simple drop-only handling with the FRD’s formal Withdrawal workflow.

### Deliverables

- Withdrawal request per subject
- reason dropdown / reason master list
- approval chain: Dean then Registrar
- deadline enforcement until midterm
- tuition charge matrix by timing
- regenerated Reg Form after approval
- reporting by withdrawal reason

### Current-state gap

- Current registrar flow uses direct subject drop operations.
- No formal withdrawal entity/workflow was found.
- No per-subject withdrawal reason reporting model was found.

### Backend tasks

- create withdrawal domain model:
  - header/request
  - line items per subject
  - reason code
  - dean approval
  - registrar approval
  - effective timestamps
- integrate with existing enlistment and finance recalculation logic
- enforce deadline based on academic calendar / policy settings
- compute tuition/fee effect from a configurable matrix
- support cancellation / rejection states

### UI tasks

- add Withdrawal action from Student Profile current load
- replace raw “Drop” wording in registrar-facing UI where FRD requires “Withdrawal”
- show approval pipeline status and outstanding approver
- present reason dropdown and optional remarks
- regenerate/print updated Reg Form after approval

### Data tasks

- add:
  - withdrawal reasons master table
  - withdrawal requests table
  - withdrawal subject lines table
  - charge-matrix configuration table if not stored elsewhere
- preserve financial and academic audit trail

### Reporting tasks

- list withdrawals by:
  - date
  - reason
  - program
  - approver
  - deadline timing bucket

### Exit criteria

- withdrawal is no longer a silent direct subject deletion
- every withdrawal is approved, timestamped, reasoned, and reportable

---

## Workstream E. Dean and Registrar shared evaluation workflow

### Goal

Support FRD requirements for student evaluation shared between Dean and Registrar, with clearer responsibility boundaries.

### Deliverables

- shared student evaluation view
- curriculum-progress view with year-color coding
- dean advisory input where needed
- registrar final academic placement / recording actions

### Recommended approach

- Keep the authoritative registrar-side student profile and curriculum data in registrar.
- Allow dean-scoped access to evaluation/advising screens only.
- Keep final registrar-only actions separate from dean recommendations.

### Backend tasks

- define advisory notes / recommendation model
- expose evaluation projections from current curriculum assignment + passed/credited courses
- reuse current deficiency logic once `LEGACY_BACKFILL` side effects are removed

### UI tasks

- either extend current deficiency/TOR panel or create a dedicated evaluation page
- add curriculum-year color legend
- distinguish:
  - passed
  - credited
  - pending
  - deficient
  - orphan prior credit

### Exit criteria

- dean and registrar can evaluate irregular/transferee placement in one coherent registrar-side workflow

---

## Workstream F. Student Grade File and registrar-only academic records

### Goal

Implement the FRD separation between shared evaluation and registrar-only grade file control.

### Deliverables

- registrar-only Student Grade File access path
- clean distinction between:
  - grade sheet operations
  - evaluation view
  - transcript/grade file records

### Backend tasks

- verify current academic history / print routes against FRD restrictions
- create permission gates for grade-file viewing and printing
- define whether TOR/COG/grade file require separate role permissions

### UI tasks

- add dedicated registrar academic records area if current Student Profile is too broad
- clarify labels for:
  - COG
  - TOR
  - Grade File
  - Evaluation

### Exit criteria

- grade file access is explicitly registrar-only
- evaluation sharing does not leak registrar-only records

---

## Workstream G. Class scheduling, special sections, and slot monitoring

### Goal

Extend the existing class scheduling module to satisfy FRD operations for irregular/manual enrollment support.

### Current-state baseline

- Block Sections and Course Sections exist.
- legacy irregular open-section support is retired.
- open/close section, assign faculty, and schedule slots already exist.

### Missing FRD capabilities

- special/custom/make-up section workflow
- richer slot monitoring
- student-list drilldowns
- tracking lists
- stronger schedule governance
- Head Registrar-only schedule override controls

### Deliverables

- section type classification:
  - block
  - irregular open
  - special
  - make-up
  - custom
- slot monitoring dashboard
- exact student roster per section
- capacity change history
- dissolve section workflow
- tracked section list
- active schedule lock controlled by Head Registrar

### Backend tasks

- extend section model with `section_type` and governance flags
- add section lifecycle actions:
  - open
  - close
  - dissolve
  - archive
- add section roster queries and section-capacity audit logging
- add schedule-lock rules and privileged override checks

### UI tasks

- enhance `admin_class_scheduling.html` with:
  - section type filters
  - section occupancy dashboard
  - tracked sections panel
  - student roster modal/page
  - dissolve section action
  - capacity history
- add “Select All” support where FRD asks for bulk tracking actions

### Reporting tasks

- slot monitoring by:
  - subject code
  - subject name
  - section
  - capacity
  - current load
  - remaining slots
  - dissolved/closed state

### Exit criteria

- registrar can actively manage irregular-supporting sections, not just create them
- slot monitoring is operationally useful, not only a scheduling editor

---

## Workstream H. Registrar forms, reg-form lifecycle, and transaction history

### Goal

Capture the FRD requirement for Reg Form history and transaction context.

### Deliverables

- reg-form transaction log beyond current ledger behavior
- tracking of:
  - student name
  - datetime
  - purpose
  - admin remarks
  - triggering action
- regenerated Reg Form artifacts after key workflow changes

### Backend tasks

- define form event log table
- emit log events from:
  - enrollment/load changes
  - withdrawal approval
  - irregular pre-reg finalization
  - profile/curriculum changes where relevant

### UI tasks

- add reg-form history card or separate registrar report
- include printable version history or at minimum event history

### Exit criteria

- registrar staff can trace why and when a registration form changed

---

## Workstream I. Reports, audit, and operational visibility

### Goal

Support registrar operations with explicit reports instead of relying on raw tables or ad hoc DB inspection.

### Deliverables

- irregular applicant advisory queue
- finalized irregular pre-reg list
- withdrawal reason reports
- slot monitoring reports
- section roster exports
- registrar action audit reports
- curriculum-assignment change history

### Exit criteria

- key registrar workflows are reportable from the product itself

---

## 6. Recommended delivery order

### Phase 0. Code safety and correctness

- Workstream A

Reason:

- Security and correctness defects will make every later registrar feature harder to trust and harder to test.

### Phase 1. Irregular advising foundation

- Workstream B
- minimum slice of Workstream E

Reason:

- This is the largest true business gap between the current registrar app and the new handoff obligations.

### Phase 2. Student Profile consolidation

- Workstream C
- remaining Workstream E
- Workstream F access shaping

Reason:

- Once irregular advising exists, the primary registrar operator experience should be cleaned up and renamed.

### Phase 3. Formal registrar operations

- Workstream D
- Workstream H

Reason:

- Withdrawal and form lifecycle are major workflow additions that should be built on top of the stabilized profile/evaluation model.

### Phase 4. Scheduling and operational dashboards

- Workstream G
- Workstream I

Reason:

- Existing scheduling already works for demo use; FRD enhancements can build from that stable base.

---

## 7. Suggested implementation slices

### Slice 1. Security and correctness patch set

- secure endpoints
- remove boot password reset
- remove curriculum auto-backfill side effects
- fix dashboard counters

### Slice 2. Irregular applicant MVP

- irregular applicant list/search
- snapshot header writer
- subject line writer
- finalize/reopen
- readiness badge

### Slice 3. Irregular advising UX

- better manual subject selection
- assessment/header fields
- PDF source selection and operator messaging

### Slice 4. Student Profile rename and cleanup

- navigation/page/title rename
- profile editing cards
- clearer academic placement indicators

### Slice 5. Withdrawal MVP

- request + reasons + approvals
- finance effect
- updated Reg Form

### Slice 6. Scheduling operations expansion

- section types
- slot monitor
- rosters
- dissolve/track tools

---

## 8. Open decisions to resolve with stakeholders

These are registrar-side decisions that should be confirmed before or during build:

1. Irregular readiness rule:
   - Is `subject line count > 0` sufficient, or is explicit finalization required?
2. Shared-table contract:
   - Is `snapshot_source = 'REGISTRAR'` mandatory?
   - Is `evaluation_finalized_at` also required?
3. Snapshot fee payload:
   - Which assessment/header fields must be filled by registrar for irregular PDF generation?
4. Student Profile fields:
   - Which fields may registrar edit directly vs only through controlled workflows?
5. Withdrawal matrix:
   - Exact charge schedule by timing window
6. Section governance:
   - Which actions are Head Registrar only?
7. Grade file scope:
   - Which print/export actions are registrar-only versus dean-visible?

These decisions do not block planning, but they affect schema shape and UI wording.

---

## 9. Risks

- Implementing irregular applicant flow without first clarifying the shared-table contract may cause rework.
- Leaving security until after large workflow additions increases regression and exposure risk.
- Mixing current “enrolled student add/drop” logic with “irregular applicant pre-reg” logic in one page could create operator confusion.
- If curriculum auto-assignment side effects remain, evaluation and deficiency views may silently distort irregular advising outcomes.
- Withdrawal finance rules will be error-prone unless tied to explicit policy/config tables.

---

## 10. Definition of done for the registrar scope

Registrar scope should be considered complete when:

- registrar security and role enforcement are in place
- irregular applicant advising is registrar-owned and persisted to shared snapshot tables
- Student Manager has been converted into a proper Student Profile workspace
- formal Withdrawal exists with approval chain and reporting
- dean/registrar evaluation flow is coherent
- grade-file permissions match FRD intent
- scheduling supports special/irregular operations and slot monitoring
- key registrar workflows are auditable and reportable

---

## 11. Immediate next build recommendation

Start with this exact order:

1. Security/correctness patch set
2. Irregular applicant shared-snapshot MVP
3. Student Profile rename/consolidation
4. Withdrawal MVP
5. Scheduling operations expansion

That order gives the fastest path to closing the biggest registrar-side business gap while reducing the chance that new features are built on top of unsafe or contradictory behavior.
