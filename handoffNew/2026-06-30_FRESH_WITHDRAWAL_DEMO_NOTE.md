# Fresh Withdrawal Demo Note

Date: 2026-06-30  
Scope: Registrar-only withdrawal governance, ledger impact, archive state, and downstream lock behavior

## What Was Tested

I ran a fresh end-to-end registrar withdrawal demo against a live enrolled student record:

- Student: `Noel Addclass Demo`
- Student number: `ADDCLS-2026-001`
- Starting state before the action:
  - `ENROLLED`
  - `BSIT`
  - `1-1`
  - active load: `9 units`
  - current-term subject lines: `4`

## Action Performed

From the registrar student profile, I submitted the full-student withdrawal flow using:

- Withdrawal scope: `FULL_CURRENT_TERM`
- Reason code: `MEDICAL`
- Remarks: `Fresh end-to-end withdrawal demo`

## Confirmed Result

The registrar processed the withdrawal as an approved school-withdrawal event, not just a subject drop.

### Student State After Withdrawal

- `admission_status = WITHDRAWN`
- `status = WITHDRAWN`
- `is_active = 0`
- `enrollment_blocked = 1`
- program/year/semester remained historically attached for recordkeeping
- archive key was assigned for custody/history continuity

### Withdrawal Request Record

- Withdrawal request ID: `17`
- Scope: `FULL_CURRENT_TERM`
- Subject count processed: `4`
- Reason: `MEDICAL`
- Status: `APPROVED`
- Completed timestamp was written immediately
- Estimated withdrawal charge: `PHP 6,673.50`
- Policy note recorded:
  - `50% tuition charge applies after 7 enrolled day(s).`
  - `Outstanding balance remains: PHP 6,673.50.`
  - `Official document release stays blocked until settled.`

### Ledger Movement Confirmed

The withdrawal created penalty ledger entries for each active enrolled subject line instead of mutating the record into a fake or half-withdrawn state.

Observed ledger rows included:

- `DROP_PENALTY` for `AECO 11`
- `DROP_PENALTY` for `CC101`
- `DROP_PENALTY` for `CC102`
- `DROP_PENALTY` for `UPR1 11`

This confirms the registrar withdrawal path is doing the expected historical bookkeeping:

- current load is cleared
- penalty is posted
- historical record is retained
- student is formally withdrawn
- the record remains searchable through archive/history identity

## What This Means For The Canon

This run confirms the withdrawal governance is now behaving like a real registrar-controlled archival event:

- no silent deletion
- no fake enrollment state
- no loss of ledger history
- no accidental reactivation of the student
- document-release blocking remains tied to the withdrawn record and outstanding balance

## Why This Is Useful For The Next Agent

If the next agent resumes this thread, the important anchor is:

- the registrar withdrawal path is live and demonstrably writes the correct historical state
- the ledger receives proper penalty entries
- the withdrawn student becomes inactive and blocked from ordinary enrollment actions
- the archive key / history trail remains the correct lookup path for past records

## Practical Handoff Summary

The current registrar state is strong enough to continue policy work on top of:

- withdrawal governance
- archive/history lookup
- document-release blocking
- student-number release / reuse policy
- shift cleanup behavior
- ledger integrity for withdrawn students

## Follow-up Verification Pass

After the fresh withdrawal demo, I also checked two guardrail paths:

### 1) Ledger Lookup On A Student With Payment History

- Student checked: `SPRINT-DEMO-2026-001`
- Result:
  - enrollment ledger lookup opened normally
  - the database state stayed stable:
    - `status = ACTIVE`
    - `admission_status = ENROLLED`
    - `is_active = 1`
    - `enrollment_blocked = 0`
  - the student also has a real payment row in the `payments` table:
    - OR/reference: `SPRINT-DEMO-2026-001`
    - amount: `PHP 1,000.00`
    - status: `COMPLETED`

This confirms ordinary ledger reading is not mutating the student record.

### 2) Withdrawn-Student Hard Blocks

- Student checked: `ADDCLS-2026-001`
- Document release:
  - `Print COG` shows the block message:
    - `Certificate of Grades release blocked: withdrawn student has outstanding balance of PHP 6,673.50. Settle the ledger before official document release.`
- Shift path:
  - the withdrawn profile does not expose a program-shift form
  - no `Apply Program Shift` control is rendered
  - no `/student-manager/shift-program` form is present on the withdrawn profile page

This is stronger than a soft validation error because the withdrawn record is being held out of the shift UI entirely.
