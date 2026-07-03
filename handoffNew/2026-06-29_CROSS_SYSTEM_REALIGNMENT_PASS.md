# 2026-06-29 Cross-System Realignment Pass

## Purpose

This pass tightened the three-system testing boundary so staff are less likely to train on the wrong owner application.

It does not move business logic between systems.  
It primarily realigns wording, links, and registrar-side authoring surfaces so the current canon is visible in the UI.

## Ownership Canon Applied

- Admission owns applicant intake and qualification.
- Enrollment3 owns dean pre-advising for irregular/transferee applicant flows, fee authoring, payment processing, and enrollment finalization.
- Registrar owns academic master data, downstream academic records, and read-only visibility or approval where registrar authority is required.

## Changes Made

### Admission

- The public qualified-stage status text now points the applicant to Enrollment for payment/enrollment processing instead of Registrar.
- The irregular pre-advising preview wording now points to Enrollment dean pre-advising instead of Registrar faculty advising.
- Admission no longer falls back to Registrar's retired irregular-advising route when building the faculty/dean pre-advising link.
- The applicant-review warning text now says the queue is waiting for Enrollment dean pre-advising.

### Registrar

- The registrar admission-acceptance preview now labels the irregular snapshot as a shared preview instead of implying registrar-owned advising.
- Registrar `Finance Policy` is now treated as a read-only mirror:
  - GET still works for inspection.
  - POST authoring actions redirect back with a read-only ownership notice.
  - The page UI now shows an ownership banner and disables authoring controls.
- Registrar `Program Fees / Term Fees` is now treated as a read-only mirror:
  - GET still works for inspection and readiness visibility.
  - POST authoring/import actions redirect back with a read-only ownership notice.
  - The page UI now shows an ownership banner and disables authoring controls.

## What This Fixes For Three-App Testing

- Staff testing from Admission are pointed to the correct next-owner system for irregular dean pre-advising.
- Staff testing in Registrar are less likely to accidentally maintain fees or finance policy in the wrong application.
- Registrar still retains visibility into fee readiness and irregular/shared snapshot context without remaining an active authoring home.

## Residuals

- Some Admission internal naming still uses legacy `registrar*` field/property names even where the visible UI wording is now aligned.
- Some older cross-system documents still contain registrar-first phrasing and should be treated as historical unless updated.
- Registrar still ships dormant irregular-advising code paths for historical compatibility, but they are not part of the live testing canon.

## Validation

- `mvn -q -DskipTests compile` passed in:
  - `E:\registrarCanon_canon`
  - `E:\AdmitLatest\admission`
