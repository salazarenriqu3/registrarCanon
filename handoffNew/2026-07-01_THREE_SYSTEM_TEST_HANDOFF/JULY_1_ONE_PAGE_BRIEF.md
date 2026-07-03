# July 1 One-Page Brief

Date: 2026-07-01

This is the shortest current summary for the next agent.

## What Changed

- The three-system handoff bundle is now the July 1 test pack in `handoffNew/2026-07-01_THREE_SYSTEM_TEST_HANDOFF/`.
- The pack includes a runnable SQL feed and a readiness verifier for the shared `eacdb`.
- Registrar now has a more explicit LEC/LAB component contract for mixed lecture/lab courses.
- The shared schema now expects pre-reg subject-line ordering, student-number release tracking, and append-only grade events.

## What The SQL Pack Does

- Preflights the shared database, current term, and core accounts.
- Applies required compatibility columns and tables.
- Seeds one safe smoke applicant: `3SYS-SMOKE-0001`.
- Seeds one LEC/LAB component family: `DEMOCP-LEC` and `DEMOCP-LAB`.
- Verifies active-term health, section/schedule health, withdrawal-governance tables, grade-governance tables, pre-reg sort order, and the LEC/LAB split-state verifier.

## What The Latest Docs Mean

- Admission still owns applicant intake and documents.
- Enrollment still owns pre-registration, cashier/accounting, student-number issuance, and final enrollment commit.
- Registrar still owns academic master data, official records, withdrawal governance, archive custody, grade governance, and approval gates.
- Withdrawal remains a historical registrar state, not an active enrollment state.
- Enrollment ledger lookup must stay read-only.

## What To Watch

- Many active-term sections still lack faculty assignment.
- The July 1 docs still mention `E:\...` in human instructions, while the live local repos are on `D:\...`.
- The new SQL pack is a smoke baseline, not a full demo dataset.
- The LEC/LAB seed is intentionally minimal and should be expanded only if the test case needs it.
- The registrar startup repair auto-runs the legacy lecture/lab migration helper when old mixed rows are detected.

## Best Files To Open Next

1. `README.md`
1. `RUNBOOK_THREE_APPS.md`
1. `SQL_FEED_AND_VERIFY.md`
1. `TESTING_STORYBOARD.md`
1. `NEXT_AGENT_HANDOFF.md`

## Short Judgment

The ownership split is stable. The newest work is mostly schema hardening, demo readiness, and making the three-system test path repeatable.
