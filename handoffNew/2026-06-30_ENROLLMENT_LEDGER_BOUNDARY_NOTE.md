# Enrollment Ledger Boundary Note

Last updated: 2026-06-30

This note is for the next agent and for presentation use. It captures a boundary that matters for cross-system testing:

- the **cashier/enrollment finalize path** may legitimately write enrollment status when a downpayment gate is satisfied, but
- the **ledger view path** must stay read-only and must not mutate student status as a side effect of lookup.

## What We Confirmed

1. Enrollment owns the live student-number issuance and cashier-finalization lifecycle.
2. The cashier terminal may perform status finalization after payment/downpayment gating.
3. The ledger screen currently reuses the same pre-finalization helper, and that helper can write `ENROLLED -> PENDING` when the enrollment is not officially finalized.
4. That makes the ledger page a state-changing read path, which is unsafe.

## Why This Matters

- A ledger lookup should be safe for auditors, registrar staff, and troubleshooting.
- Opening the ledger must not silently downgrade a student just because a finalization precondition is incomplete.
- If a status correction is needed, it should happen through an explicit workflow action, not as a side effect of viewing financial history.

## Safe Boundary

### Acceptable

- Cashier terminal checks whether a student is ready to be officially enrolled.
- Cashier terminal can finalize the current term when the downpayment / committed load gates are satisfied.
- Ledger may calculate balances, readiness, and display official status.

### Not Acceptable

- Ledger lookup mutating `applicant_status` or enrollment state.
- Any read-only inspection route writing `ENROLLED -> PENDING`.
- Using the ledger page as an implicit repair mechanism for enrollment state.

## Current Evidence

Observed Enrollment behavior:

- `/admin/cashier` calls the finalization helper in a payment workflow.
- `/admin/ledger` also calls the same helper during simple lookup.
- The helper persists a status downgrade when the student is marked `ENROLLED` but not officially finalized.

That means the bug is not the existence of finalization logic itself. The bug is that the logic is reachable from a read-style ledger route.

## Cross-System Testing Impact

When the three systems are tested together:

- Admission should prepare the applicant snapshot.
- Enrollment should own issuance, pre-reg, cashier payment, and official finalization.
- Registrar should inspect the ledger and withdrawal/archive history without the ledger page mutating the identity state.

If this boundary is not enforced, ledger inspection can interfere with withdrawal tests, shift tests, and any demo that relies on the enrolled/withdrawn state staying stable while we inspect balances.

## Suggested Fix Direction

The next implementation pass should split the current behavior into two explicit paths:

1. **Read-only ledger inspection**
   - gather balance, load, and status data
   - never persist student state

2. **Explicit enrollment finalization**
   - keep the write path on cashier/finalization screens only
   - require a clear action or gate before status changes are saved

## Handoff Summary

Treat cashier auto-finalization as intentional enrollment behavior, but treat ledger-triggered status mutation as a defect that should be separated before the next full three-system UAT run.

