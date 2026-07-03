# Three-Project 5-Minute Rehearsal

Date: 2026-06-27

## Goal

Show the full registrar story fast:

- admission source record
- enrollment readiness
- registrar official record
- one add
- one shift
- one withdrawal
- one scholarship check

## Minute 0 to 1: Open the three apps

1. Open Admission: `http://localhost:8081/admission/admin/login`
2. Open Enrollment3: `http://localhost:8082/login`
3. Open Registrar: `http://localhost:8083/registrar/login`

Logins:

- Admission: `encoder-adms / encoderadms`
- Enrollment3: `cashier / 1234`
- Registrar: `admin / 1234`

## Minute 1 to 2: Admission proof

1. Open applicant `DEMO-SANTOS-001`
2. Show uploaded documents
3. Show the trail or log history
4. Say: this is the upstream applicant record tied to registrar student `2026-1001`

## Minute 2 to 3: Registrar profile and add-subject

1. Open `/admin/student-manager?username=2026-1001`
2. Show identity, current load, admission snapshot, documents, and reg-form history
3. Print the registration form
4. Switch to `ADDCLS-2026-001`
5. Add one valid subject from a block-section offering
6. Show the updated reg-form history or document trail entry

## Minute 3 to 4: Shift and withdrawal

1. Switch to `TSHFT-2026-001`
2. Show the shift workspace and submit a program shift
3. Switch to `SPRINT-DEMO-2026-001`
4. Show the current load
5. Run the withdrawal / direct-drop path
6. Show the trail entry

## Minute 4 to 5: Scholarship and close

1. Open `/admin/scholarships`
2. Inspect `SCH-UAT-ELIGIBLE`
3. Inspect `SCH-UAT-LOWUNITS`
4. Say:
   - eligibility is curriculum / year-level / semester specific
   - registrar only approves academic results and official records
   - Enrollment3 owns payment readiness
   - Admission owns applicant intake

## Fast fallback

If you only have 2 minutes, show only these:

1. `DEMO-SANTOS-001`
2. `2026-1001`
3. `ADDCLS-2026-001`
4. `SCH-UAT-ELIGIBLE`

## Reminder

- Use the seeded data bundle before the rehearsal
- Keep the demo students unchanged until after the presentation
