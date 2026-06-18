# Focused UAT - Registrar Academic Refinement

Run date: 2026-06-17  
App: Registrar  
Base URL: `http://localhost:8083/registrar`  
Login used: `admin / 1234`  
Scope rule: retired admission/pre-registration/dean bridge was not tested.

## Startup

Result: `pass`

- MySQL was already listening on `3306`.
- Registrar was started with `mvn spring-boot:run`.
- App started on port `8083` with context path `/registrar`.
- Compile already passed before this UAT slice with `mvn -q -DskipTests compile`.

Startup warning observed:

- Hibernate logged `Unknown column 'RESERVED' in 'WHERE'` while reading connection metadata.
- The app still completed startup and all tested pages loaded.

## Page Checks

### Student Profile

URL: `/admin/student-manager`  
Result: `pass with follow-up`

Confirmed:

- Admin login did not redirect back to login.
- Page heading is `Student Profile`.
- Student list renders with program filters and `View Profile` actions.
- Detail profile renders registrar-editable fields.
- Summary now shows `Registrar Alerts` with finance clear/accounting-hold state and curriculum deficiency count.
- Profile has visible cards for:
  - Registrar Editable Profile
  - Current Enrolled Subjects
  - Add Subjects
  - TOR & Transfer Crediting
  - Academic History
  - Financial Ledger
  - Installment Plan Override
  - Current Curriculum Assignment
  - Program Shift
  - Scholarship Control
- `WDRW-UAT-2026-001` profile has one current-load withdrawal request form with `scheduleId` and `reasonCode`.

Follow-up:

- `Print Registration Form` is now the visible terminology; the legacy `/admin/print-cor` route remains compatible.
- Account-block / deficiency indicator still needs a field-level decision.

### Class Scheduling

URL: `/admin/class-scheduling`  
Result: `pass with follow-up`

Confirmed:

- Page heading is `Class Scheduling`.
- Block Sections and Course Sections views render.
- Current data showed `95 block(s)` and open section statuses.
- Create Block modal now shows `Section Group`.
- Add Schedule Slot modal shows Day, time, section, and room controls.

Patched during UAT:

- Fixed mojibake dash text in this template, including page title and select placeholders.

Follow-up:

- Room still shows as assign-later/optional. This is now accepted as tentative/TBA behavior.
- Course taxonomy for regular/tutorial/petition is not yet visibly confirmed.

### Withdrawals

URLs:

- `/admin/withdrawals`
- `/admin/withdrawals/report`
- `/faculty/withdrawals`

Result: `pass`

Confirmed:

- Registrar queue loads.
- Withdrawal report loads.
- Dean review page loads.
- Pages show status counts for Dean, Registrar, and Approved.
- Report table headers include Student, Subject, Reason, Policy, Status, Dean, Action.
- Student Profile exposes formal withdrawal request controls for an enrolled test student.

Current data:

- Queue counts were zero during this run, so approve/reject actions were not exercised.

### Scholarships

URL: `/admin/scholarships`  
Result: `implemented; focused workflow retest required`

Confirmed:

- Page heading is `Scholarship Eligibility`.
- Sections render:
  - Academic Scholarship Policy
  - Manual Scholarship Type Catalog
  - Term Scholarship Review
- Updated labels are visible:
  - Maximum Allowed GWA
  - Maximum Allowed Individual Grade
  - Minimum Completed Units

Follow-up:

- Scholarship minimum is now intended to be completed-unit based.
- Default threshold is configurable, with `27` units as the current demo/default policy.
- Demo can use existing grade rows with course units; no full grade-finalization workflow is required for the scholarship demo.
- Manual demo seed is available at `handoffNew/sql_manual/08_scholarship_demo_seed.sql`.
- Live demo seed was applied locally:
  - `SCH-UAT-ELIGIBLE` / Sofia Scholar: 27 units, eligible
  - `SCH-UAT-LOWUNITS` / Liam Low Units: 24 units, not eligible
- Sofia now follows `Submit for Review -> Approve -> Post`.
- Approval alone does not activate the discount; posting updates the existing finance-consumed scholarship fields.
- Reject is available from Pending and Approved; Revoke is available after Posted.

### Grading / Approvals

URLs:

- `/admin/approvals`
- `/admin/classes`
- `/grades`

Result: `pass with follow-up`

Confirmed:

- Admin approvals page loads.
- Approval sections render:
  - VPAA Extension Requests
  - Pending Class Grade Submissions
  - Grade Change & INC Resolution Requests
- Admin classes page loads and lists subject management rows.
- Faculty-style `/grades` page is accessible to admin and shows assigned classes/review queue entry.

Follow-up:

- Dean approval versus admin/VPAA approval semantics still need business confirmation.
- Fine-grained faculty access levels are not yet confirmed by UI UAT.

### Document Trail And Registration History

URLs:

- `/admin/document-trail`
- `/admin/reg-form-history`

Result: `pass`

Confirmed:

- Document Trail page loads.
- Reg Form History page loads.
- Both pages expose filters and recent event tables.
- Document Trail has links back to Student Profile, Reg Form History, and Withdrawal Report.

## Remaining Decisions

- Should `Assign Room Later` display as `Tentative/TBA` more explicitly?
- Visible document wording is now `Registration Form`; compatible route names remain internal.
- Visible `Add/Drop` wording has been renamed to `Withdrawal / Enrollment Changes` while preserving technical keys.
- Should Dean approval be introduced for grade finalization later, or is current Registrar/Admin/VPAA approval acceptable?

## Next Recommended Patch

Do not add another large feature yet.

Recommended next slice:

1. Retest Registration Form print output from Student Manager and the student load page.
2. Retest the registrar scholarship review/posting chain using the seeded Sofia and Liam records.
