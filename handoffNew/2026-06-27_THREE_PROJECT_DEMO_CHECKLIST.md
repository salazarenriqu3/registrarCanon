# Three-Project Demo Checklist

Date: 2026-06-27  
Goal: a short, repeatable presentation path for Registrar + Enrollment3 + Admission

## Before you start

- Registrar: `http://localhost:8083/registrar/login`
- Enrollment3: `http://localhost:8082/login`
- Admission: `http://localhost:8081/admission/admin/login`

### Logins

- Registrar: `admin / 1234`
- Enrollment3: `cashier / 1234`
- Admission: `encoder-adms / encoderadms`

## Demo order

### 1) Admission proof

Purpose: show the applicant source record and uploaded docs.

1. Sign in to Admission as `encoder-adms / encoderadms`.
2. Open applicant `DEMO-SANTOS-001`.
3. Show:
   - application details
   - uploaded documents
   - document trail / log history
4. Point out that `2026-1001` is the registrar-side student bridge for this applicant.

### 2) Enrollment proof

Purpose: show the payment / readiness side that Registrar reads from.

1. Sign in to Enrollment3 as `cashier / 1234`.
2. Open the cashier or term readiness workspace.
3. Show the current term state and that the registrar only reads term-fee / readiness data.
4. If needed, show `prof.cruz / 1234` later for the grading bridge.

### 3) Registrar core student profile

Purpose: the main registrar story.

1. Sign in to Registrar as `admin / 1234`.
2. Open `/admin/student-manager?username=2026-1001`.
3. Show:
   - identity card
   - current load
   - admission snapshot
   - applicant documents
   - document trail
   - reg-form history
4. Print the registration form.

### 4) Add subject flow

Target student: `ADDCLS-2026-001`

1. Open `/admin/student-manager?username=ADDCLS-2026-001`.
2. Scroll to `Add Subjects`.
3. Search the subject list.
4. Pick a valid block-section offering.
5. Click `Add`.
6. Reopen reg-form history and document trail to show the event.

### 5) Shift flow

Target student: `TSHFT-2026-001`

1. Open `/admin/student-manager?username=TSHFT-2026-001`.
2. Show the current load or withdrawn load state.
3. Open the program shift workspace.
4. Select the target program and curriculum.
5. Submit the shift.
6. Explain that the shift keeps the student active and reassigns the curriculum source of truth.

### 6) Withdrawal flow

Target student: `SPRINT-DEMO-2026-001`

1. Open `/admin/student-manager?username=SPRINT-DEMO-2026-001`.
2. Show the committed current classes.
3. Run the withdrawal / direct-drop path.
4. Show that the current load clears and the trail records the action.

### 7) Scholarship proof

Target students:

- `SCH-UAT-ELIGIBLE`
- `SCH-UAT-LOWUNITS`

1. Open `/admin/scholarships`.
2. Inspect `SCH-UAT-ELIGIBLE`.
3. Show the eligible state.
4. Inspect `SCH-UAT-LOWUNITS`.
5. Show the rejection reason tied to the curriculum/year/semester requirement.

### 8) Faculty grading bridge

Target account: `prof.cruz / 1234`

1. Sign in to Registrar as `prof.cruz`.
2. Open the grades workspace.
3. Show a grading-ready class.
4. Save / submit as the bridge to official academic posting.

## Fast demo set

If you only want the shortest impressive pass, use these six checkpoints:

1. Admission: `DEMO-SANTOS-001`
2. Registrar profile: `2026-1001`
3. Add subject: `ADDCLS-2026-001`
4. Shift: `TSHFT-2026-001`
5. Withdrawal: `SPRINT-DEMO-2026-001`
6. Scholarship: `SCH-UAT-ELIGIBLE`

## What to emphasize

- Admission owns the applicant source data and uploads
- Enrollment3 owns payment/readiness and pre-enlistment support
- Registrar owns the official record, academic enforcement, and auditable history
- Block sections are the source of offerings
- Irregular students customize from block-section offerings, not dedicated irregular classes
- Room and schedule conflicts are real constraints
- Curriculum assignment is the source of truth for load and scholarship logic

## Reset reminder

- Use the full demo seed before presenting
- Re-run the verify SQL if you want to confirm the dataset before a live run
- If you mutate the showcase students, reload the seed bundle before the next demo

## Short rehearsal

If you want the fastest run-through, use:

- `handoffNew/2026-06-27_THREE_PROJECT_5_MINUTE_REHEARSAL.md`
- `handoffNew/2026-06-27_THREE_PROJECT_CUE_CARD.md`
- `handoffNew/2026-06-27_TESTING_DEMO_FILE_MAP.md`
