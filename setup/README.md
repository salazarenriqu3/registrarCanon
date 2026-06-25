# Fresh Setup - Single Folder

Last updated: 2026-06-25

For humans: `../handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`
For agents: `AGENT_FRESH_SETUP.md`
Seed inventory: `BOOTSTRAP_SEED_MANIFEST.md`

---

## Quick start

From project root:

```cmd
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
```

One-command bootstrap:

```cmd
setup\RUN_FRESH_SETUP.cmd
```

Skip the prereq check only if you already verified it:

```cmd
setup\RUN_FRESH_SETUP.cmd --skip-prereq
```

Active term after bootstrap: `1120242025` (`term_id = 1`)

For the full Registrar feature presentation dataset after bootstrap:

```cmd
setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
setup\START_REGISTRAR_DEMO.cmd
```

---

## Folder contents

| Path | Purpose |
|------|---------|
| `AGENT_FRESH_SETUP.md` | Agent and human playbook for setup and smoke checks |
| `BOOTSTRAP_SEED_MANIFEST.md` | Ordered inventory of everything the bootstrap loads |
| `CHECK_PREREQUISITES.cmd` | JDK, Maven, MariaDB, and project layout gate |
| `RUN_FRESH_SETUP.cmd` | One-shot bootstrap using the bundled SQL package |
| `LOAD_FULL_REGISTRAR_DEMO_DATA.cmd` | Loads the curated registrar feature demo dataset |
| `START_REGISTRAR_DEMO.cmd` | Starts Registrar with admission-upload assets preconfigured |
| `sql/01` ... `06` | Term activation, fees, faculty mix, schedule cleanup, verification |
| `fees/` | CSV templates for Program Fees UI |

The bootstrap uses the self-contained SQL package tracked in this repository. Admission and Enrollment source checkouts are not required for the Registrar-only flow.

---

## What bootstrap seeds

- Full schema plus finance gates and installment-plan support
- Curriculum for active programs and calendar terms through 2728
- Block sections, block offerings, and `IRREG-A` sections on every seeded calendar term
- Schedules, faculty accounts, and grading windows
- Post-seed schedule cleanup so inactive terms do not keep stale faculty assignments
- Fees across every seeded calendar term
- Active term `1120242025` plus curated demo faculty assignments, including grading-ready `prof.cruz`

Details: `BOOTSTRAP_SEED_MANIFEST.md`

The separate full-demo loader overlays:

- roomed schedules for the BSIT/BSCPE blocks and `IRREG-A` sections used in the live demo
- named students for add-class, transfer credit, shift, withdrawal, scholarship, and overpayment flows
- Maria's admission-linked document files for inline viewing from Student Profile
- registration-form and document-trail starter history for the presentation path

---

## After bootstrap

| Check | URL |
|-------|-----|
| Settings | http://localhost:8083/registrar/admin/settings |
| Term fees | http://localhost:8083/registrar/admin/term-fees?termId=1 |
| Class scheduling | http://localhost:8083/registrar/admin/class-scheduling?termId=1 |

| Login | Password |
|-------|----------|
| `admin` | `1234` |
| `prof.cruz` | `1234` |

Human UAT: `handoffNew/HUMAN_UAT_CHECKLIST.md`
Feature demo script: `handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`

---

## Related docs

| Doc | Use |
|-----|-----|
| `handoffNew/START_HERE_NEW_PC_HANDOFF.md` | Human new-PC overview |
| `handoffNew/HUMAN_UAT_CHECKLIST.md` | Demo sign-off checklist |
| `handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md` | Detailed registrar feature presentation order |
| `handoffNew/THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` | Older lifecycle-track walkthrough |
