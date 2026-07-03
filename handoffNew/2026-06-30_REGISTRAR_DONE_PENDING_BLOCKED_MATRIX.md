# Registrar Done / Pending / Blocked Matrix

Last updated: 2026-06-30

This note is meant for the next agent and for presentation use. It condenses the current registrar canon into a strict status matrix so we can see what is already solid, what still needs work, and what is intentionally out of scope.

## Working Rule

- **Done** means the registrar repo already has the logic or enforcement in place for the intended active scope.
- **Pending** means the registrar repo still needs refinement, hardening, or UAT validation.
- **Blocked** means the item is deliberately outside registrar scope or depends on a retired / external ownership path.

## High-Level Read

- The registrar project is now mainly an **identity, academic master data, enforcement, archive, and audit** system.
- Enrollment owns applicant-facing accreditation authoring and continuing-term manual pre-advising.
- Registrar consumes or approves those outcomes where needed, but does not need to rebuild them.
- Withdrawn students are historical records and must stay that way.

## Matrix

| Area | Status | Registrar-side meaning | Notes |
|------|--------|------------------------|-------|
| Withdrawn-student identity lock | **Done** | Withdrawn/inactive students are blocked from active academic actions | Subject add, bulk add, shift, curriculum reassignment, and official document release are already governed as withdrawn-state concerns |
| Student number reissue / reuse | **Done** | Historical identity is preserved while the live student number can be released and reused through the controlled registry flow | Use archive key / release registry, not manual revival |
| Program shift cleanup | **Done** | A shift can clear the current-term load without invoking full school withdrawal | This is distinct from student withdrawal and must stay separate |
| Shift-to-target-curriculum enforcement | **Done** | Destination curriculum stays program-scoped and backend validation rejects cross-program curriculum picks | UI filtering and backend ownership validation now work together so the selected curriculum cannot drift across programs |
| Shift on not-yet-enrolled students | **Pending** | Pre-enrollment shifts should remain a quick reassignment path | Needs final demo/UAT confirmation that the target curriculum and term fields are always set correctly |
| Withdrawal governance | **Done** | Full school withdrawal is a registrar-owned historical state with trail and archive | Must remain separate from subject drop / shift cleanup |
| Withdrawal with outstanding balance | **Done** | Full withdrawal is still allowed when balance remains, but the balance stays collectible on the historical record | Request history and withdrawal events now preserve the balance-hold note, and official document release remains blocked until settlement |
| Official document release blocking | **Done** | Withdrawn students with open balance are blocked from official document release | Document Trail should record blocked attempts |
| Archive custody tracking | **Done** | Physical record-room custody events are tracked and mirrored | Request, release, evaluation, scan, return, and refile are already in scope |
| Academic scholarship | **Done** | Registrar grants academic scholarship only | Uses SQL-seeded academic results and configurable policy values |
| Scholarship policy knobs | **Pending** | GWA, minimum units, and PE/NSTP disqualification must remain configurable and enforced | Athlete/sports cases are intentionally out of focus for now |
| Curriculum lifecycle clarity | **Pending** | Active vs legacy curriculum labels need to remain understandable in the UI | Old curricula can still be assignable to returning students, but should not be mislabeled as actively offered |
| Curriculum selection by program | **Done** | Program choice filters curriculum choices to that same program and backend validation rejects mismatches | This now protects the shift workflow on both the UI and service side |
| Section and schedule hardening | **Done** | Room, faculty, section, and schedule collision rules are enforced | Same-term room/faculty/section conflicts should not pass silently |
| Room monitoring | **Done** | Room usage is a first-class operational view, not just slot counting | This is separate from section slot monitoring |
| Course catalog drilldown | **Done** | The shared catalog already exposes a concrete `Where Used` drilldown for curricula, sections, records, and prerequisites | The remaining concern is UX clarity, not missing usage data |
| Student profile document visibility | **Done** | Admission-submitted files are viewable/downloadable from Registrar custody and the access is trailed | Registrar remains the safekeeping view for student documents rather than re-authoring admission uploads |
| Registrar audit trail | **Done** | User and action trails already belong in the canon | Keep tracking broad enough to support future accountability reviews |
| Registrar read-only dependency on Enrollment accreditation | **Blocked** | Registrar should not recreate applicant accreditation authoring | Registrar may consume or approve downstream outcomes, but the authoring flow belongs to Enrollment |
| Continuing-term manual pre-advising authoring | **Blocked** | Enrollment owns the continuing irregular / dean pre-advising workflow | Registrar should not duplicate that module unless scope is reopened |
| Applicant pre-registration authoring | **Blocked** | Admission/Enrollment owns applicant-time pre-reg and dean accreditation | Registrar can approve or reference the outcome, not replace the workflow |
| Grading workflow | **Blocked** | Grading was retired from registrar scope | Registrar can accept grade inserts for reporting / scholarship / record display only |
| Cashier / payment authoring | **Blocked** | Fee authoring and cashier/payment logic belong to Enrollment / Accounting | Registrar may read the result but should not own the transaction engine |

## What Is Already Solid Enough To Present

- The three-system ownership split is now stable enough for presentation.
- Withdrawal is registrar-governed and historical, not an active-state alias.
- Shift cleanup is separate from student withdrawal.
- Academic scholarship is registrar-owned and academic-only.
- Section/schedule conflict hardening is already a real enforcement layer.

## What Still Needs Work Before We Call Everything Fully Polished

1. Re-test the not-yet-enrolled shift path end to end in demo/UAT.
2. Keep scholarship configuration and enforcement verified during UAT.
3. Make the curriculum labels and course-usage presentation easier to understand.
4. Keep UAT clean across the registrar-only scope without drifting back into retired Enrollment ownership.
5. Confirm the Enrollment ledger remains read-only during lookup so it cannot silently change student status while registrar testing is in progress.

## What Not To Rebuild In Registrar

- applicant dean accreditation authoring
- continuing-term manual pre-advising authoring
- cashier/payment authoring
- grading workflow
- admission intake logic

## Short Presentation Line

- “Registrar now owns identity, archive, academic enforcement, and shift/withdrawal governance; Enrollment owns applicant accreditation and continuing-term pre-advising.”

## Handoff Reminder

If the next agent resumes work from this note, they should:

1. Treat withdrawn students as historical records.
2. Keep shift cleanup separate from full withdrawal.
3. Keep scholarship enforcement academic-only and configurable.
4. Keep enrollment-facing accreditation authoring out of registrar scope.
5. Use the current handoff notes as the source of truth before coding.
