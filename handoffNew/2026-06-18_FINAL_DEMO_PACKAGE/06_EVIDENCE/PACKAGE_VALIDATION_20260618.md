# Package Validation Evidence

Validation date: 2026-06-18  
Package: `2026-06-18_FINAL_DEMO_PACKAGE`

## Results

| Check | Result |
|---|---|
| Required package files | PASS |
| Bundled SQL hidden `SOURCE` dependencies | PASS - none found |
| SHA-256 integrity validation | PASS |
| Workspace prerequisite runner | PASS |
| MySQL/MariaDB connectivity | PASS |
| Registrar package runner | PASS |
| Enrollment package runner | PASS |
| Read-only database smoke runner | PASS |
| Scholarship test seed | PASS |
| Scholarship cleanup | PASS |
| Scholarship cleanup followed by reseed | PASS |
| Registrar functional tests | 42 passed, 1 skipped |
| Registrar full test command | Known non-zero result from one Modulith package-cycle architecture error |

Database smoke observed:

- active term id 1, code `1120242025`
- enlistment status contract present
- active programs and curricula present
- active-term sections and schedules present
- exact active-term fee rows present
- `prof.cruz` assignments present
- Sofia Scholar and Liam Low Units test candidates present after reseed

## Intentionally not executed

The destructive `RUN_FRESH_DATABASE.cmd` was not executed against the shared working database during package validation. Its 17 SQL inputs were copied from the current canonical sources, checked for presence and checksum integrity, and checked to contain no nested external `SOURCE` dependencies.

Execute the destructive setup only on a disposable fresh database.
