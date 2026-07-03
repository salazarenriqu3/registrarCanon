# D Enrollment Ledger Mutation Audit

Date: 2026-07-02

Scope:

- Compare the latest `D:\downloads\Latest-20260701T151156Z-3-001` Enrollment canon against the registrar-side expectations already captured in `registrarCanon_canon`
- Determine whether ledger / balance read paths in Enrollment are side-effect free
- Identify whether the previously observed status mutation on lookup is still present

## Bottom line

The latest D Enrollment build is **not fully read-only** on ledger-style lookup paths.

The balance calculations themselves are largely read-only, but the request flow still invokes helper methods that can:

- reconcile pre-reg rows
- post or clear ledger assessment rows
- change student / applicant enrollment standing

So the earlier symptom where a ledger view could appear to mutate a student's status is still credible in the latest Enrollment canon.

## What is safe

These are read-only or effectively display-only:

- `FinancialService.refreshOfficialEnrollmentStatusModel(...)`
- `FinancialService.isOfficialEnrollmentFinalized(...)`
- the actual ledger arithmetic used to compute balances and paid totals

## What still mutates

### 1. Cashier / ledger page pre-finalization hook

File:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3\src\main\java\com\example\enrollment\controller\AdminController.java`

Method:

- `viewLedger(...)`
- `showCashierTerminal(...)`
- `loadStudentForWalkin(...)`

These paths call:

- `ensurePreRegEnrollmentFinalized(s)`

That helper can write back to the student row:

- `s.setApplicantStatus("PENDING")`
- `studentRepository.saveAndFlush(s)`

So a screen that looks like a ledger view can still trigger standing repair or finalization logic before the page renders.

### 2. Ledger-history backfill

File:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3\src\main\java\com\example\enrollment\service\FinancialService.java`

Method:

- `getStudentLedgerHistoryForViewTerm(...)`

That method can call:

- `ensurePreRegAssessmentOnLedger(...)`

Which can:

- clear stale assessment rows
- post pre-reg assessment rows
- normalize ledger charge rows

So the ledger display layer is not strictly read-only either.

### 3. Other view-like entry points that still call repair helpers

These are not the main ledger screen, but they carry the same risk because they invoke the same state-repair helpers while preparing a page:

- `AdminController.showCashierTerminal(...)`
  - calls `checkEnrollmentTimeout(s)`
  - calls `ensurePreRegEnrollmentFinalized(s)`
  - calls `reconcilePaymentCredits(s)`
  - then renders cashier-facing financial data

- `AdminController.loadStudentForWalkin(...)`
  - calls `ensurePreRegEnrollmentFinalized(s)`
  - calls `checkEnrollmentTimeout(s)`
  - calls `reconcilePaymentCredits(s)`
  - then renders walk-in financial data

- `AdminController.downloadAssessmentPdf(...)`
  - calls `checkEnrollmentTimeout(student)`
  - then `AssessmentPdfService.exportAssessmentPdf(...)`
  - which itself calls `reconcilePaymentCredits(student)`

- `AdminController.downloadApplicantAssessmentPdf(...)`
  - calls `checkEnrollmentTimeout(enrolledStudent)` when the applicant has already received a student number
  - or calls `populateApplicantWalkinFinancialData(ref, model)` for the applicant view branch

- `EnrollmentController.index(...)` and `EnrollmentController.viewLedger(...)`
  - call `populateStudentFinancialData(...)`
  - that helper can also reach `ensurePreRegAssessmentOnLedger(...)` and therefore is not guaranteed pure display

### 4. Intentional write actions that are not part of the bug

These should remain classified as explicit mutating actions, not read-path bugs:

- `process-walkin`
- `finalize-enrollment`
- payment cancel / void flows
- reopen-account / undo-enrollment
- subject add/drop
- shift / transition workflows

## Current audit conclusion

The latest D Enrollment canon still mixes **rendering** and **repair** in the same request path.

That does **not** mean every student lookup mutates data, but it does mean:

- ledger and cashier page loads are not safe to treat as purely read-only verification
- PDF export and some profile/financial preview screens also remain repair-capable
- the earlier ledger-status mutation concern is still valid as a boundary issue, even if the exact mutation now depends on which helper gets invoked and which student state is being rendered

For registrar-side testing, the safest assumption is:

- **viewing Enrollment financial screens can change state**
- **only explicit write buttons should be treated as deterministic mutation points**

## Interpretation

The latest D Enrollment canon appears to use a **view-triggered repair model**:

- the system tries to self-heal stale enrollment / ledger state when staff open ledger/cashier pages
- this reduces drift
- but it also means the lookup path can mutate the student record or ledger

That is acceptable only if the team explicitly intends these screens to be “repair on view” surfaces.

If the requirement is true read-only ledger inspection, then D Enrollment still needs a separation between:

- pure read/report screens
- explicit repair/finalize actions

## Registrar-side consequence

Registrar should **not** assume that opening the Enrollment ledger page is harmless.

For cross-system demo and UAT:

- treat Enrollment ledger lookup as a possible write operation
- do not use it as a pure verification source for status mutation debugging
- confirm status changes only after checking the underlying write helpers

## Recommended next step

If we keep the current behavior, document it as intentional “ledger repair on view.”

If we do not want that behavior, the fix belongs in Enrollment:

- move status repair and pre-reg posting behind explicit cashier actions
- keep ledger rendering read-only
- preserve a separate repair command for administrators
