# Registration Form PDF Alignment Note

Date: 2026-06-28

## What changed

- Registrar `Print Registration Form` now returns a generated PDF instead of the old browser-print HTML page.
- The registrar PDF is now structured to follow the same two-page layout family as the admission pre-registration form:
  - centered school header
  - boxed student identity block
  - subject table with section and schedule
  - fee details and payment details side by side
  - rules page and printed footer

## Source of truth

- Admission sample format reference:
  - `C:\newws\admission\src\main\java\com\example\enrollment\service\prereg\PreRegPdfService.java`
- Registrar implementation:
  - `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\forms\RegistrationFormPdfService.java`
  - `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\EnrollmentController.java`

## Current behavior notes

- Registrar still computes financial totals from registrar-owned assessment logic.
- Itemized MISC and OTHER fee rows come from scoped term fee settings for the student's program, year level, and semester.
- Forwarded balance and withdrawal charges are injected into the PDF breakdown when present so the printed total remains aligned with registrar finance.
- Student-side `/my-load` remains an HTML screen; the admin `Print Registration Form` action is the PDF output aligned to admission format.
