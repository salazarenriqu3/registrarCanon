# Focused UAT Checklist

## Course Catalog

1. Open **Academics > Course Catalog**.
2. Create a temporary course with 2 lecture units and 1 laboratory unit.
3. Confirm the table shows `3` total and `2 lec + 1 lab`.
4. Edit the course and confirm both unit fields reopen correctly.
5. Click the location-pin action and confirm the usage dialog opens.

## Curriculum

1. Open **Academics > Curriculum** and confirm maintenance tools are collapsed.
2. Create or clone a working draft for a test program.
3. Search for the temporary catalog course and attach it.
4. Confirm the row shows lecture, laboratory, and total units separately.
5. Confirm only year and semester placement are editable for an attached catalog course.
6. Add at least one course, click **Publish & Activate**, and accept the confirmation.
7. Return to Active and History views; confirm the new version is active and the previous version is historical.

## Usage Trace

1. Return to Course Catalog and open the temporary course's usage dialog.
2. Confirm the exact program, curriculum name, academic year, year level, and semester appear.
3. After creating a class section, confirm its section code and term appear in the same dialog.

## Scheduling Constraints

1. Materialize a regular block from an active curriculum.
2. Save one schedule with room and faculty.
3. Attempt an overlapping schedule in the same term using the same room; confirm it is rejected.
4. Attempt an overlapping schedule in the same term using the same faculty; confirm it is rejected.
5. Create a non-overlapping schedule with room left TBA; confirm it is accepted.

## Faculty Load

1. Open **Faculty Teaching Load** for the same term.
2. Confirm each assigned section contributes its total catalog credit units once.
3. Confirm the detail panel lists the exact sections behind the summary total.

## Automated Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=CourseCatalogServiceTest,ScheduleConflictValidatorTest" test`
- `mvn -q test` currently has one known unrelated failure: `ModulithTests` reports pre-existing module cycles among academic, curriculum, finance, and scholarship.
