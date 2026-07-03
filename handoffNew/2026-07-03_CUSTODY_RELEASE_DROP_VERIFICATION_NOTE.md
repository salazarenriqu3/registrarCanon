# 2026-07-03 Custody, Release, and Drop Verification Note

Purpose:

- Preserve the latest live verification pass in a short, presentation-ready format.
- Give the next agent a clean handoff point for the registrar-only withdrawal / archive / subject-drop track.

## Live Checks Completed

### 1. Archive custody trail

Action performed:

- Posted an archive custody event for withdrawn student `ADDCLS-2026-001`.

Payload used:

- `eventType = REQUESTED`
- `counterpart = Records Custodian`
- `purpose = TOR generation`
- `storageLocation = EAC Cavite records room`
- `remarks = Custody smoke test`

Verified result:

- `student_archive_custody_events` gained a new row for archive key `ARCH-A5B77945EF72`.
- `student_document_events` also recorded the custody action.
- The latest custody row shows:
  - `event_type = REQUESTED`
  - `counterpart = Records Custodian`
  - `purpose = TOR generation`
  - `storage_location = EAC Cavite records room`
  - `remarks = Custody smoke test`

### 2. Withdrawn student number release

Action performed:

- Released withdrawn student `ADDCLS-2026-001` through the registrar release flow.

Verified result:

- The old live number no longer exists in `students`.
- The withdrawn identity now lives under archive key `ARCH-A5B77945EF72`.
- `student_number_release_registry` now contains:
  - `released_student_number = ADDCLS-2026-001`
  - `archive_key = ARCH-A5B77945EF72`
  - `release_status = AVAILABLE`
  - `released_by = admin`
  - `release_note = Archive custody complete`

Current state of the released identity:

- `student_number = ARCH-A5B77945EF72`
- `admission_status = WITHDRAWN`
- `status = WITHDRAWN`
- `is_active = 0`
- `enrollment_blocked = 1`

### 3. Single-subject drop without full student withdrawal

Action performed:

- Dropped one current-term subject for live enrolled student `24-1-00002`.

Target used:

- `studentNumber = 24-1-00002`
- `scheduleId = 2009`
- `remarks = Drop smoke test`

Verified result:

- The student remained:
  - `admission_status = ENROLLED`
  - `status = ACTIVE`
  - `is_active = 1`
  - `enrollment_blocked = 0`
- Current committed load decreased from `16` to `15`.
- A completed withdrawal request was archived as:
  - `withdrawal_scope = SINGLE_SUBJECT`
  - `subject_count = 1`
  - `approval_source = REGISTRAR_DIRECT`
  - `reason_code = CLASS_DROP`
  - `estimated_charge = 370.75`

## What This Confirms

- Archive custody tracking is live and writable.
- Withdrawn identity release is working and rewires the old live number into archive-key custody.
- Single-subject dropping does not accidentally trigger a full school withdrawal.
- The withdrawal ledger still records the charge and approval trail.

## Additional Boundary Checks

### 4. Full-student withdrawal boundary

Action performed:

- Fully withdrew live demo student `OVRPAY-2026-001`.

Verified result:

- The student transitioned to:
  - `admission_status = WITHDRAWN`
  - `status = WITHDRAWN`
  - `is_active = 0`
  - `enrollment_blocked = 1`
- Current committed load dropped from `9` to `0`.
- The completed request was archived as:
  - `withdrawal_scope = FULL_CURRENT_TERM`
  - `subject_count = 9`
  - `status = APPROVED`
  - `reason_code = FINANCIAL`
  - `estimated_charge = 8898.00`

### 5. Shift load cleanup boundary

Action performed:

- Cleared the current-term load for `SPRINT-DEMO-2026-001` using the shift cleanup flow.

Verified result:

- The student remained:
  - `admission_status = PENDING`
  - `status = ACTIVE`
  - `is_active = 1`
  - `enrollment_blocked = 1`
- Current committed load dropped from `3` to `0`.
- The archived request shows:
  - `withdrawal_scope = SHIFT_PROGRAM_CLEANUP`
  - `subject_count = 3`
  - `status = APPROVED`
  - `reason_code = SHIFTING`
  - `estimated_charge = 7785.75`

Practical meaning:

- The registrar can now distinguish:
  - one-subject drops
  - full withdrawal
  - shift-only load cleanup
- The shift cleanup path does not convert into a full school withdrawal.

## What To Test Next

1. Re-open the student profile document trail and archive trail screens to confirm the new actions appear in the UI.
2. Continue registrar-only realignment checks against the latest Admission and Enrollment canon copies before expanding the next demo pass.
3. Decide whether the remaining `RESERVED` startup warning belongs to a registrar note or should stay documented as an external enrollment/admission concern.

## Short Presentation Line

- “Custody tracking, withdrawn identity release, subject-drop behavior, full withdrawal, and shift-load cleanup are all behaving correctly in the live registrar runtime; the next step is UI confirmation and the final cross-system rehearsal.”

## Final UI Smoke

Live registrar route checks completed successfully after the latest boundary pass:

- `GET /registrar/admin/student-manager?username=24-1-00002` returned `200`
- `GET /registrar/admin/student-manager?username=OVRPAY-2026-001` returned `200`
- `GET /registrar/admin/student-manager?username=SPRINT-DEMO-2026-001` returned `200`
- `GET /registrar/admin/document-trail?query=24-1-00002` returned `200`
- `GET /registrar/admin/reg-form-history?studentNumber=24-1-00002` returned `200`
- `GET /registrar/admin/withdrawals/report` returned `200`
- `GET /registrar/admin/scholarships?termId=1` returned `200`
- `GET /registrar/admin/class-scheduling?termId=1` returned `200`
- `GET /registrar/admin/room-monitoring` returned `200`
- `GET /registrar/admin/slot-monitoring` returned `200`
- `GET /registrar/admin/print-cor?username=24-1-00002` returned `200`
- `GET /registrar/admin/print-cog?username=24-1-00002` returned `200`
- `GET /registrar/admin/print-tor?username=24-1-00002` returned `200`

Practical meaning:

- The visible registrar surfaces that support the current handoff are reachable and rendering normally in the live app.
- The browser login widget in the Codex in-app browser session was flaky, but the live registrar runtime itself responded cleanly to authenticated checks through the shell session.

## Full-Stack Startup Hardening

The latest D Admission and D Enrollment canon copies were restarted in fresh temp slots after adding:

- `spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false`

Verified effect:

- D Enrollment temp startup on `8092` no longer logs `Unknown column 'RESERVED' in 'WHERE'`.
- D Admission temp startup on `8093` no longer logs `Unknown column 'RESERVED' in 'WHERE'`.

What remains:

- Enrollment still logs a harmless `MariaDBDialect does not need to be specified explicitly` deprecation warning.
- Enrollment still logs a deprecated `@Temporal` annotation warning for `SubjectRequest.requestDate`.
- Admission still logs a harmless `MariaDBDialect does not need to be specified explicitly` deprecation warning.
- Admission still logs the Spring Security `AuthenticationProvider` note on startup.

Practical meaning:

- The previously blocking `RESERVED` metadata probe is now handled for full-stack readiness.
- The previously noisy Admission index-create warnings were caused by invalid TEXT-column index definitions and are now fixed in the D canon source.
- The remaining startup warnings are noisy but non-fatal and do not block the stack from starting or serving routes.

## Registrar Bootstrap Hardening

The live registrar canon at `E:\registrarCanon_canon` was also hardened for production-style boot:

- `spring.jpa.open-in-view=false` is now set, so the old open-in-view warning no longer appears.
- Hibernate no longer emits the `Unknown column 'RESERVED' in 'WHERE'` metadata probe on startup.
- The live `8083` registrar restarts cleanly after packaging.
- Hibernate now boots without the explicit `MySQLDialect` warning because the registrar uses the MariaDB JDBC driver and lets Hibernate infer the dialect normally.

What remains:

- The Hibernate `MySQLDialect` deprecation note is no longer part of the registrar boot path.
- The live console is now clean on that specific issue because the registrar uses the MariaDB JDBC driver and allows Hibernate to infer the dialect normally.

Practical meaning:

- The registrar is now in a much cleaner production-ready state than before.
- The old warning was removed at the configuration/source level rather than just being hidden in logs.
- The latest live registrar boot no longer emits the prior Hibernate warning or the schema-repair duplicate-column/index noise.
