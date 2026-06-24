# Registrar Academic Builders Realignment

Date: 2026-06-19

Scope: registrar only. The retired admission/pre-registration/dean bridge is not part of this patch.

## Canonical ownership

- Course Catalog owns reusable course identity, title, department, status, lecture units, and laboratory units.
- Total credit units are derived as `lecture units + laboratory units`.
- Curriculum Management places catalog courses into a program version by year and semester.
- Publishing a working curriculum draft sets that version as the program's `CURRENT` offering and moves the previous current offering to `LEGACY`.
- Class Scheduling materializes the current curriculum offering into regular block offerings, then assigns faculty, time, and room.
- Faculty Teaching Load counts total catalog credit units once per assigned class section unless a coordinator-equivalent load is configured.

## UI changes

- The normal curriculum path is displayed as three explicit steps: **New Curriculum Draft**, build the course plan, then **Publish & Set Current**.
- Curriculum recovery/import actions are collapsed under **Import & Maintenance** and identified as administrative tools outside the normal workflow.
- **New Curriculum Draft** replaces the ambiguous placeholder action in the normal workflow.
- **Publish & Set Current** makes the current-offering transition explicit.
- Curriculum rows display lecture, laboratory, and total units.
- Course Catalog edits lecture and laboratory units and derives total units.
- Course Catalog's **Where Used** action opens concrete curriculum, section, record, and prerequisite usage.
- Scheduling copy now explains current-offering materialization, TBA rooms, and collision blocking.

## Technical changes

- Startup schema repair adds `courses.lec_units` and `courses.lab_units` when absent and backfills legacy courses as lecture-only.
- Catalog and curriculum manual-course saves validate a total from 1 to 12 units.
- Existing catalog courses remain shared records; attaching one to a curriculum does not mutate its catalog setup.
- New endpoint: `GET /admin/courses/usage?courseId={id}`.

See `TEST_CHECKLIST.md` for focused UAT.
