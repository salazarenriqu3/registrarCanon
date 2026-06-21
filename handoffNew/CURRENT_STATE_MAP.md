# Current State Map

Last updated: 2026-06-21

> **For current status, roadmap, and UAT progress read `PROJECT_STATUS_AND_ROADMAP.md` first.**  
> Changelog: **`HANDOFF_UPDATES_20260609.md`** (§13–15 = UI contrast, doc sync, UAT decisions).  
> Historical closure: **`READINESS_CLOSURE_20260608.md`**.

## Live Overlay (2026-06-15)

| Item | State |
|------|--------|
| Active term | **`1120242025`** (term_id **1**, 1st sem AY 2024–2025) |
| Bootstrap | `registrar/setup/RUN_FRESH_SETUP.cmd` |
| Irregular new enrollee bridge | Dormant / retired from active registrar scope; do not treat Dean / Faculty irregular advising or registrar pre-reg snapshots as current acceptance targets |
| Fee readiness | Clean for active term after bootstrap |
| Program Builder | Live; Registrar owns core program master data separately from curriculum mapping |
| Course Catalog | Live; lecture/laboratory units and usage drilldown are exposed in the Registrar UI |
| Curriculum readiness | 18 active programs; 6 soft-retired (no source curriculum) |
| Schedule collision rules | Live; active saves reject same-term room, faculty, and same-section overlaps while allowing TBA rooms |
| Slot Monitoring | Live; committed counts, staged pre-registration counts, capacity edits, and close actions are visible per section |
| Human UAT | **In progress** — 0/A/B re-tested positively; C–F pending sign-off |
| Registrar Spring Security | **Deferred** — proposal only |
| UI | Higher-contrast alerts/cards (2026-06-10) |
| Runtime verification (June 8) | Cross-app, term transition scripts — PASS in `_runtime_logs/` |

**Handoff implementation scope: complete.** Remaining work is UAT sign-off, user refinements, then production backlog — not stabilization coding.

## 2026-06-21 Academic Builder Clarification

- Curriculum Management presents the normal path as: create a working draft, build the year/semester course plan, then publish and activate it.
- A working draft remains inactive until **Publish & Activate** is selected; activation archives the prior active version for that program.
- Import and repair controls are administrative recovery tools, not normal curriculum creation actions.
- Course Catalog is the shared course master. **Where Used** expands concrete curriculum placements, class sections, student/academic records, and prerequisite links before a shared course is edited.
- Class Scheduling save paths hard-block same-term room, faculty, and same-section overlaps. A room may remain TBA and be assigned later.

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

The old handoff paths point to `D:\new`.

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
