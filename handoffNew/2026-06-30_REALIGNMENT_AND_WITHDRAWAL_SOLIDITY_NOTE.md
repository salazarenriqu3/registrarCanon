# Realignment and Withdrawal Solidity Note

Last updated: 2026-06-30

This note is meant for the next agent and for presentation use. It records the current maturity of the three-system realignment and the registrar withdrawal governance work in direct, operational terms.

## Bottom Line

The project is no longer in the discovery or redesign stage for these topics.

- The 3-system ownership split is established and mostly stable.
- Withdrawal governance is implemented as registrar-owned policy, not just UI behavior.
- The remaining work is mostly edge-case hardening, replayable demo data cleanup, and final UAT sign-off.

## Current Readiness

Use this phrasing if you need a concise but honest presentation summary:

- `3-system realignment`: about `85-90% complete` for the intended active scope
- `withdrawal governance`: about `88-93% complete` for the intended active scope

These are not exact engineering metrics. They are a practical confidence read based on live runtime checks, code inspection, and current handoff state.

## What Is Already Solid

- Admission owns applicant intake and qualification.
- Enrollment owns irregular / continuing pre-advising, dean review, irregular pre-registration generation, fee authoring, cashier/accounting, and enrollment finalization.
- Registrar owns academic master data, curriculum lifecycle, sections, schedules, archive custody, document trail, student history, withdrawal governance, and academic enforcement.
- Enrollment cashier finalization can legitimately write enrollment state when payment/downpayment gates are satisfied.
- Enrollment ledger lookup must remain read-only; if the ledger screen writes `ENROLLED -> PENDING`, that is a defect and not a safe feature.
- Withdrawn students are treated as historical records, not active enrollment subjects.
- Registrar blocks unsafe actions on withdrawn students, including subject add, bulk add, program shift cleanup, curriculum reassignment, and official document release when balance is still open.
- Student identity release and reuse are already modeled through `archive_key` and `student_number_release_registry`.
- Enrollment now consumes released student numbers in a controlled way instead of blindly incrementing the sequence.
- The live reissue demo case was re-tested through the cashier path and now creates a real committed enlistment row.

## What Was Proved In Runtime

The following live behavior was confirmed during the latest run:

- `SPRINT-DEMO-2026-001` loaded through the Enrollment walk-in path.
- The repaired reissue pre-reg snapshot materialized a real committed enlistment:
  - `course_id = 101`
  - `section_id = 1001`
  - `enlistment_status = COMMITTED`
- Enrollment posted official ledger rows for the committed term assessment.
- The shared `students` row was synchronized again instead of staying stale.
- The reissue student did not get marked fully enrolled just because the old preview payment existed.
- The enrollment state only remained official when the real committed current-term load and downpayment gate were both satisfied.

## Key Remaining Risk

The biggest remaining risk is not the core logic itself. It is stale or disposable test data.

- One demo snapshot initially drifted to a fake course/section pair.
- That made the flow look broken even when the code path was mostly correct.
- The fix was to repair the live demo row, not to redesign the business logic.

## What Still Needs Closure

- More end-to-end UAT across the 3 apps together.
- Repeatable reset/replay SQL for disposable demo cases.
- Final verification that every withdrawn-student restriction is enforced in live runtime, not only in code.
- Separate the Enrollment cashier finalize path from the Enrollment ledger lookup path so a balance inquiry cannot mutate enrollment status.
- Final cleanup of deprecated historical screens so they do not confuse new testing passes.

## Best Short Presentation Line

If you need one sentence for a meeting:

- "The core ownership split and withdrawal governance are already working; what remains is edge-case cleanup, replayable demo data, and final UAT."

## Next-Agent Handoff Guidance

When continuing from here:

1. Treat the ownership split as canon, not a proposal.
2. Treat withdrawal as a registrar-governed historical state, not an active student state.
3. Use live replay data carefully; do not assume every failing demo page is a code defect.
4. Verify the committed current-term load before declaring a student officially enrolled.
5. Keep the scope registrar-first unless the user explicitly reopens retired bridge work.
