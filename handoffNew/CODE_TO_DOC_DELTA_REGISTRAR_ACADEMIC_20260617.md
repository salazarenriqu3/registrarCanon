# Code-to-Document Delta - Registrar Academic Refinement

Last updated: 2026-06-17  
Source document: `D:/downloads/interview 2 clear documentation.docx`  
Registrar scope rule: Ignore the retired admission/pre-registration/dean bridge.

## Purpose

This file converts the useful interview-document inputs into a registrar-centered implementation delta.

It answers three questions:

- what the current registrar already supports
- what is only partially aligned
- what still needs a decision before code should be changed

## Snapshot Verdict

The registrar system already has more of the academic spine than the interview document initially suggests:

- Student Profile is mostly the current canonical area, even if some old route names still say `student-manager`.
- Class scheduling already has block sections, curriculum selection, faculty assignment, close-section behavior, and schedule slots.
- Withdrawals already have reason capture, Dean-to-Registrar approval movement, reporting, and document-trail integration.
- Program shift and curriculum assignment already exist from the Student Profile area.
- Scholarships already have policy settings, type catalog, evaluation, and manual grant/revoke actions.
- Grading already has faculty encoding, grading windows, grade-change requests, and admin approval screens.

The main gaps are terminology cleanup, policy-detail confirmation, and a few deeper behavior mismatches that should not be patched blindly.

## Phase A Findings

### Policy Windows And Settings

Status: `partial`

Already present:

- active term resolution
- grading windows and faculty grade-entry flow
- withdrawal request timing metadata
- late enrollment policy hooks in finance/admission-related code
- scholarship policy settings by selected term

Needs confirmation:

- whether enrollment, adding/changing, withdrawal, and class scheduling windows are all separate registrar-owned configurable windows
- which defaults are demo-only versus approved policy
- whether late enrollment fees belong to Registrar configuration or remain Accounting/Enrollment-owned

Do not change yet:

- official fee values
- official add/drop/withdrawal charge schedule
- production deadline defaults

### Student Profile

Status: `mostly aligned`

Already present:

- Student Profile / Student Manager page handles student search and profile display
- curriculum assignment is available
- program shifting is available
- carry-over / credited-course summary exists for shift scenarios
- scholarship controls are visible from the student record
- withdrawal and academic history areas are integrated elsewhere in registrar workflows

Needs confirmation:

- exact canonical editable profile fields
- whether account block / deficiency state is stored in Registrar or only read from Accounting/Enrollment
- whether old/new curriculum history is visible enough for daily registrar use
- whether route/template names should be renamed away from `student-manager` or left as internal compatibility names

Recommended next patch:

- current curriculum, deficiency count, and accounting-hold indicators are now visible in the Student Profile summary.

### Class Scheduling And Sections

Status: `partial`

Already present:

- block section creation
- program and curriculum selection during block creation
- year level, semester, section-group suffix, and capacity
- adviser/faculty assignment
- room selection during schedule-slot creation
- close-section behavior
- faculty loading connection through assigned schedules

Patched now:

- Create Block label changed from `Group` to `Section Group` so the UI matches registrar section language.

Needs confirmation:

- interview says room assignment is mandatory, but current backend accepts schedule slots without a room.
- interview asks for regular/tutorial/petition course classification, but current scheduling screens do not clearly expose that taxonomy.
- slot monitoring and enrolled/pre-registration counters need hands-on UAT verification.

Do not change yet:

- do not enforce room-required behavior until the registrar confirms whether `Assign Room Later` is still operationally needed.
- do not invent tutorial/petition semantics without a real scheduling rule.

### Withdrawal / Change Of Enrollment

Status: `mostly aligned`

Already present:

- withdrawal request flow
- reason capture
- request status movement
- Dean and Registrar approval roles in the workflow
- withdrawal report endpoint
- document-trail event synthesis for withdrawal actions

Needs confirmation:

- interview wording strongly prefers `Withdraw` over `Drop`; visible `Add/Drop` wording has been renamed where safe.
- exact tuition-charge timing policy, including 50% / 100% charge periods, requires Accounting/Registrar confirmation.
- whether change-of-schedule and withdrawal should share one workflow or stay separate.

Recommended next patch:

- visible permission/menu text was renamed from `Add/Drop` to `Withdrawal / Enrollment Changes` where it is not tied to legacy technical keys.

### Academic Records, TOR, COG, And Evaluation

Status: `partial`

Already present:

- TOR/COG print-related pages exist
- grade history and student grades exist
- curriculum assignment and carry-over evaluation exist
- registrar print forms exist

Patched now:

- visible `Certificate of Registration` / `COR` wording is standardized as `Registration Form`.
- existing `/admin/print-cor` route and template filenames remain unchanged for compatibility.

Needs confirmation:

- curriculum evaluation matrix with color-coded indicators needs a UI-level check.
- Dean read access to evaluation data needs a role/access audit.
- registrar-only access for official grade files needs route-level verification.

### Scholarships

Status: `partial`

Already present:

- scholarship eligibility page
- academic scholarship policy settings
- scholarship type catalog
- manual scholarship grant/revoke
- term candidate evaluation

Patched now:

- policy labels expanded from abbreviations:
  - `Max GWA` to `Maximum Allowed GWA`
  - `Max Grade` to `Maximum Allowed Individual Grade`
- scholarship minimum converted to configurable completed units:
  - setting key: `SCHOLARSHIP_MIN_COMPLETED_UNITS`
  - default: `27`
  - evaluator sums graded course `credit_units`
- registrar scholarship review workflow added:
  - `PENDING` after submission
  - `APPROVED` after review, with no financial effect yet
  - `POSTED` only when the approved scholarship is activated for assessment
  - `REJECTED` and `REVOKED` remain auditable terminal states

Boundary:

- this is a registrar-owned review/posting demonstration and does not modify Enrollment/Cashier.
- finance continues to read the existing `students.scholarship_approved` fields, which are updated only at `POSTED`.
- no full grade-finalization subsystem was added.

Demo support:

- `sql_manual/08_scholarship_demo_seed.sql` seeds two scholarship candidates using official grade rows:
  - one eligible with 27 completed units
  - one blocked by insufficient completed units

### Grading

Status: `partial`

Already present:

- grading windows
- faculty grade entry
- admin approval views
- grade-change request handling
- preliminary/midterm/final grading concepts appear in the academic module

Needs confirmation:

- faculty access levels from the interview: no access, view only, encode
- Dean approval before grade finalization versus current admin approval semantics
- configurable grade components and percentage breakdowns per subject/class
- final grade-file locking and registrar-only release policy

Recommended next patch:

- audit routes and templates for who can view, encode, approve, and release grades before changing labels or access rules.

## Immediate Execution Checklist

- [x] Retired admission/pre-reg/dean bridge kept out of scope.
- [x] Created code-to-document registrar delta.
- [x] Patched section terminology in class scheduling.
- [x] Clarified scholarship policy labels and converted the minimum requirement to completed units.
- [x] Verify compile after this documentation/UI-label pass.
- [x] Run focused browser UAT for Student Profile, Class Scheduling, Withdrawals, Scholarships, and Grading.
- [x] Decide whether room is required now or still allowed to be assigned later: assign-later remains allowed and should be treated as tentative/TBA.
- [x] Decide whether scholarship minimum should become unit-based: use configurable completed units.
- [x] Decide scholarship minimum default: `27` units.
- [x] Rename visible `Add/Drop` wording while preserving technical keys.
- [x] Surface deficiency/account-block state in Student Profile using existing registrar data.
- [x] Seed and verify scholarship demo data using completed units.
- [x] Standardize visible `COR` / certificate wording as `Registration Form` while preserving compatible routes.
- [x] Add registrar scholarship review, approval, rejection, posting, and revocation states.

Focused UAT artifact: `FOCUSED_UAT_REGISTRAR_ACADEMIC_20260617.md`

## Recommended Next Slice

Run focused UAT in this order:

1. Student Profile: confirm curriculum, previous curriculum, program shift, deficiency/account-block visibility.
2. Class Scheduling: create section, assign curriculum, add schedule slot with and without room, close section.
3. Withdrawal: submit, approve, reject, and confirm document-trail/report output.
4. Scholarship: run eligibility, submit Sofia for review, approve, post, and verify that the discount activates only after posting.
5. Grading: verify faculty entry, approval, grade-change request, and official record visibility.
