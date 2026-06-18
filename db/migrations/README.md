# Database Migrations

This folder is the reserved home for non-destructive upgrade scripts when we need to move an existing shared `eacdb` forward without running a full rebuild.

## Current local canon

- Fresh-machine setup is still driven by `setup\RUN_FRESH_SETUP.cmd`
- A number of schema adjustments are auto-applied on application startup by `DatabaseSetupService`
- This now includes registrar-side support for:
  - `courses.lec_units`
  - `courses.lab_units`
  - `programs.duration_years`

## When to use this folder

Add versioned SQL here when we decide to package a manual upgrade path for staging or production-like databases.

Until then, treat this folder as documentation plus sign-off support rather than a runnable migration bundle.

## Sign-off

Use `db/MIGRATION_SIGNOFF.md` whenever a manual shared-database upgrade is prepared.
