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
| [`CANON/README.md`](CANON/README.md) | Canonical D-drive landing page for the current registrar canon |
| [`AGENTS.md`](AGENTS.md) | Compact registrar workflow and canon for future agent passes |
| [`handoffNew/REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`](handoffNew/REGISTRAR_SYSTEM_SPECIFICATION_20260625.md) | Current front-door system specification: scope, ownership, workflows, boundaries, and module map |
| [`handoffNew/REPO_PUSH_GUIDE_REGISTRARCANON_20260625.md`](handoffNew/REPO_PUSH_GUIDE_REGISTRARCANON_20260625.md) | Agent-facing guide for publishing this updated Registrar state to the `registrarCanon` GitHub repo |
| [`handoffNew/REPO_PR_AND_COMMIT_TEMPLATE_REGISTRARCANON_20260625.md`](handoffNew/REPO_PR_AND_COMMIT_TEMPLATE_REGISTRARCANON_20260625.md) | Ready-to-use commit titles, push command, PR title, and PR description for publishing this branch |
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
