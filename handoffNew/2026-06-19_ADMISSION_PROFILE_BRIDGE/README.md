# Admission Profile Bridge

Date: 2026-06-19

This dated handoff captures the registrar-only bridge that reads applicant identity and document data from Admission and presents it on the Student Profile page as read-only context.

## What changed

- Registrar Student Profile now shows a read-only admission snapshot for linked applicants.
- Registrar Student Profile now shows applicant documents and their status without allowing registrar-side edits to admission-owned records.
- Document viewing is authenticated and ownership-checked.
- The upload root is configurable through `APP_UPLOAD_DIR` so the Registrar app can point at the same files used by Admission.

## Scope boundaries

- Do not move admission intake, applicant approval, or payment collection into Registrar.
- Do not revive the retired irregular/dean advising scope.
- Keep registrar profile editing limited to registrar-owned fields.

## Operational note

If the Student Profile page redirects to `/login`, the session has expired. Re-authenticate and continue the test from the same profile search.

## Verified live smoke

- Student number: `26-1-00003`
- Admission reference: `EAC-4A09644A`
- Result: Registrar Student Profile showed the read-only admission snapshot and applicant documents, and the Form 138 `View` link opened the PDF inline.
