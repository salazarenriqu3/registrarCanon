# D Enrollment Ledger Mutation Worklist

Date: 2026-07-02

Status: working note, not a handoff

## Goal

Keep cross-system testing honest by separating:

- pure read/verify checks
- view-triggered repair checks
- explicit cashier/finalize actions

This note is based on the latest D Enrollment canon and the current registrar alignment notes.

## What we already know

- The ledger/balance math in D Enrollment is mostly read-only.
- The ledger/cashier view path is **not** fully read-only.
- Opening some ledger-related pages can trigger:
  - student standing repair
  - pre-reg assessment posting
  - status reconciliation

## Safe to use for verification

Use these as verification-oriented surfaces:

- direct SQL checks against `students`, `sys_users`, `student_enlistments`, `student_ledger`, and `payments`
- registrar Student Profile history views when you want historical identity context
- enrollment views only after you expect a repair/finalize action to occur

## Not safe as a pure read-only verifier

Avoid treating these as no-op inspection surfaces:

- Enrollment cashier/ledger lookup by keyword
- Enrollment ledger history view
- any page path that calls ledger repair or pre-reg finalization helpers before rendering

## Internal test sequence

1. Check the student state by SQL before opening any enrollment page.
2. Open the enrollment ledger/cashier page only if you want to observe repair behavior.
3. Re-check the same student state by SQL after the page loads.
4. Record whether the change came from:
   - explicit cashier action
   - page-load repair
   - finalize action

## Fields to compare before and after

- `students.admission_status`
- `students.status`
- `students.is_active`
- `sys_users.admission_status`
- `sys_users.status`
- `sys_users.is_active`
- current-term `student_enlistments.enlistment_status`
- current-term `student_ledger` assessment rows
- current-term `payments.reference_number`

## What to watch for

- a withdrawn or inactive student becoming pending again during ledger lookup
- pre-reg assessment rows being posted during page load
- payment credits being normalized before an explicit cashier action
- committed load appearing only after finalization, not before

## Recommended rule of thumb

If a screen repairs state, do not use it as your only evidence that the student was already correct.
Use it only after:

- a direct SQL snapshot
- a note of the exact page opened
- a second SQL snapshot

