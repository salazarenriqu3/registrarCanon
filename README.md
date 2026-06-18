# IUIMS Registrar System

Spring Boot registrar module for the IUIMS/CAPSS stack covering admissions handoff visibility, student profiles, curriculum, class scheduling, grading, finance, scholarships, and registrar administration.

## Quick start

Prerequisites: JDK 17, Maven, MariaDB/MySQL

From the project root:

```cmd
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
mvn spring-boot:run
```

Default URL: `http://localhost:8083/registrar`  
Demo login: `admin` / `1234`

`RUN_FRESH_SETUP.cmd` drops and recreates `eacdb` after an explicit `RECREATE` confirmation. It uses the repository's self-contained SQL bundle; no Admission or Enrollment source checkout is required.

## Repository layout

| Path | Purpose |
|------|---------|
| `src/` | Application code, templates, and runtime configuration |
| `db/` | SQL schema, seeds, demo scripts, manual verification, migrations |
| `setup/` | Fresh database bootstrap and setup helpers |
| `docs/` | Architecture and business-logic documentation |
| `handoffNew/` | Current handoff, demo, UAT, and delivery documentation |
| `scripts/` | Helper scripts |

## Canonical docs

| Start here | Description |
|------------|-------------|
| [`handoffNew/START_HERE_NEW_PC_HANDOFF.md`](handoffNew/START_HERE_NEW_PC_HANDOFF.md) | Current handoff entry point |
| [`handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`](handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md) | Full setup and demo guide |
| [`handoffNew/FINAL_SYSTEM_DOCUMENTATION_20260618.md`](handoffNew/FINAL_SYSTEM_DOCUMENTATION_20260618.md) | Current system state and scope |
| [`handoffNew/FINAL_DEMO_AND_TEST_MANUAL_20260618.md`](handoffNew/FINAL_DEMO_AND_TEST_MANUAL_20260618.md) | Demo flow and acceptance testing |
| [`handoffNew/FINAL_HANDOVER_20260618.md`](handoffNew/FINAL_HANDOVER_20260618.md) | Release, risks, and successor notes |
| [`setup/README.md`](setup/README.md) | Bootstrap commands and folder map |

## Build and test

```cmd
mvn test
mvn test -Dtest=!ModulithTests
mvn spring-boot:run
```

`ModulithTests` is currently treated as non-blocking structural audit coverage; targeted functional suites should still pass cleanly.

## Notes

- Active demo term: `1120242025`
- Retired dean irregular advising bridge is out of current canon
- Program Builder is the core registrar-side master for programs; curriculum stays separate
