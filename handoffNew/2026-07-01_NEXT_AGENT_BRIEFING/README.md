# Next Agent Briefing

Use this folder as the fast entry point for the current Registrar/Enrollment/Admission state.

## Canon Paths

- Registrar: `D:\registrarCanon_canon`
- Enrollment: `D:\EnrollLatest\enrollment3`
- Admission: `D:\AdmitLatest\admission`

## Current Live State

- Registrar is the canonical owner for academic master data, student records, grades, withdrawal, document custody, and official academic gates.
- Enrollment owns cashier/accounting, pre-registration, student-number issuance, and final enrollment commit.
- Admission owns applicant intake and applicant documents.
- The Registrar student profile mini-ledger and the official full ledger are now aligned through the same finance refresh path.
- The full ledger page is live at `/admin/scholar-ledger?keyword=<student>`.

## What Was Just Fixed

- Added a real Registrar route for the official ledger page.
- Made finance reads refresh assessment rows before rendering totals.
- Normalized academic-load data so the official ledger page renders without Thymeleaf field errors.
- Verified that the student profile and full ledger now show the same outstanding balance for the checked demo student.

## What The Next Agent Should Read First

1. [Repo Canon](D:\registrarCanon_canon\AGENT_HANDOVER.md)
2. [Three-System Test Handoff](D:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\README.md)
3. [Next Agent Handoff](D:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\NEXT_AGENT_HANDOFF.md)
4. [Runbook](D:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\RUNBOOK_THREE_APPS.md)
5. [SQL Feed and Verify](D:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\SQL_FEED_AND_VERIFY.md)
6. [Testing Storyboard](D:\registrarCanon_canon\handoffNew\2026-07-01_THREE_SYSTEM_TEST_HANDOFF\TESTING_STORYBOARD.md)

## Current Known Risks

- Many unrelated files in the repo are already modified; do not revert them unless the user explicitly asks.
- The SQL and demo documents in `handoffNew/2026-07-01_THREE_SYSTEM_TEST_HANDOFF/` are the most reliable current reference set.
- If a UI change is touched, verify it in the browser.
- If finance logic is changed again, make sure the profile snapshot and ledger page stay in sync.

## Quick Check

- Registrar login: `http://localhost:8083/registrar/login`
- Student Manager: `http://localhost:8083/registrar/admin/student-manager`
- Official Ledger: `http://localhost:8083/registrar/admin/scholar-ledger`

## Summary For The Next Agent

If you only keep one thing in mind: Registrar finance is now supposed to read from one canonical current-term refresh path, so the mini-ledger, full ledger, and persisted ledger rows should not drift apart.
