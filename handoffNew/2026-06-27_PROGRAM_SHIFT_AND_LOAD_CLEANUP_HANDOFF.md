# Program Shift And Load Cleanup Handoff - 2026-06-27

## Scope

- Registrar-only Student Profile and Program Shift behavior.
- Canon workspace: `E:\registrarCanon_canon`.
- Branch used during implementation: `codex/registrar-withdrawal-governance`.
- This does not revive the retired Dean / irregular-advising bridge.

## Current Behavior

1. Empty current load labeling
   - A student with a Student ID but no current-term subject load now shows **No Current Subject Load**.
   - This avoids implying the student is not enrolled in school.
   - The profile state can still remain `ENROLLED` while the current subject list is empty.

2. Clear Subject Load for Shift
   - Student Profile exposes **Clear Subject Load for Shift** when the student has current enrolled/staged subjects.
   - The registrar supplies one reason and optional remarks for the full cleanup.
   - The action clears all current-term subject rows, including the final remaining subject.
   - The cleanup archives class-line snapshots as `SHIFT_PROGRAM_CLEANUP`.
   - The cleanup keeps the student active/enrolled and must not trigger full school withdrawal.
   - Full school withdrawal remains a separate registrar action.

3. Program Shift curriculum filtering
   - Target Program controls the Destination Curriculum dropdown.
   - Destination Curriculum options are hidden/disabled unless they belong to the selected target program.
   - The fallback **Use target program active curriculum** remains available.
   - Backend validation should still reject impossible program/curriculum combinations.

4. Shift error handling
   - Program shift POST failures now redirect back to Student Profile with a flash `errorMessage`.
   - The user should not see a Spring Whitelabel page for ordinary backend validation or rollback failures.

## Main Files

- `src/main/resources/templates/admin_student_manager.html`
- `src/main/java/com/iuims/registrar/portal/EnrollmentController.java`
- `src/main/java/com/iuims/registrar/withdrawal/WithdrawalController.java`
- `src/main/java/com/iuims/registrar/withdrawal/WithdrawalService.java`
- `src/test/java/com/iuims/registrar/withdrawal/WithdrawalControllerTest.java`
- `src/test/java/com/iuims/registrar/withdrawal/WithdrawalServiceDirectDropTest.java`
- `handoffNew/CURRENT_STATE_MAP.md`

## Validation Already Performed

- `mvn -q "-Dtest=WithdrawalControllerTest,WithdrawalServiceDirectDropTest" test`
- `mvn -q -DskipTests compile`
- `mvn -q -DskipTests package`
- Student Profile smoke check:
  - `SCH-UAT-LOWUNITS` shows **No Current Subject Load**.
  - `SCH-UAT-ELIGIBLE` shows **Clear Subject Load for Shift** and still keeps **Withdraw Student From School** separate.
- Program Shift smoke check:
  - Rendered page includes target-program curriculum markers and `filterShiftCurricula`.
  - An intentionally mismatched target program/curriculum POST redirected back to Student Profile instead of Whitelabel.

## Notes For The Next Agent

- If a selected target program has no assignable `CURRENT` or `LEGACY` curriculum, the UI may only show the active-curriculum fallback and the backend can still reject the shift.
- The cleanup is intentionally different from student withdrawal/dropping out of school.
- A known non-fatal startup warning may still appear: `Unknown column 'RESERVED' in 'WHERE'`.
- Treat existing dirty/untracked work carefully. Several files were already modified by prior agents before this handoff.
