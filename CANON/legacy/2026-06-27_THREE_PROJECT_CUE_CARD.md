# Three-Project Cue Card

Date: 2026-06-27

## Open these tabs

- Admission: `http://localhost:8081/admission/admin/login`
- Enrollment3: `http://localhost:8082/login`
- Registrar: `http://localhost:8083/registrar/login`

## Logins

- Admission: `encoder-adms / encoderadms`
- Enrollment3: `cashier / 1234`
- Registrar: `admin / 1234`
- Faculty: `prof.cruz / 1234`

## Demo IDs

- `DEMO-SANTOS-001` = admission applicant docs
- `2026-1001` = registrar profile, reg form, history, trail
- `ADDCLS-2026-001` = add subject
- `TSHFT-2026-001` = shift
- `SPRINT-DEMO-2026-001` = withdrawal
- `SCH-UAT-ELIGIBLE` = scholarship pass
- `SCH-UAT-LOWUNITS` = scholarship fail

## One-line story

- Admission owns the applicant record.
- Enrollment3 owns readiness and payment.
- Registrar owns the official academic record.

## Fast click path

1. Admission: open `DEMO-SANTOS-001` and show uploaded docs.
2. Enrollment3: show cashier / readiness.
3. Registrar: open `2026-1001` and show profile, documents, reg-form history.
4. Registrar: use `ADDCLS-2026-001` for one add.
5. Registrar: use `TSHFT-2026-001` for shift.
6. Registrar: use `SPRINT-DEMO-2026-001` for withdrawal.
7. Registrar: use `SCH-UAT-ELIGIBLE` and `SCH-UAT-LOWUNITS` for scholarship.

## What to say

- Block sections are the source of offerings.
- Irregular students customize from block-section offerings.
- Room conflicts, faculty conflicts, and max-load rules are enforced.
- Curriculum assignment is the source of truth for load and scholarship rules.
