# Enrollment Ledger Status Presentation Fix

Date: 2026-06-30  
Scope: Enrollment ledger UI wording, not enrollment mutation logic

## Why This Was Changed

During the fresh verification pass, the enrollment ledger was still displaying `PENDING` on some students who were already persisted as `ENROLLED`.

That was not a write-path bug, but it was confusing enough to look like one while testing withdrawal, shift, and ledger-read behavior.

## What Was Fixed

- The ledger page now shows the stored enrollment status directly.
- The ledger page also shows a separate finalization badge:
  - `OFFICIAL` when the term is finalized
  - `PENDING FINALIZATION` when the student is enrolled but the current-term load is not officially finalized yet

## What Was *Not* Changed

- No enrollment write path was reopened.
- No status mutation logic was added back to the ledger lookup.
- Withdrawn-student locks remain intact.
- Official document release remains blocked for withdrawn students with outstanding balances.

## Verification Result

Live browser verification for `SPRINT-DEMO-2026-001` now shows:

- `ENROLLMENT STATUS: ENROLLED`
- finalization badge: `PENDING FINALIZATION`

Withdrawn-student page behavior still holds for `ADDCLS-2026-001`:

- no shift form
- document-release block remains visible
- withdrawal archive and custody history remain intact

## Handoff Meaning

This is a readability fix, not a business-rule change.

The ledger remains read-only, and the current change simply makes the UI less misleading while keeping the boundary intact.

