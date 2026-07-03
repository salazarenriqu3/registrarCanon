# Ledger Demo Talk Track

Use this guide when presenting how the Registrar profile, withdrawal ledger, and current balance computation work together.

## Purpose

Show that the Student Profile page reflects the same financial computation used by the enrollment/cashier side, without needing to open another tab.

## Core Formula

`Outstanding Balance = Current Term Fees + Forwarded Balance + Withdrawal Charges - Applied Payments - Scholarship Discount`

Where:

- `Current Term Fees = Tuition + Miscellaneous Fees + Other Fees`
- `Withdrawal Charges` includes formal subject-drop or withdrawal penalties
- `Forwarded Balance` is prior-term debt carried into the current term
- `Applied Payments` are completed payments already posted
- `Scholarship Discount` is deducted only when the student qualifies

## What To Show In Demo

1. Open the student in `Student Profile`.
2. Point to the summary cards:
   - `Assessment Total`
   - `Applied Payments`
   - `Remaining Balance`
   - `Overpayment / Credit`
3. Point to the `Official Ledger Breakdown`.
4. Point to the `Ledger Transactions` table.
5. Perform a subject withdrawal or view the post-withdrawal state.
6. Refresh and show the new figures.

## How To Explain The Movement

- The withdrawn subject is removed from tuition computation.
- A withdrawal penalty appears as a separate ledger movement when policy requires it.
- Historical entries remain visible, so the change is auditable.
- The updated balance is the recomputed net of fees, forwarded debt, penalties, payments, and scholarship.

## Suggested Talk Track

### Before the action

- "This is the current assessment for the term."
- "This shows what has already been paid."
- "This is the remaining balance the student still owes."

### After the action

- "The subject was removed from the current load."
- "The ledger shows the withdrawal charge separately."
- "The balance updated because the computation was recomputed from the new load."

## Sample Explanation Using the Current Numbers

If the ledger shows:

- Tuition decreases after withdrawal
- Miscellaneous and other fees stay unchanged
- A drop penalty is added
- Payments stay posted

Then the final balance should move exactly by the net effect of those changes.

## Presentation Order

Use this visual order for the cleanest explanation:

1. Assessment Total
2. Applied Payments
3. Remaining Balance
4. Overpayment / Credit
5. Official Ledger Breakdown
6. Ledger Transactions

## Important Note

This is a read-only reference view. It should explain the computation clearly, but it should not become a second finance editor or duplicate the enrollment cashier workflow.

## Current UI Status

- The Student Profile page now includes a compact `Mini Ledger Snapshot`.
- The full ledger remains available, but it is collapsed by default so the snapshot is easier to read first.
