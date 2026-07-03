# Registrar Identity Bridge Fix

Date: 2026-07-02

Purpose:

- Record the registrar-side fix that keeps returned student identities stable when the other system sends back a live number, archive key, or a previously withdrawn identity.

## What changed

- `StudentProfileService` now resolves identity before reading or updating profile/archive rows.
- `ensureArchiveKey(...)` now reuses the existing archive identity from the release registry or archive snapshots instead of minting a fresh archive key when the student has already been archived.
- `StudentArchiveCustodyService` now resolves the canonical student identity before writing archive custody rows or reading archive summaries.
- `AcademicGradingService.findStudentByIdOrName(...)` now falls back to the archive-aware registrar resolver when a direct student search returns nothing.

## Why it matters

- The registrar side must not invent a new archive key when the enrollment system sends a returned identity in a different shape.
- Withdrawn or archived records should continue to read back through the same archive handle.
- Reissued live numbers stay reusable for new enrolees without losing the historical archive trail.

## Outcome

- Archive-aware read paths now stay stable across the registrar profile, archive custody, and student lookup surfaces.
- Focused H2 tests passed for the new archive-aware identity bridge.
