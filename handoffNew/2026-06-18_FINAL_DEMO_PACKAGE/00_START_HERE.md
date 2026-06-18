# EAC Registrar Final Demo Package

Package date: 2026-06-18  
Expected location: `registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE`

This is the canonical, sorted package for setting up, building, demonstrating, testing, and handing over the Registrar-centered build.

## Folder map

| Folder | Purpose |
|---|---|
| `01_DOCUMENTATION` | Final system documentation, demo/UAT manual, and handover |
| `02_FRESH_DATABASE` | Self-contained destructive fresh database setup and all required SQL |
| `03_TEST_DATA` | Current test-only seeds, read-only smoke queries, and cleanup |
| `04_RUNNERS` | Package validation, build, tests, app startup, and preflight commands |
| `05_MANIFEST` | Source mapping and SHA-256 integrity manifest |
| `06_EVIDENCE` | Blank execution/sign-off record for the demo machine |

## First terminal

Open PowerShell at the workspace root containing `registrar` and `enrollment3`.

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\00_VERIFY_PACKAGE.cmd
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\01_CHECK_MACHINE.cmd
```

For a disposable machine/database only:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd
```

Warning: the fresh database command drops and recreates `eacdb`.

Then run:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\02_BUILD_ALL.cmd
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\03_RUN_REGISTRAR_TESTS.cmd
```

Start each application in its own terminal:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\04_START_REGISTRAR.cmd
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\05_START_ENROLLMENT.cmd
```

Load the current scholarship demonstration records when needed:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\07_LOAD_SCHOLARSHIP_TEST_DATA.cmd
```

Read `01_DOCUMENTATION\FINAL_DEMO_AND_TEST_MANUAL_20260618.md` before presenting.

## Canonical boundaries

- Active demo term: `1120242025`, term id 1.
- Registrar irregular new-enrollee advising/pre-registration is retired and excluded.
- Demo fee values are not official production values.
- This package supports controlled demo/UAT, not production deployment.
