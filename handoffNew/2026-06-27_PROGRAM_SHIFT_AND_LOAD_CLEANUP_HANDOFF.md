# Program Shift And Load Cleanup Handoff - 2026-06-27

## Scope

- Registrar-only Student Profile and Program Shift behavior.
- Canon workspace: `E:\registrarCanon_canon`.
- Branch used during implementation: `codex/registrar-withdrawal-governance`.
- This does not revive the retired Dean / irregular-advising bridge.
- Program shift is a curriculum transition. Enrollment3 remains the authoring home for continuing/irregular pre-advising and draft generation.

## Current Behavior

1. Empty current load labeling
   - A student with a Student ID but no current-term subject load now shows **No Current Subject Load**.
   - This avoids implying the student is not enrolled in school.
   - The profile state can still remain `ENROLLED` while the current subject list is empty.
   - If an explicit current curriculum assignment exists, the Add Subjects workspace remains valid even while the current load is empty.

2. Clear Subject Load for Shift
   - Student Profile exposes **Clear Subject Load for Shift** when the student has current enrolled/staged subjects.
   - The registrar supplies one reason and optional remarks for the full cleanup.
   - The action clears all current-term subject rows, including the final remaining subject.
   - The cleanup archives class-line snapshots as `SHIFT_PROGRAM_CLEANUP`.
   - The cleanup keeps the student active/enrolled and must not trigger full school withdrawal.
   - Full school withdrawal remains a separate registrar action.
   - Cleanup must not remove or hide the explicit current curriculum assignment.

3. Program Shift curriculum filtering
   - Target Program controls the Destination Curriculum dropdown.
   - Destination Curriculum options are hidden/disabled unless they belong to the selected target program.
   - The fallback **Use target program active curriculum** remains available.
   - Backend validation should still reject impossible program/curriculum combinations.
   - A successful shift must leave `student_curriculum_assignments.is_current = 1` pointing at the destination curriculum.

4. Shift error handling
   - Program shift POST failures now redirect back to Student Profile with a flash `errorMessage`.
   - The user should not see a Spring Whitelabel page for ordinary backend validation or rollback failures.

5. Shifted student bulk add
   - A shifted student with zero current-term load now shows **Shifted Student - Ready for Curriculum Load** instead of the generic empty-load wording.
   - The Current Load summary shows **Shift completed; ready for curriculum bulk add** and Profile State shows a **Shifted** badge when the current curriculum assignment type is `PROGRAM_SHIFT`.
   - Student Profile exposes **Bulk Add Shifted Curriculum** / **Bulk Add Assigned Curriculum**.
   - The action reuses the same analyzed offering scan as block enrollment, so it respects disabled rows, prerequisites, capacity, conflicts, duplicate course protection, and curriculum load limits.
   - The Student Profile action redirects back to Student Profile and records a `STUDENT_PROFILE_BULK_ENROLL_COMPLETED` document trail event when it adds at least one subject.

## Main Files

- `src/main/resources/templates/admin_student_manager.html`
- `src/main/java/com/iuims/registrar/portal/EnrollmentController.java`
- `src/main/java/com/iuims/registrar/withdrawal/WithdrawalController.java`
- `src/main/java/com/iuims/registrar/withdrawal/WithdrawalService.java`
- `src/test/java/com/iuims/registrar/withdrawal/WithdrawalControllerTest.java`
- `src/test/java/com/iuims/registrar/withdrawal/WithdrawalServiceDirectDropTest.java`
- `src/test/java/com/iuims/registrar/portal/EnrollmentControllerStudentManagerTest.java`
- `handoffNew/CURRENT_STATE_MAP.md`

## Validation Already Performed

- `mvn -q "-Dtest=WithdrawalControllerTest,WithdrawalServiceDirectDropTest" test`
- `mvn -q -Dtest=EnrollmentControllerStudentManagerTest test`
- `mvn -q -DskipTests compile`
- `mvn -q -DskipTests package`
- Student Profile smoke check:
  - `SCH-UAT-LOWUNITS` shows **No Current Subject Load**.
  - `SCH-UAT-ELIGIBLE` shows **Clear Subject Load for Shift** and still keeps **Withdraw Student From School** separate.
- Program Shift smoke check:
  - Rendered page includes target-program curriculum markers and `filterShiftCurricula`.
  - An intentionally mismatched target program/curriculum POST redirected back to Student Profile instead of Whitelabel.
- Shifted empty-load Student Profile smoke check:
  - `24-1-00001` rendered **Shifted Student - Ready for Curriculum Load**.
  - The page rendered **Bulk Add Shifted Curriculum** without the old generic "Use Program Shift below" instruction.

## Notes For The Next Agent

- If a selected target program has no assignable `CURRENT` or `LEGACY` curriculum, the UI may only show the active-curriculum fallback and the backend can still reject the shift.
- The cleanup is intentionally different from student withdrawal/dropping out of school.
- Shifted students may temporarily have zero current-term load. Treat missing current curriculum assignment as a data defect, not as a reason to fall back to legacy curriculum logic.
- A known non-fatal startup warning may still appear: `Unknown column 'RESERVED' in 'WHERE'`.
- Treat existing dirty/untracked work carefully. Several files were already modified by prior agents before this handoff.
