# Current State Map

Last updated: 2026-07-02

> **For current status, roadmap, and UAT progress read `PROJECT_STATUS_AND_ROADMAP.md` first.**  
> Changelog: **`HANDOFF_UPDATES_20260609.md`** (§13–15 = UI contrast, doc sync, UAT decisions).  
> Historical closure: **`READINESS_CLOSURE_20260608.md`**.
> Presentation-ready solidity note: **`2026-06-30_REALIGNMENT_AND_WITHDRAWAL_SOLIDITY_NOTE.md`**.
> Done / pending / blocked matrix: **`2026-06-30_REGISTRAR_DONE_PENDING_BLOCKED_MATRIX.md`**.
> Grade governance note: **`2026-06-30_REGISTRAR_GRADE_GOVERNANCE_NOTE.md`**.
> Latest custody / release / drop verification note: **`2026-07-03_CUSTODY_RELEASE_DROP_VERIFICATION_NOTE.md`**.
> Three-system test handoff and SQL feed pack: **`2026-07-01_THREE_SYSTEM_TEST_HANDOFF/README.md`**.
> Latest focused runtime findings: **`2026-07-02_THREE_SYSTEM_REALIGNMENT_RUNTIME_PASS.md`**.
> Real applicant-created live demo note: **`2026-07-02_LIVE_ADMISSION_ENROLLMENT_REGISTRAR_DEMO.md`**.
> Latest D Enrollment walk-in status-heal fix note: **`2026-07-02_D_ENROLLMENT_WALKIN_STATUS_HEAL_FIX.md`**.
> Fresh post-fix rerun with a brand-new applicant: **`2026-07-02_FRESH_POSTFIX_THREE_SYSTEM_RERUN.md`**.
> Shared enrollment-type mirror closure note: **`2026-07-02_ENROLLMENT_STATUS_TYPE_MIRROR_FIX.md`**.
> That note now includes both the original defect snapshot and the same-day post-fix recheck against the latest D Admission + Enrollment canon copies.

## 2026-07-01 Three-System Test Handoff Pack

The current runnable handoff for Admission, Enrollment, and Registrar is now consolidated in `2026-07-01_THREE_SYSTEM_TEST_HANDOFF/`.

- `README.md` gives the canonical local paths, ports, accounts, and system ownership summary.
- `RUNBOOK_THREE_APPS.md` gives the exact startup order and URLs.
- `SQL_FEED_AND_VERIFY.md` gives the ordered SQL files and the existing SQL helpers to use for scholarship, accreditation, reissue, pre-reg ordering, and LEC/LAB component checks.
- `TESTING_STORYBOARD.md` gives the intended end-to-end demo flow.
- `NEXT_AGENT_HANDOFF.md` is the cold-start note for the next agent.

## 2026-06-26 Cross-System Boundary Correction

The older registrar-first handoff language around irregular pre-registration and fee ownership is no longer the intended canon.

Enrollment3 owns irregular / continuing pre-advising, draft generation, and fee authoring; Registrar is the downstream academic authority for approval/posting and readiness visibility.

Use this corrected business ownership model for future work:

- Admission owns applicant intake and qualification.
- Enrollment3 owns irregular/transferee pre-advising, dean review, irregular pre-registration generation, fee authoring, cashier/accounting, and enrollment finalization.
- Registrar owns academic master data, downstream academic enforcement, and official academic posting/approval where registrar authority is required.

Important implication:

- registrar-side irregular advising and fee authoring code still exists, but should be treated as historical or transitional unless the user explicitly reopens that scope
- see `CROSS_SYSTEM_ALIGNMENT_REANALYSIS_20260626.md`
- see `IMPLEMENTATION_PLAN_CROSS_SYSTEM_REALIGNMENT_20260626.md`
- see `2026-06-29_CROSS_SYSTEM_REALIGNMENT_PASS.md`

## 2026-06-27 Dependency Hierarchy and Term Overlay

The registrar canon now has a formal ownership graph for the next agent pass:

- Department owns programs and shared academic grouping.
- Program owns curriculum lineage.
- Curriculum owns year/semester placement.
- Sections, schedules, rooms, and faculty are downstream operational records.
- Active term overlays the whole graph and decides what is currently enforceable.

See `2026-06-27_REGISTRAR_DEPENDENCY_MATRIX.md` for the full dependency matrix and term-scope rules.
See `2026-06-27_THREE_PROJECT_DEMO_RUNBOOK.md` for the current three-app launch and presenter script.

## 2026-06-29 Withdrawn Student Governance

Withdrawn students are historical registrar records and must not be treated as active enrollment subjects.

- Registrar Profile remains readable for history, ledger visibility, withdrawal archive, document trail, and archive custody.
- Registrar subject add, bulk add, program shift, and curriculum reassignment are blocked for withdrawn students.
- Official document release is held for withdrawn students with open balance; blocked release attempts are written to Document Trail.
- EnrollmentLatest cashier, walk-in payment, finalization, and canonical status sync paths now reject withdrawn/inactive students before they can be revived or acted on.
- Enrollment ledger now also treats withdrawn archive records as read-only history surfaces: term advance and scholarship update actions are blocked, while historical balances and penalties remain visible.
- Full-student withdrawal stamps the registrar archive file as `WITHDRAWN_FILE` with permanent retention.
- Registrar now also persists an immutable `archive_key` on withdrawn students and mirrors it into withdrawal, reg-form, custody, and document-trail history rows.
- Registrar now snapshots withdrawn live identity into `student_identity_archive` so the archive key owns the long-term registrar history handle.
- Registrar Student Profile now exposes an explicit **Release Student Number** action for withdrawn records, which migrates live historical joins onto the archive key and registers the former live number in `student_number_release_registry`.
- EnrollmentLatest student-number issuance now consumes `AVAILABLE` rows from `student_number_release_registry` before incrementing the normal sequence, then marks the released number as `REISSUED`.
- Enrollment may re-key `payments.reference_number` to the student number after issuance; registrar admission payment reads now resolve both applicant ref and student number so the applicant detail screen stays truthful after handoff.
- Latest D Enrollment still uses some ledger/cashier view paths as repair-on-view surfaces; opening the ledger, cashier, walk-in, PDF export, or some financial preview screens can trigger status/assessment reconciliation, so they are not pure read-only inspection paths.
- Internal testing note: use `2026-07-02_D_ENROLLMENT_LEDGER_MUTATION_AUDIT.md` and `2026-07-02_D_ENROLLMENT_LEDGER_MUTATION_WORKLIST.md` when you need to separate pure verification from repair-on-view behavior.
- Demo gate note: use `2026-07-02_THREE_SYSTEM_DEMO_GONOGO_CHECKLIST.md` before any full three-system run.
- Latest focused runtime pass note: `2026-07-02_THREE_SYSTEM_REALIGNMENT_RUNTIME_PASS.md` records the current pass/fail state after the most recent live three-app verification.
- Registrar identity bridge note: `2026-07-02_REGISTRAR_IDENTITY_BRIDGE_FIX.md` records the archive-aware lookup fix so reissued or archived identities do not mint a fresh archive key when they come back through the other system.
- Enrollment withdrawn-ledger hardening note: `2026-07-02_ENROLLMENT_WITHDRAWN_LEDGER_HARDENING.md` records the ledger UI/backend guards added to both the live E enrollment copy and the newer D enrollment canon copy.
- Registrar Student Profile now deep-links into Enrollment cashier using the currently opened student number, reducing one manual re-search step during three-system testing.
- Enrollment withdrawn ledger wording is now clearer on both the live E copy and the newer D canon: withdrawn archive records show historical-only messaging and plain `WITHDRAWN` status instead of active-looking helper copy.
- Full-student withdrawal now preserves an explicit outstanding-balance hold note in withdrawal history and event trails when the ledger is still open; withdrawal is still allowed, but official document release remains blocked until settlement.
- Historical report links now prefer `archive_key` over the former live `student_number` so released/reissued withdrawn identities do not reopen the wrong active student profile.
- Full-stack startup hardening for the latest D Admission and D Enrollment canon copies is now in place: both apps were restarted cleanly after adding `spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false`, and the prior `Unknown column 'RESERVED' in 'WHERE'` startup blocker no longer appears on fresh boot.
- Admission startup index warnings are now removed too: the two bad applicant performance indexes were corrected to use valid prefix lengths on TEXT columns, and fresh boot now reports them as ensured instead of warning on every startup.
- Registrar bootstrap is also hardened now: `spring.jpa.open-in-view=false` is set, the live 8083 registrar restarts cleanly, the registrar uses the MariaDB JDBC driver so Hibernate can auto-detect the dialect, and the remaining startup schema repair paths now guard existing columns, indexes, and legacy objects before touching them.
- The registrar console is now clean on the live boot path. The old logger suppression workaround is no longer the source of truth; the warning noise was removed at the configuration/schema-guard level.

See `2026-06-29_WITHDRAWN_STUDENT_GOVERNANCE.md`.

## 2026-06-30 Enrollment Pre-Reg Line Ordering Correction

The shared pre-reg subject-line table in `eacdb` was still missing the `sort_order` column on the live demo database even though Enrollment-side read paths now order by `sort_order` as part of the canonical pre-reg snapshot contract.

- Enrollment pre-admission schema now auto-adds `applicant_pre_reg_subject_lines.sort_order` and backfills it from `line_order` when the column is missing.
- Manual SQL helper added at `E:\EnrollLatest\enrollment3\src\main\resources\sql\07_pre_reg_subject_line_sort_order.sql`.
- Runtime verification now shows the pre-reg subject-line query no longer fails on missing `sort_order`.
- Enrollment now grants a narrow exception for `student_number_release_registry.release_status = 'REISSUED'` identities so a registrar-released student number can still finalize from the matching admission snapshot.
- Enrollment pre-reg finalization no longer marks a student `ENROLLED` when the snapshot resolves to zero valid course/section enlistments; the finalize path now fails fast instead of producing a half-enrolled identity.
- Successful pre-reg finalization now mirrors the canonical `sys_users` status back into the shared `students` profile row during the same finalize flow.
- Enrollment `FinancialService.isOfficialEnrollmentFinalized(...)` now requires a committed current-term enlistment row before a paid assessment can be treated as official enrollment; a paid preview alone no longer suppresses the recovery finalize path.
- Enrollment `StudentProfileService` now mirrors `enrollment_status_type` into the shared `students` row during profile creation and sync.
- The remaining runtime blocker for the current reissue demo case is live data drift, not code/schema:
- the active snapshot row for `REISSUE-DEMO-001` currently points to non-existent `course_code = 'REISSUE 101'` and `section_code = 'R-101'`
- those values are not present in the repo seeds or canon SQL and must be repaired in the demo database before that case can auto-enlist

## 2026-06-30 Scheduling Loader and Course Usage Clarification

- Registrar Course Catalog usage presentation is now more explicit: row chips separate curriculum placements, section usage, enlistment rows, grade rows, and prerequisite links instead of collapsing student history into one vague count.
- The Course Catalog **Where Used** modal now shows lifecycle-aware curriculum placements, richer section context, and working drilldown links back into Curriculum Management and Class Scheduling.
- Course usage record counts now tolerate the shared-schema naming drift between `student_waitlist`/`waitlists` and `subject_requests`/`student_requests`, so registrar-side usage summaries no longer silently undercount those sources when the older table names are absent.
- Class Scheduling course-detail loading still stays opt-in by term, but the backend loader no longer performs one section query and one schedule query per course/section chain. It now batch-loads current-term sections and schedules, then groups them in memory for the UI.
- Fresh setup and runtime bootstrap now add hot-path enlistment indexes for `(course_id, enlistment_status)` and `(section_id, enlistment_status)` to support slot counts, course usage reads, and class-scheduling section loads more efficiently.
- Lecture/lab course handling now uses separate enlistable course-component rows when both components are present. A mixed source course is split into `BASE-LEC` and `BASE-LAB`, each with its own `course_id`, `credit_units`, `lec_units`/`lab_units`, schedule/section/enlistment path, and shared `course_family_code`. Legacy mixed rows are now migrated into archived source records plus live split components, and startup auto-runs that repair when old mixed rows are still present.

## 2026-06-30 Registrar Grade Governance Layer

- Registrar grading remains registrar-only and does not revive the retired standalone grading-system scope.
- The canonical `grades` row still stores the current official state, but registrar actions now also write an append-only `grade_record_events` ledger for monitoring and reporting.
- Registrar approval, rejection, reopen, draft save, class submit, official posting, and INC expiration are now expected to be trackable as distinct grade-record events.
- `grade_change_requests` now also carries reviewer-side decision metadata (`reviewed_by`, `review_note`, `rejected_at`) so registrar decisions stop being one-sided.
- The registrar UI now separates **Grade Records** from **Grade Approvals**:
- **Grade Records** is the monitoring/reporting surface for official rows and event history.
- **Grade Approvals** remains the action queue for pending class postings and change requests.
- Unified Document Trail should now surface both grade-change requests and the dedicated grade-record ledger events.

## Live Overlay (2026-06-15)

| Item | State |
|------|--------|
| Active term | **`1120242025`** (term_id **1**, 1st sem AY 2024–2025) |
| Bootstrap | `registrar/setup/RUN_FRESH_SETUP.cmd` |
| Irregular new enrollee bridge | Dormant / retired from active registrar scope; do not treat Dean / Faculty irregular advising or legacy registrar snapshot paths as current acceptance targets |
| Fee readiness | Clean for active term after bootstrap |
| Program Builder | Live; Registrar owns core program master data separately from curriculum mapping |
| Course Catalog | Live; lecture/laboratory units and usage drilldown are exposed in the Registrar UI |
| Curriculum readiness | Current-offering lifecycle labels implemented; legacy curricula remain assignable to returning students |
| Student load caps | Live max units resolve from assigned curriculum + year level + semester; legacy year-level/global caps are reference only |
| Student Manager manual add scope | Live; explicit curriculum assignment is the source of truth, same-semester courses from any year level may be added, and off-semester courses are blocked |
| Schedule collision rules | Live; schedule saves require a room and reject same-term room, faculty, and same-section overlaps |
| Slot Monitoring | Live; committed counts, staged pre-registration counts, capacity edits, and close actions are visible per section |
| Room Monitoring | Live; active rooms, utilization, concrete room schedule rows, room conflicts, missing room rows, and no-faculty/no-schedule exceptions are visible per term |
| Program shift load cleanup | Live; post-enrollment shifts clear all current-term enrolled/staged subjects without withdrawing the student from school, and the explicit curriculum assignment must remain present |
| Academic scholarship | Live; registrar grants academic scholarship only, using SQL-seeded official grades, configurable GWA/period-grade caps, assigned-curriculum unit load, and 3rd/4th-year PE/NSTP disqualification |
| Grade governance | Live; registrar approvals, rejection notes, official row monitoring, and append-only grade event ledger are exposed without reviving the retired standalone grading app |
| Archive custody tracking | Live; Student Profile records physical file request/release/evaluation/scanning/return/refile movements and mirrors them to Document Trail |
| Human UAT | **In progress** — 0/A/B re-tested positively; C–F pending sign-off |
| Registrar Spring Security | **Deferred** — proposal only |
| UI | Higher-contrast alerts/cards (2026-06-10) |
| Runtime verification (June 8) | Cross-app, term transition scripts — PASS in `_runtime_logs/` |

**Handoff implementation scope: complete.** Remaining work is UAT sign-off, user refinements, then production backlog — not stabilization coding.

## 2026-06-21 Academic Builder Clarification

- Curriculum Management presents the normal path as: create a working draft, build the year/semester course plan, then publish it as the current offering.
- A working draft remains inactive until **Publish & Set Current** is selected; publishing moves the prior current offering to `LEGACY`, not `ARCHIVED`.
- Import and repair controls are administrative recovery tools, not normal curriculum creation actions.
- Course Catalog is the shared course master. **Where Used** expands concrete curriculum placements, class sections, student/academic records, and prerequisite links before a shared course is edited.
- Class Scheduling save paths now require a concrete room and hard-block same-term room, faculty, and same-section overlaps.
- Room Monitoring is separate from Slot Monitoring: Slot Monitoring answers capacity questions, while Room Monitoring answers physical-room assignment, utilization, and conflict questions.
- The Class Scheduling warning banner includes a registrar repair action that clears conflicting room assignments for rescheduling, removes section-internal overlap rows, and unassigns faculty from overlapping sections for the selected term.
- Program shifting now has a distinct current-term load cleanup path. It can remove the student's final enrolled subject and archive the class-line snapshots, but it does not call the full school-withdrawal status change.
- Student Profile exposes that cleanup as **Clear Subject Load for Shift**. It defaults the registrar reason to **Shifting**, archives the withdrawal lines as `SHIFT_PROGRAM_CLEANUP`, and keeps the student active/enrolled for the actual program shift.
- A shifted student may temporarily have zero current-term load. That must not hide Add Subjects when `student_curriculum_assignments.is_current = 1` still identifies the assigned curriculum.
- Program Shift filters Destination Curriculum options to the selected Target Program. Shift submissions also redirect back to Student Profile with a flash error instead of exposing Whitelabel on backend validation or rollback failures.
- Academic Scholarship is registrar-owned and academic-only. Eligibility reads official grade rows, configurable GWA/period caps, assigned curriculum term units, and blocks students still taking PE/NSTP in 3rd or 4th year.
- Student Profile now has Archive & Custody Tracking for the physical record room workflow. It records request, release, evaluation completion, scan submission to MIS, return, and refile events, then mirrors those actions into the unified Document Trail.

## 2026-06-17 Registrar Scope Overlay

Registrar is not the canonical home for new-enrollee intake flows:

- Registrar does not own regular applicant pre-registration, automated regular section assignment, cashier payment processing, or normal student-number issuance.
- The prior Dean / Faculty irregular applicant advising bridge in Registrar is now dormant and intentionally outside active acceptance scope.
- Existing dean/faculty irregular-advising routes, snapshot tables, and handoff notes should be treated as historical implementation remnants, not live workflow canon.
- Active registrar canon centers on academic master data and downstream student records: programs, courses, curriculum, schedules, sections, slot monitoring, Student Manager, TOR / transfer crediting, program shift, grading, approvals, and reporting.
- Registrar still guards against duplicate student-number creation when `students.reference_number` already exists.

## Purpose

This document is a live baseline built from the current codebase and handoff docs.

It is meant to complement `MASTER_HANDOFF.md`, not replace it.

Use this file when you need a quick answer to:

- what is already implemented in code
- where the handoff is still accurate
- where the code has moved ahead of the handoff
- what still looks risky before the next edits

## Actual Workspace

The archived handoff paths point to older workspaces; the live canonical workspace is `E:\registrarCanon_canon`.

The real project roots for this workspace are:

- `C:\newer\new\registrar`
- `C:\newer\new\enrollment3`
- `C:\newer\new\admission`

The apps still share one MySQL schema and communicate through shared tables, not HTTP APIs.

## Runtime Shape

Registrar:

- Spring Boot parent on Java 17
- local port `8083`
- context path `/registrar`
- Spring AI MCP server enabled in `application.properties`

Enrollment:

- Spring Boot parent version `4.0.0`
- Java 21
- local port `8082`

Important note:

- the two apps are not on the same Spring Boot baseline
- registrar includes MCP support; enrollment does not appear to expose MCP endpoints

## Shared Contract Reality

The handoff is still directionally correct:

- `system_settings.CURRENT_ACADEMIC_TERM` is the intended current-term authority
- `student_number` is the practical shared identity key
- `student_enlistments` is the operational contract for load state
- `program_fee_settings` is the intended fee source of truth

However, the code shows that not every path has been fully normalized to those rules yet.

## Batch Status Snapshot

### Batch 1: Term authority

Status:

- partially implemented
- materially further along than a pure planning state

What is already in code:

- registrar now has `RegistrarTermService`
- `GlobalTermService` delegates to that resolver
- the resolver reads `system_settings.CURRENT_ACADEMIC_TERM`
- it can normalize raw DB term codes and SL-style term values
- enrollment `AcademicTermService` also resolves the active term from the same setting
- enrollment `TermContextService` maps student `term_year` back to registrar `term_id`
- registrar admission writes `semester`, `year_level`, and `term_year` into both `sys_users` and `students`
- enrollment profile sync mirrors `program_code`, `year_level`, `semester`, and `term_year` between `students` and `sys_users`

What still looks incomplete:

- there are still local fallback conversions in a few places instead of one strict shared path

Practical reading:

- Batch 1 is not done, but it is actively underway in real code
- registrar walk-in payment and registrar-side enrollment screens are deprecated and should not be treated as active Batch 1 acceptance targets

### Batch 2: Enlistment lifecycle

Status:

- actively tightened in this session
- active enrollment-side and non-deprecated registrar scheduler/class-count paths are now aligned with the intended contract

What is already in code:

- both apps have enlistment schema helpers
- explicit `STAGED` and `COMMITTED` semantics exist
- enrollment has `EnlistmentWriteService`
- enrollment assessment readers use `COMMITTED_ONLY`
- registrar scholar and jaypee paths already reference committed-only filtering
- runtime helper filters no longer treat `NULL` enlistment status as committed
- enrollment active load, offering analysis, cashier staging, block staging, faculty counts, ledger view, and term-history helpers now apply explicit staged/committed scope filters
- enrollment waitlist promotion now writes through the committed enlistment path instead of relying on a default insert
- enrollment-side registrar section monitor now counts committed rows only, preserving empty-section visibility
- enrollment-side registrar dashboard full-section counts now count committed rows only
- enrollment-side registrar waitlist force-enroll and regular auto-enlist writes now use the explicit committed enlistment path
- enrollment waitlist student-id parsing now tolerates the live `student_waitlist.student_id` text schema
- registrar active class scheduling counts and close-section checks now count committed rows only
- registrar active Jaypee integration capacity, duplicate, and schedule-conflict checks now apply committed-only filtering
- registrar class scheduling template no longer uses Thymeleaf string expressions directly in event-handler attributes, allowing the active scheduler page to render under Thymeleaf 3.1

What still looks risky:

- deprecated registrar walk-in/payment and registrar-side enrollment screens still exist in code but remain out of active scope

Practical reading:

- the lifecycle model exists
- the main `NULL`-as-committed risk has been removed from shared runtime helpers
- controlled `B2TEST` runtime cases proved staged-only rows stay visible to cashier staging but do not affect official ledger, section monitor counts, faculty dashboard counts, or faculty rosters
- controlled `B2TEST` runtime cases proved waitlist force-enroll creates a committed enlistment and the promoted row appears in section capacity counts
- the controlled `B2TEST` rows were temporary and have been cleaned from the shared database
- active enrollment-side Batch 2 behavior is verified for the current purpose-built cases
- controlled `B2TEST-REG` registrar runtime check proved active class scheduling renders a section with one staged row and one committed row as `1 / 40 enrolled`
- Batch 2 is ready to treat as closed for the agreed active scope, with deprecated registrar enrollment/payment screens intentionally left untouched

### Batch 3: Fee unification

Status:

- active code paths tightened and runtime-verified in this session
- active-term exact fee coverage is now prepared where the current database has usable source data
- remaining no-source scopes still need official registrar fee values before full live billing readiness

What is already in code:

- both apps use `program_fee_settings`
- registrar and enrollment both have `ProgramFeeSettingRepository`
- enrollment `FeeScheduleService` is explicitly centered on registrar-managed `program_fee_settings`
- live fee reads in both apps now require exact `program_fee_settings.term_id` scope instead of silently falling back to global `NULL` rows
- enrollment tuition and RLE amount fallbacks from Java/settings have been removed from live assessment
- enrollment assessment snapshot no longer queries legacy `program_fee_rates` for the RLE rate audit item
- enrollment settings seed/schema no longer creates `fee_fallback_enabled`, `default_tuition_per_unit`, or `rle_rate_per_hour`
- registrar scholar fee calculations now fail visibly for chargeable students when exact official fee settings are missing
- registrar fee preparation no longer creates blank exact rows for scopes with no source/template data
- registrar fee preparation completed exact active-term BSIT rows from available source data
- registrar template copy completed exact active-term BSCPE rows from the BSIT template mapping
- registrar fee admin now shows a term-readiness card, unresolved scope queue, and CSV export for the fee completion pass
- registrar fee admin now provides an import-ready CSV template and CSV upload/import flow for bulk official fee entry

What still looks risky:

- the live database currently has exact active-term fee coverage for BSIT and BSCPE only
- the remaining 248 active-term program/year/semester scopes do not have usable source rows and were intentionally not auto-filled
- global `NULL term_id` fee rows still exist as admin/template data, but no longer satisfy live assessment reads
- registrar admin preparation/import flows still use fallback/template rows intentionally when creating exact term rows

Practical reading:

- `program_fee_settings` is now the sole live fee source for active code paths touched in Batch 3
- missing current-term fee rows now fail closed instead of producing default or legacy-derived amounts
- registrar fee admin runtime checks confirmed BSIT/BSCPE exact rows display and an unresolved BSCS scope surfaces missing-rate warnings
- registrar fee admin runtime checks confirmed the new readiness workspace renders and exports a 248-row unresolved scope CSV for term `1`
- registrar fee admin runtime checks confirmed the import template exports 248 unresolved rows and a controlled CSV upload creates exact fee rows, with the temporary runtime row cleaned afterward
- enrollment cashier runtime check confirmed billing uses exact active-term `program_fee_settings` rows for the controlled BSIT case
- hard-test follow-up corrected the suspicious BSIT/BSCPE term `1`, year `1`, semester `1` fee outliers by copying each program's own year `1`, semester `2` exact fee profile into the matching year `1`, semester `1` row
- hard-test follow-up seeded `BSCPE-1-1-A` active-term block sections from the real BSCPE year `1`, semester `1` curriculum and verified BSCPE section assignment, block staging, payment, finalization, and COR export end to end
- the controlled `B3FEE` runtime rows were temporary and have been cleaned from the shared database
- before full live billing, registrar must supply/import official fee values for the remaining unresolved scopes

### Batch 4: Legacy mirror retirement

Status:

- active source cleanup complete

What is already in code:

- comments in both apps describe `jp_*` tables as legacy and non-canonical
- some newer flows already assume canonical shared tables are the real source of truth
- enrollment finalize/undo/status paths now update canonical `sys_users`, `students`, `applicants`, and `student_enlistments` only
- registrar term transition/status logic no longer updates `jp_students` or `jp_student_enlistments`

What still exists:

- active Java source scan no longer finds `jp_students` or `jp_student_enlistments` reads/writes
- `enrollment3/src/main/resources/sql/curriculum_fallback.sql` still contains legacy `jp_courses` fixture data, but it is not an active runtime write path

Practical reading:

- Batch 4 active mirror retirement is complete for the known controller and transition paths
- future cleanup may remove or archive legacy SQL fixtures separately, but live flows no longer depend on mirror writes

### Batch 5: Registrar schema/query cleanup

Status:

- active source cleanup complete

What is already in code:

- registrar Student Manager roster now computes current-term official units from `student_enlistments.section_id`
- roster unit computation uses committed-only enlistment filtering through registrar `EnlistmentSchemaService`
- stale registrar `StudentEnlistment` / `StudentEnlistmentRepository` JPA model files were removed
- stale `ClassSectionRepository.countEnrolledStudents` helper was removed

Practical reading:

- Batch 5 is complete for the known roster/query and stale model drift
- live section scheduling counts and close-section checks already use the shared `student_enlistments` shape with committed-only filtering
- runtime verification confirmed `/registrar/admin/student-manager` renders a roster row with official enrolled units instead of falling back to an empty roster

### Batch 6: Identity and profile normalization

Status:

- active transaction-key cleanup complete

What is already in code:

- enrollment `StudentProfileService` syncs `students` and `sys_users`
- enrollment `StudentLedgerService` now resolves runtime reads and writes to `student_number` only
- registrar `JaypeeIntegrationService` now uses `student_number` only for transaction-table reads
- enrollment faculty roster and grade sync paths now read/write `grades.student_id` by student number
- enrollment program-code hydration no longer searches `student_enlistments` by numeric `user_id`
- migration repair still re-keys old numeric transaction rows to student number when active student flows touch them
- registrar admission now inserts canonical student rows directly

What still looks risky:

- broader profile behavior should remain part of manual UAT, but the active controlled Batch 7 path now verifies cross-app identity/profile behavior for a disposable student

Verification:

- enrollment and registrar compile cleanly after Batch 6
- active source scan no longer finds the retired mixed `student_number` / stringified `user_id` read patterns
- isolated enrollment runtime on port `8092` loaded a faculty section roster for student `26-1-00001` through the canonical `student_id = username` path
- database audit found `0` numeric-only `student_id` rows in `student_ledger`, `student_enlistments`, and `grades`

### Batch 7: Regression and hardening

Status:

- active-path controlled regression complete

What was verified:

- both apps compile cleanly after the Batch 7 hardening patch
- active source scan remains clean for retired `jp_*`, stale enlistment-shape, numeric identity, and fee-fallback patterns
- isolated enrollment runtime on port `8092` completed a disposable `B7REG-001` BSCPE flow through section assignment, block staging, payment, finalization, official ledger, COR export, faculty roster, and grade submission
- official `student_enlistments` rows were committed under `student_number`, with no numeric `student_id` leak
- official `grades` rows were written under `student_number`, with no numeric `student_id` leak
- registrar runtime on port `8083` loaded Student Manager profile, BSCPE roster, Settings readiness, Term Fees, and registrar print-COR for the same disposable student
- Settings readiness now exposes incomplete primary-rate fee scopes in the page, JSON status text, and term-transition error message
- scholar cashier runtime on port `8083` completed a disposable `SCHREG-001` BSCPE flow through scholarship grant, current-term subject enlistment, committed official load display, scholarship-aware walk-in assessment display, payment posting, and canonical ledger/payment writes
- temporary `B7REG-%` runtime rows were cleaned from the shared database after verification
- temporary `SCHREG-%` runtime rows were cleaned from the shared database after verification

Remaining manual/UAT scope:

- deprecated registrar walk-in/payment and registrar-side enrollment screens were intentionally excluded
- full term transition should be rehearsed only after readiness is clean
- readiness still reports `248` incomplete primary-rate fee scopes and `6` programs without active curriculum for the active term, so official data completion is still needed before term transition can be considered ready
- the current unresolved fee import file leaves `TUITION_PER_UNIT` and `LEC_FEE_PER_UNIT` blank for those 248 scopes, so Codex should not invent primary rates without registrar-approved values

## Important Drift From Handoff

The code is ahead of the handoff in at least one important place:

- `student_term_closes` snapshot writing is already implemented in both apps
- enrollment `FinancialService` also appears to read historical snapshots

This matters because the older finance handoff still describes snapshot work as a future sprint item.

Practical reading:

- do not assume every "next sprint" item in the older finance handoff is still future work
- verify in code before planning from older documents

## Current High-Risk Seams

These are the best current watchpoints before making new changes:

- deprecated registrar enrollment/payment screens still contain old paths and should remain out of scope unless deliberately reactivated
- registrar scholarship is now academic-only in the live UI; manual/non-academic scholarship type maintenance is retired and should not be revived without explicit scope approval
- legacy `jp_*` mirror writes are retired from active Java source; remaining legacy fixtures should be treated as archive/cleanup work
- app baselines differ: registrar is Java 17/Spring Boot 3.x, enrollment is Java 21/Spring Boot 4.0.0
- older notes still mention term 2 after a transition run; the current doc-pack canon for demos/UAT is term `1120242025` unless staff intentionally advances the term
- six programs (`BSBA`, `BSCE`, `BSCS`, `BSECE`, `BSED`, `BSMATH`) are soft-retired until official curriculum exists
- future AY terms (`1120252026`+) need fee copy + section seeding before use

## Safe Working Assumptions For Next Edits

- treat `NEXT_AGENT_HANDOFF_20260608.md` and this file as the live status overlay
- treat deprecated registrar walk-in/payment and registrar-side enrollment screens as out of active scope
- Batches 2–7 active paths: **complete and runtime-verified through term 2**
- term transition: **done** on live DB — do not re-run without backup/approval
- next work: UAT, doc alignment, official curriculum content, future AY operational prep — not batch stabilization code

## Recommended Near-Term Baseline

- Batch 1: mostly done; duplicate term-fallback cleanup remains non-blocking
- Batches 2–7: **complete** for active scope including term-2 edge verification
- Production UAT: ready to continue with staff on term `1120242025`

## Verification Note

This map is based on:

- handoff documentation review
- code inspection across registrar and enrollment
- configuration review
- fresh package builds for both registrar and enrollment after Batch 2 lifecycle edits
- fresh registrar compile and Student Manager runtime check after Batch 5 roster/query cleanup
- targeted runtime verification of staged-only section count behavior after the section monitor patch
- controlled `B2TEST` runtime verification for cashier staging, official ledger, enrollment-side registrar section monitor, faculty dashboard, faculty roster, and waitlist force-enroll
- temporary `B2TEST` runtime rows cleaned after verification
- controlled `B2TEST-REG` registrar runtime verification for active class scheduling committed-only counts
- temporary `B2TEST-REG` runtime rows cleaned after verification
- controlled `B7REG-001` cross-app runtime verification for enrollment cashier finalization, committed official load, official assessment/ledger, COR export, faculty roster/grade write, registrar Student Manager, registrar roster, Settings readiness, Term Fees, and registrar print-COR
- temporary `B7REG-%` runtime rows cleaned after verification
- fresh registrar compile after Batch 7 readiness hardening
- controlled `SCHREG-001` scholar cashier runtime verification for scholarship grant, subject enlistment, committed official load display, scholarship-aware walk-in assessment display, payment posting, and canonical `student_number` ledger/payment/enlistment writes
- temporary `SCHREG-%` runtime rows cleaned after verification
- **2026-06-08 closure:** term transition executed; term-2 edge verification (B2TEST, waitlist, drop, admission, catalog/curriculum pages); evidence in `_runtime_logs/term2_edge_verify_result_20260608.json`

This map is backed by fresh package builds and controlled active-path runtime checks through Batch 7. Deprecated registrar enrollment/payment screens remain intentionally outside the active acceptance scope.
