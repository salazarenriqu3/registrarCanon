# Program Shift and Load Cleanup

Canonical workspace: `E:\registrarCanon_canon`

## Intended Behavior

- shifting a student clears current-term load
- shifting does not mean the student is withdrawn from school
- the assigned destination curriculum remains the basis for load and subject eligibility
- after shifting, the student should still be able to bulk-add eligible subjects from the new curriculum

## Do Not Do

- do not hide Add Subjects just because the current-term load became zero after a shift
- do not reclassify the student as school-withdrawn unless the user explicitly performed withdrawal
- do not let old legacy fallbacks override explicit curriculum assignment
