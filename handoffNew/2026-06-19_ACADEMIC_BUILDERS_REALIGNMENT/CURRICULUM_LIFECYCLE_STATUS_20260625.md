# Curriculum Lifecycle Status Patch

Date: 2026-06-25

## Why This Exists

`Active` was ambiguous. Old curriculum versions may still be valid for returning students, but they should not be shown as the active/current curriculum being offered to new/default enrollment flows.

The registrar system now uses an explicit lifecycle label on `curriculum_templates.lifecycle_status`.

## Canonical Labels

- `CURRENT`: the one curriculum version currently offered for a program. New/default assignment, fee readiness, scholarship subject reads, and scheduling readiness should prefer this.
- `LEGACY`: an old curriculum version that is no longer the current offering but can still be assigned to returning students who must finish under that curriculum.
- `DRAFT`: editable working copy. It should not drive default enrollment, schedules, fees, or scholarship subject picks.
- `ARCHIVED`: retained historical record. It should not be assigned to students.

## UI Behavior

- Curriculum dashboard tab formerly labeled `Active` is now labeled `Current`.
- Curriculum detail has explicit lifecycle actions:
- `Publish & Set Current` for editable drafts with course rows.
- `Set Current` for legacy/archived curriculum versions that should become the current offering.
- `Mark Legacy` for current versions that should stop being the current offering.
- `Archive` for non-current versions that should be kept only as records.
- Maintenance action `Repair Current Labels` demotes duplicate current versions to legacy.

## Backend Rules

- Publishing or setting a curriculum as `CURRENT` demotes any other current curriculum in the same program to `LEGACY`.
- A current curriculum cannot be archived directly. Set another curriculum current first.
- A current curriculum cannot be marked legacy unless another current curriculum already exists for that program.
- Archived curricula are blocked when active student curriculum assignments still point at them.
- Drafts remain the only editable curriculum versions.

## Verification SQL

Run:

```sql
source handoffNew/sql_manual/12_curriculum_lifecycle_status_20260625.sql
```

Important expected result:

- Verification 1 should return zero rows. If it returns any program, that program still has more than one `CURRENT` curriculum.

## Manual Test Path

1. Open `Academics > Curriculum`.
2. Confirm the first tab says `Current`.
3. Pick a program that has more than one curriculum version.
4. Open a non-current curriculum with course rows.
5. Click `Set Current`.
6. Return to the dashboard and confirm only that version is labeled `Current Offering`.
7. Open the previous current version and confirm it is now `Legacy`.
8. Clone a curriculum, confirm the clone is `Draft`, edit it, then use `Publish & Set Current`.
9. Confirm the prior current version moves to `Legacy`.
10. Try archiving a current version. The system should block it and ask you to set another current version first.
