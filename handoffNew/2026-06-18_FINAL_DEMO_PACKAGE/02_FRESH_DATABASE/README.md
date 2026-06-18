# Fresh Database Setup

`RUN_FRESH_DATABASE.cmd` is the package-local replacement for the scattered legacy bootstrap.

It uses only SQL stored under this folder and does not depend on SQL elsewhere in the workspace.

## Run

From the workspace root:

```powershell
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\CHECK_PREREQUISITES.cmd
registrar\handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd
```

The setup asks for confirmation before dropping `eacdb`. For an automated disposable environment, append `--yes`.

Optional environment variables:

```powershell
$env:EAC_DB_HOST = "127.0.0.1"
$env:EAC_DB_PORT = "3306"
$env:EAC_DB_USER = "root"
$env:EAC_DB_PASSWORD = ""
```

## SQL phases

| Phase | Content |
|---|---|
| `01_SCHEMA` | Canonical base schema and seed |
| `02_CONTRACTS` | Enlistment lifecycle and exact fee schema contracts |
| `03_ACADEMIC_MASTER` | Full curricula, terms, retirements, sections, schedules, and faculty |
| `04_TERM_AND_FEES` | Active term and exact fee materialization |
| `05_VERIFICATION` | Read-only post-bootstrap readiness report |

The final SQL step is read-only. Expected active term is `1120242025` and active-term fee gaps should be zero for the demo dataset.
