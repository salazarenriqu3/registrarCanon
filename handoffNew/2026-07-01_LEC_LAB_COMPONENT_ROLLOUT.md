# 2026-07-01 - Lecture / Lab Component Rollout

## Status
- Supersedes `2026-06-30_LEC_LAB_COMPONENT_CLARIFICATION.md`.
- Registrar canon now treats mixed lecture/laboratory courses as separate enlistable course records.
- Legacy mixed rows are migrated forward into archived source rows plus live `BASE-LEC` / `BASE-LAB`
  component rows.

## Business Rule
- If a course has lecture units and laboratory units, it must be represented as two course components:
  - `BASE-LEC` for the lecture component.
  - `BASE-LAB` for the laboratory component.
- Each component has its own `course_id`, `credit_units`, schedule/section path, enlistment path, and load accounting.
- Components are linked by `course_family_code`.
- `component_type` identifies `LEC`, `LAB`, or `SINGLE`.

## Implemented Registrar Behavior
- Course Catalog creates two component rows when a new course is saved with both lecture and laboratory units.
- Curriculum manual-add creates two curriculum rows when a mixed lecture/laboratory course is entered.
- Pure lecture, pure lab, and ordinary non-split courses remain one row and are marked by `component_type`.
- Existing legacy mixed rows are migrated by archiving the source row, creating live `BASE-LEC` / `BASE-LAB`
  siblings, and re-homing curriculum / prerequisite references to the new components.

## Schema Additions
- `courses.course_code` widened to `VARCHAR(40)`.
- `courses.component_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE'`.
- `courses.course_family_code VARCHAR(40) NULL`.
- `courses.parent_course_id INT NULL`.
- Startup repair backfills `component_type` and `course_family_code` for existing rows.

## Remaining Follow-Up
- Review whether any historical section, enlistment, or grade rows should also be re-keyed during a maintenance window.
- Review scheduling seed SQL that currently infers room type from `lab_units`; after old rows are migrated, it can rely on `component_type`.
- Review prerequisite behavior for component families. Current manual-add applies prerequisites to both components.
