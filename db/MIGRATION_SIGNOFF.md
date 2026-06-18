# Database Migration Sign-Off

Use this template when promoting schema changes from Registrar dev into shared, staging, or production-like databases.

## Release

| Field | Value |
|-------|-------|
| Release / sprint | |
| Date | |
| Environment | dev / staging / prod |
| Applied by | |

## Schema changes

| Object | Change type | Script / auto-migrate | Verified |
|--------|-------------|----------------------|----------|
| `programs.duration_years` | ADD COLUMN | `DatabaseSetupService` | |
| `courses.lec_units` | ADD COLUMN | `DatabaseSetupService` | |
| `courses.lab_units` | ADD COLUMN | `DatabaseSetupService` | |
| sprint 1-10 uplift objects | MIXED | `db/migrations/20260619_sprint_1_10_upgrade.sql` | |

## Verification checklist

- [ ] Application starts cleanly
- [ ] `mvn test -Dtest=!ModulithTests` passes
- [ ] Program Builder loads and lists existing programs
- [ ] Program create/edit works for admin users
- [ ] Curriculum and Admissions still resolve active programs correctly
- [ ] Course Catalog still reads separated lecture/lab units correctly

## Rollback notes

| Change | Rollback action |
|--------|-----------------|
| New nullable/defaulted columns | Leave in place unless a formal rollback script is prepared |
| Idempotent upgrade scripts | Re-run only after reviewing partial application state |

## Sign-off

| Role | Name | Signature / date |
|------|------|------------------|
| Developer | | |
| DBA | | |
| Registrar lead | | |
