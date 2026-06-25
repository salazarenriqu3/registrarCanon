# EAC Registrar Final Demo Package

Package date: 2026-06-18  
Expected location: `registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE`

This is the canonical, sorted package for setting up, building, demonstrating, testing, and handing over the Registrar-centered build.

## Folder map

| Folder | Purpose |
|---|---|
| `01_DOCUMENTATION` | System specification, final system documentation, demo/UAT manual, and handover |
| `02_FRESH_DATABASE` | Self-contained destructive fresh database setup and all required SQL |
| `03_TEST_DATA` | Current test-only seeds, read-only smoke queries, and cleanup |
| `04_RUNNERS` | Package validation, build, tests, app startup, and preflight commands |
| `05_MANIFEST` | Source mapping and SHA-256 integrity manifest |
| `06_EVIDENCE` | Blank execution/sign-off record for the demo machine |

## First terminal

Open PowerShell at the cloned Registrar repository root.

```powershell
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\00_VERIFY_PACKAGE.cmd
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\01_CHECK_MACHINE.cmd
```

For a disposable machine/database only:

```powershell
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd
```

Focused Registrar withdrawal data can then be loaded with:

```cmd
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\11_LOAD_WITHDRAWAL_UAT_DATA.cmd
```

The detailed checklist is `handoffNew\2026-06-22_WITHDRAWAL_UAT_CHECKLIST.md`.

Warning: the fresh database command drops and recreates `eacdb`.

Then run:

```powershell
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\02_BUILD_ALL.cmd
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\03_RUN_REGISTRAR_TESTS.cmd
```

Start Registrar:

```powershell
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\04_START_REGISTRAR.cmd
```

Load the current scholarship demonstration records when needed:

```powershell
handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\04_RUNNERS\07_LOAD_SCHOLARSHIP_TEST_DATA.cmd
```

Read in this order before presenting:

1. `01_DOCUMENTATION\REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
2. `01_DOCUMENTATION\FINAL_SYSTEM_DOCUMENTATION_20260618.md`
3. `01_DOCUMENTATION\REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`
4. `01_DOCUMENTATION\FINAL_DEMO_AND_TEST_MANUAL_20260618.md`

## Canonical boundaries

- Active demo term: `1120242025`, term id 1.
- Registrar irregular new-enrollee advising/pre-registration is retired and excluded.
- Admission and Enrollment are external systems and their source code is not bundled here.
- Demo fee values are not official production values.
- This package supports controlled demo/UAT, not production deployment.
