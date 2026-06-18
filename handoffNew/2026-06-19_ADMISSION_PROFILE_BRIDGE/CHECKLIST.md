# Admission Profile Bridge Checklist

Date: 2026-06-19

Use this checklist for a fresh-terminal or UAT pass of the registrar-side admission bridge.

## Preconditions

- Registrar is running on `http://localhost:8083/registrar`
- Registrar and Admission point to the same database
- `APP_UPLOAD_DIR` matches the Admission upload directory
- A student exists whose `students.reference_number` matches an Admission applicant `reference_number`
- That applicant has at least one document row or legacy document field populated

Verified live reference for this smoke:

- Student number: `26-1-00003`
- Admission reference: `EAC-4A09644A`
- Opened file: Form 138 PDF

## Browser test

1. Open `/login` and sign in with the local demo admin account.
2. Open `/admin/student-manager`.
3. Search the linked student number.
4. Confirm the page shows the new read-only admission snapshot.
5. Confirm the applicant document list shows submitted and missing items correctly.
6. Click a document `View` action and confirm it opens inline.
7. Confirm registrar profile edits still target registrar-owned fields only.

## Expected results

- Admission data is visible but not editable from Registrar.
- Applicant documents are visible but remain read-only in Registrar.
- Document links resolve only for the linked applicant/student pair.
- The page should not loop forever to login unless the session expired.

## Stop conditions

- Missing admission link
- Missing upload directory
- Document access without a linked applicant
- Login loop after fresh sign-in
