# Fresh Setup — Single Folder

Last updated: 2026-06-09

**For humans:** **`../handoffNew/COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`** — everything in one doc.  
**For agents:** **`AGENT_FRESH_SETUP.md`** (prerequisites + bootstrap + smoke).  
**Seed inventory:** **`BOOTSTRAP_SEED_MANIFEST.md`**

---

## Quick start

From **project root**:

```cmd
setup\CHECK_PREREQUISITES.cmd
setup\RUN_FRESH_SETUP.cmd
```

One command bootstrap (includes prereq gate):

```cmd
setup\RUN_FRESH_SETUP.cmd
```

Skip prereq check only if you already verified:

```cmd
setup\RUN_FRESH_SETUP.cmd --skip-prereq
```

**Active term after run:** `1120242025` (A.Y. 2024–25, **1st Semester**, `term_id = 1`)

---

## Folder contents

| Path | Purpose |
|------|---------|
| **`AGENT_FRESH_SETUP.md`** | **Agent/human playbook** — prereqs, bootstrap, smoke |
| **`BOOTSTRAP_SEED_MANIFEST.md`** | Complete list of what bootstrap seeds |
| **`CHECK_PREREQUISITES.cmd`** | JDK, Maven, MariaDB, project layout |
| **`RUN_FRESH_SETUP.cmd`** | One-shot bootstrap (17 SQL steps) |
| `sql/01` … `05` | Term activation, fees (all calendar terms), prof.cruz, verify |
| `fees/` | CSV templates for Program Fees UI |

The bootstrap uses the self-contained SQL package tracked in this repository. Admission and Enrollment source checkouts are not required.

---

## What bootstrap seeds (summary)

- Full schema + finance gates + installment plan  
- Curriculum (21 active programs) + calendar terms through 2728  
- **Block sections + block offerings + IRREG-A** on **every calendar term**  
- **Schedules + faculty + grading windows**  
- **Fees on every calendar term** (not only active)  
- Active term **1120242025** + prof.cruz on all active sections  

Details: **`BOOTSTRAP_SEED_MANIFEST.md`**

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

---

## Related docs

| Doc | Use |
|-----|-----|
| `handoffNew/START_HERE_NEW_PC_HANDOFF.md` | Human new-PC overview |
| `handoffNew/HUMAN_UAT_CHECKLIST.md` | Demo sign-off |
| `handoffNew/THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` | Lifecycle tracks |
