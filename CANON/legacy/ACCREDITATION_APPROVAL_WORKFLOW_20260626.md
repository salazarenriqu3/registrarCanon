# Accreditation Approval Workflow - 2026-06-26

## Scope

This patch converts Student Manager TOR / transfer crediting from direct posting into a registrar-controlled approval workflow.

Registrar remains the official academic authority for the posting outcome.
Enrollment remains the upstream owner of initial accreditation/evaluation in the broader business narrative.

## What Changed

### Student Manager

Path:
- `http://localhost:8083/registrar/admin/student-manager?username=<studentNumber>`

TOR & Transfer Crediting now has two distinct layers:

1. submission layer
- single deficiency row actions now submit a request instead of immediately posting a grade
- bulk CSV import now submits multiple pending requests instead of immediately posting grades

2. approval layer
- a new `Accreditation Requests` panel lists pending, approved, and rejected requests for the student
- pending rows expose:
  - `Approve & Post`
  - `Reject`
- approved/rejected rows remain visible as workflow history

### Backend

New registrar-owned workflow table:
- `transfer_credit_requests`

Request statuses:
- `PENDING`
- `APPROVED`
- `REJECTED`

Approval behavior:
- approval re-validates that the student still has a current curriculum
- approval re-validates that the course still belongs to that curriculum
- approval re-validates that the student does not already have a passing grade
- only then does the system post the official transfer credit into `grades`

## Audit and Trail Coverage

This workflow now leaves artifacts in three places:

1. request table
- `transfer_credit_requests`

2. registration-form event history
- `TRANSFER_CREDIT_REQUESTED`
- `BULK_TRANSFER_CREDIT_REQUESTED`
- `TRANSFER_CREDIT_APPROVED`
- `TRANSFER_CREDIT_REJECTED`
- `TRANSFER_CREDIT`

3. document trail
- document type: `TRANSFER_CREDIT`
- stored workflow events are mirrored into `student_document_events`

## Current Business Rule

The registrar page no longer performs silent direct credit posting from the UI.

Current expected flow:
1. submit single or bulk TOR/prior-school request
2. registrar reviews pending row
3. registrar approves or rejects
4. only approved rows are posted into grades

## Runtime Verification Performed

Verified on live registrar UI:
- login works
- Student Manager shows the new `Accreditation Requests` panel
- a pending request row renders with:
  - request count
  - pending status
  - `Approve & Post`
  - `Reject`

Verified in automated tests:
- request submission stores a pending row and mirrors trail events
- approval marks the row approved and calls the grade-posting port
- rejection marks the row rejected and mirrors trail events

## Manual Demo Notes

Recommended disposable student:
- use a student with:
  - explicit current curriculum assignment
  - visible curriculum deficiencies
  - no existing passing grade for the target course

Manual test:
1. open Student Manager
2. search disposable student
3. scroll to `TOR & Transfer Crediting`
4. submit one deficiency row
5. confirm it appears under `Accreditation Requests` as `PENDING`
6. approve it
7. confirm:
   - row becomes `APPROVED`
   - deficiency list updates on refresh
   - Reg Form History shows transfer-credit workflow events
   - Document Trail shows transfer-credit workflow events

## Files Touched By This Patch

- `src/main/java/com/iuims/registrar/curriculum/CreditGradeService.java`
- `src/main/java/com/iuims/registrar/portal/EnrollmentController.java`
- `src/main/java/com/iuims/registrar/forms/StudentDocumentTrailService.java`
- `src/main/resources/templates/admin_student_manager.html`
- `src/test/java/com/iuims/registrar/curriculum/CreditGradeServiceApprovalWorkflowTest.java`
