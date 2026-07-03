# Three-System Demo Go/No-Go Checklist

Date: 2026-07-02

Purpose:

- Use this as the short operational checklist before running the registrar, admission, and enrollment systems together.
- This checklist is focused on the current canon state, especially the ledger / repair-on-view boundary we just audited in the latest D Enrollment bundle.

## Canon sources for this check

- Registrar canon: `E:\registrarCanon_canon`
- Latest D Admission: `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Admitmoko1\admission`
- Latest D Enrollment: `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

## GO if all of these are true

- [ ] Registrar starts cleanly and loads the current student profile pages.
- [ ] Admission can still read applicant data and document trails without losing the link to the registrar profile.
- [ ] Enrollment can still issue / read student numbers and ledger data for the same record identity.
- [ ] Withdrawn students remain blocked from active student actions, while history views still work through the archive key or historical lookup key.
- [ ] Document release remains blocked for withdrawn students with open balance.
- [ ] Shift flow uses the intended withdrawal-cleanup behavior for current-term subjects only, without turning into full school withdrawal.
- [ ] Curriculum / program / course presentation remains consistent across the three systems.
- [ ] Scholarship demo data can be read and explained without needing the retired grading app.
- [ ] LEC / LAB handling is represented as separate enlistment lines where the current canon requires it.
- [ ] Enrollment ledger / cashier behavior is understood as repair-on-view in the current D canon, not mistaken for a pure read-only screen.

## NO-GO if any of these happen

- [ ] A withdrawn student becomes actionable again on a normal active workflow.
- [ ] A withdrawn student can be used as a fresh live identity instead of the archive/history identity.
- [ ] A ledger or cashier page mutates state in an unexpected place that is not one of the documented repair-on-view paths.
- [ ] Payment, balance, or ledger lookup breaks the student status linkage for enrolled students.
- [ ] Admission or enrollment loses the applicant/student number bridge after the latest re-key logic.
- [ ] Withdrawal is treated as school exit when the intent is only subject drop / shift cleanup.
- [ ] Document views lose their custody trail or become detached from the admission submission history.

## Demo order

1. Start the three systems in the current canon order.
2. Confirm a regular applicant is still processed through Admission then Enrollment.
3. Confirm the ledger / cashier pages display expected values for an enrolled student.
4. Confirm a withdrawn student is visible only through historical / archive lookup paths.
5. Confirm the shift scenario does not resurrect withdrawn-school behavior.
6. Confirm document trail and registration form history remain intact.
7. Confirm scholarship and curriculum views still render against the same canonical student record.

## Evidence to capture

- Student profile screenshots
- Admission snapshot / document trail screenshots
- Enrollment ledger screenshots
- Withdrawal history screenshots
- Shift result screenshots
- Any 500 / whitelabel / redirect anomalies

## Short read

If the systems pass this checklist, we have a workable demo posture for the current canon.

If they fail on any of the NO-GO items, treat that as a real alignment defect and fix the owning system before demoing again.
