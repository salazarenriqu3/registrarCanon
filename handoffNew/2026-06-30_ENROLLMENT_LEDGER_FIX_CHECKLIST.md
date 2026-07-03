# Enrollment Ledger Fix Checklist

Last updated: 2026-06-30

Purpose: split Enrollment's cashier-finalization behavior from the ledger read path so the ledger can no longer mutate student status during lookup.

## Scope

- In scope:
  - Enrollment cashier / finalization status writes
  - Enrollment ledger read path
  - safety around `ENROLLED -> PENDING` mutation
  - regression checks for withdrawal, balance movement, and enrollment finalization
- Out of scope:
  - Registrar withdrawal logic
  - Registrar archive custody
  - Admission intake logic
  - fee ownership changes

## Checklist

### 1. Identify the shared Enrollment write helper

- [ ] Locate every call site of the helper that currently combines ledger inspection and finalization repair.
- [ ] Confirm the helper is used by:
  - cashier terminal
  - ledger page
  - any other Enrollment read screens
- [ ] Record the exact code paths that can currently persist `ENROLLED -> PENDING`.

### 2. Split read behavior from write behavior

- [ ] Create or isolate a read-only status evaluation helper for display purposes.
- [ ] Keep the write-capable finalization helper separate and callable only from explicit cashier/finalization actions.
- [ ] Ensure the read-only helper returns data only and does not call `save`, `saveAndFlush`, `update`, or any other persistence method.

### 3. Remove ledger-side mutation

- [ ] Update `/admin/ledger` so it no longer invokes the write-capable finalization helper.
- [ ] Verify the ledger page still shows:
  - balance
  - official enrollment status
  - committed load
  - transaction history
  - payment history
- [ ] Confirm the ledger page no longer changes `applicant_status` as a side effect.

### 4. Preserve cashier auto-finalization

- [ ] Keep the cashier / payment-finalization path able to complete enrollment when payment/downpayment gates are satisfied.
- [ ] Confirm the write path still supports the intended automatic enrollment behavior.
- [ ] Verify that the cashier route remains the only routine place where Enrollment may write final status during normal payment flow.

### 5. Protect withdrawn students

- [ ] Confirm the withdrawn-student guard still blocks active enrollment actions.
- [ ] Confirm the fix does not reopen subject add, shift, finalization, or payment for withdrawn identities.
- [ ] Verify historical withdrawal and archive handling still work unchanged.

### 6. Validate withdrawal ledger movement

- [ ] Re-test full withdrawal and shift cleanup.
- [ ] Confirm withdrawal penalties and refund rows still post to `student_ledger`.
- [ ] Confirm withdrawal changes remain visible in the finance summary without needing ledger-page mutation.
- [ ] Confirm the registrar withdrawal flow is still independent from Enrollment's cashier finalization path.

### 7. Add regression coverage

- [ ] Add a test proving ledger lookup does not change student status.
- [ ] Add a test proving cashier finalization still writes status when the payment gate is satisfied.
- [ ] Add a test proving withdrawn students remain blocked from active Enrollment actions.
- [ ] Add a test proving withdrawal still creates the expected ledger entries.

### 8. Re-run cross-system UAT

- [ ] Admission creates the applicant.
- [ ] Enrollment issues the student number and finalizes correctly through cashier logic.
- [ ] Registrar views the ledger without mutating status.
- [ ] Registrar withdrawal still updates balance/history correctly.
- [ ] Confirm shift cleanup and withdrawal remain separate business processes.

### 9. Update canon after implementation

- [ ] Add a short note stating cashier finalization may write state.
- [ ] Add a short note stating ledger lookup is read-only.
- [ ] Add a short note stating any ledger-driven enrollment state change is a defect.

## Acceptance Criteria

- The ledger page is safe to open repeatedly and never changes student state.
- Enrollment cashier still auto-finalizes when the payment gate is met.
- Withdrawal continues to post the correct financial trail.
- Registrar testing remains stable across the three-system flow.

