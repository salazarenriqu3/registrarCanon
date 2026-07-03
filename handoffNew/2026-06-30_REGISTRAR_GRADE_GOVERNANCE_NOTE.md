# Registrar Grade Governance Note

Date: 2026-06-30  
Workspace: `E:\registrarCanon_canon`

## Scope

This pass strengthens registrar-side grade keeping, monitoring, and reporting without reviving the retired standalone grading-system scope.

Registrar remains the academic authority that:

- receives faculty/dean-submitted grade outcomes
- approves or rejects change requests
- posts official outcomes
- maintains the official record and monitoring surface

## What changed

### 1. Append-only grade event ledger

New table:

- `grade_record_events`

Purpose:

- preserve an append-only registrar event stream beside the mutable `grades` row
- track draft save, class submit, official posting, change request, approval, rejection, reopen, and INC expiration

Key implementation file:

- `src/main/java/com/iuims/registrar/academic/GradeRecordEventService.java`

## 2. Registrar review metadata on change requests

Extended table:

- `grade_change_requests`

New reviewer-side fields:

- `reviewed_by`
- `review_note`
- `rejected_at`

Purpose:

- make approval/rejection a complete registrar decision record instead of a one-sided faculty request log

## 3. Dedicated registrar screens

New or updated routes:

- `/admin/grade-records`
- `/admin/approvals`
- `/admin/reject-change`

Behavior:

- **Grade Records** now acts as the registrar monitoring/reporting surface
- **Grade Approvals** stays the action queue
- rejection now requires a registrar review note

## 4. Unified trail visibility

`StudentDocumentTrailService` now also surfaces:

- dedicated grade-record ledger events
- richer grade-change decision details including reviewer note and reviewer actor

## 5. Lock-status normalization

Live code previously mostly treated only `LOCKED` as frozen, while seed/demo data already used `FINALIZED`.

This pass normalizes registrar-frozen interpretation so both statuses are treated as locked/finalized states for runtime decisions.

## Validation

Build:

- `mvn -q -DskipTests package` -> PASS

Fresh-schema artifacts updated:

- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/02_FRESH_DATABASE/sql/01_SCHEMA/01_base_schema_and_seed.sql`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/05_registrar_feature_demo_seed.sql`

Demo manual updated:

- `handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`

State map updated:

- `handoffNew/CURRENT_STATE_MAP.md`

## Remaining caution

This pass was build-validated, but the live browser process was not force-restarted as part of this note. If the currently running local registrar instance predates this patch, restart the app before UI verification so the new `/admin/grade-records` route and rejection path are active in the running server.
