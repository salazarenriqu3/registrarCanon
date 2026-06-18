# Project Status & Roadmap

> **Final handover notice (2026-06-18):** Use `START_HERE_NEW_PC_HANDOFF.md` and the three `FINAL_*_20260618.md` documents as the current authority. This roadmap remains useful history, but the final pack takes precedence for scope, demo instructions, test evidence, and readiness.

Last updated: **2026-06-18**
**Canonical project root:** `C:\newer`

This is the **single status page** for what was built, sidetracks, UAT progress, and what is still pending. Read this before starting new work.

---

## Executive summary

| Area | Status |
|------|--------|
| **Demo / pilot code** | Stabilization **complete** — ready for continued UAT |
| **Fresh setup** | `registrar/setup/RUN_FRESH_SETUP.cmd` — one-command bootstrap |
| **Active demo term** | **`1120242025`** (2425 **1st sem**, `term_id = 1`) |
| **Human UAT** | **In progress** — user re-tested; satisfied with core flows; Sessions C–F not fully signed off |
| **Production** | **Not started** — security deferred; ops/fees TBD |

---

## Major roadmap (phases)

```text
[DONE] Phase 0 — Stabilization & demo readiness
         ↓
[NOW]  Phase UAT — Human sign-off (HUMAN_UAT_CHECKLIST.md)
         ↓
[LATER] Phase 1 — Production hardening (security, demo behaviors)
         ↓
[LATER] Phase 2 — Business data (official fees, finance gates)
         ↓
[LATER] Phase 3 — Ops (CI/CD, secrets, backup, HTTPS)
         ↓
[FUTURE] Phase 4 — Deferred features (TOR OCR, equivalency, scheduling automation)
```

Detail: **`PRODUCTION_IMPLEMENTATION_PLAN.md`**

---

## Phase 0 — Stabilization (DONE)

### Core cross-app bridging

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Unified **Finance Policy** (Registrar) | Done | Admission, downpayment, installments, drop penalties |
| Unified **Program Fees** (Registrar) | Done | 2-zone UI; Enrollment redirects here |
| Per-student **installment override** | Done | Student Manager + cashier resolution |
| **Block offerings** + class scheduling | Done | Block Sections panel; IRREG-A for irregular |
| Irregular vs block enlist enforcement | Done | Enrollment rejects block codes on irregular students |
| Enrollment admin redirects | Done | Term fees, finance policy → Registrar |
| Fee readiness rule fix (`hasPrimaryRate`) | Done | TUITION / LEC / RLE accepted |
| Six empty programs soft-retired | Done | BSBA, BSCE, BSCS, BSECE, BSED, BSMATH |

### Fresh setup pack (`registrar/setup/`)

| File | Purpose |
|------|---------|
| `RUN_FRESH_SETUP.cmd` | Canonical bootstrap (~17 steps) |
| `CHECK_PREREQUISITES.cmd` | Machine gate |
| `AGENT_FRESH_SETUP.md` | Agent playbook |
| `BOOTSTRAP_SEED_MANIFEST.md` | Seed inventory |
| `sql/01`–`05` | Term, fees (all calendar terms), prof.cruz, verify |
| `fees/*.csv` | Fee import templates |

Legacy `registrar/db/run_full_uat_bootstrap.cmd` **forwards** to `setup/`.

### Active term change (sidetrack — intentional)

| Before | After |
|--------|-------|
| `2120242025` (2nd sem, term_id 2) | **`1120242025`** (1st sem, term_id 1) |
| Y1 block `BSCPE-1-2-A` | Y1 block **`BSCPE-1-1-A`** |
| Term fees UI `?termId=2` | Term fees UI **`?termId=1`** |

SQL: `registrar/setup/sql/01_activate_term_2425_s1.sql`

### Documentation pack

| Doc | Role |
|-----|------|
| **`PROJECT_STATUS_AND_ROADMAP.md`** | This file — status + roadmap |
| **`COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md`** | All-in-one setup + demo |
| **`HUMAN_UAT_CHECKLIST.md`** | Sessions 0–F sign-off |
| **`DOC_TO_CODE_STARTER_MAP.md`** | Interview/spec-to-current-state starter map |
| **`IMPLEMENTATION_PLAN_REGISTRAR_ACADEMIC_REFINEMENT_20260617.md`** | Registrar academic refinement plan from interview/spec inputs |
| **`THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`** | DREG / TTRNS / TSHFT |
| **`MASTER_DEMO_UAT_MANUAL.md`** | Full reference |
| **`HANDOFF_UPDATES_20260609.md`** | Changelog by date |
| **`061026/`** | Portable copy of setup + demo manuals (2026-06-10 bundle) |

### Automated validation (June 2026)

| Check | Result |
|-------|--------|
| Full bootstrap | PASS |
| Fee readiness (active term) | 0 unresolved |
| Settings readiness | Ready for operation |
| Quick preflight (Python) | 7/7 PASS (after script fixes) |

Evidence: `_runtime_logs/*_20260608.json`, `READINESS_CLOSURE_20260608.md`

---

## Sidetracks completed

Work that was not in the original stabilization scope but was delivered:

| Sidetrack | Outcome | Doc |
|-----------|---------|-----|
| **Admission vs downpayment pooling** | Clarified as **intentional** for Y1; **no code change** | Conversation / business rule |
| **Three-track lifecycle manual** | DREG / TTRNS / TSHFT + prof.cruz grading | `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` |
| **Agent fresh-setup pack** | Prereq script + manifest + playbook | `registrar/setup/` |
| **Fees on all calendar terms** | 2425–2728 pre-seeded for term transition | `sql/05_materialize_all_calendar_term_fees.sql` |
| **061026 manual bundle** | Offline copy for hard testing | `061026/README.md` |
| **Registrar Spring Security proposal** | Full design written; **implementation deferred** | `PROPOSAL_REGISTRAR_SPRING_SECURITY.md` |
| **Registrar Dean / Faculty irregular advising bridge** | Retired to dormant scope; no longer active acceptance target | `ADMISSION_HANDOFF_IRREGULAR_PRE_REG_REGISTRAR_20260614.md` |
| **UI contrast pass** | Alerts, cards, page background — both apps | `HANDOFF_UPDATES` §13 |

---

## Phase UAT — Human testing (IN PROGRESS)

**Primary checklist:** `HUMAN_UAT_CHECKLIST.md`

| Session | Focus | Status |
|---------|--------|--------|
| **0** | Fresh setup + smoke | Re-tested (user satisfied) |
| **A** | Registrar admin | Largely exercised |
| **B** | Enrollment / cashier | Largely exercised |
| **C** | Balance / term close | **Pending** full sign-off |
| **D** | Faculty / grading (prof.cruz) | **Pending** full sign-off |
| **E** | Transferee / program shift | **Pending** full sign-off |
| **F** | Term transition | Optional — **pending** |

**User note (2026-06-10):** Re-ran tests; glad with results on completed flows. Remaining sessions + any new inputs before production.

**Execution note (2026-06-18):** Registrar functional regression is clean (42 passed, 1 skipped). The full Maven command still reports the pre-existing Modulith package-cycle violation as one structural error. Live readiness for `1120242025` is green. Enrollment was started and admin login passed, but Session C remains pending because the available withdrawal UAT record points to `SL_1120262026` while Registrar is open on `1120242025`, has no current assessment, and Enrollment logs an `Unknown column 'RESERVED'` schema warning. Session D is waiting for a working faculty test login, and Session E still needs transactional UAT data/sign-off. The dashboard active-term subtitle was realigned to the canonical active-term model instead of a hardcoded term.

**Long-form demos (optional):**

- `THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md` — 2+ hr
- `MASTER_DEMO_UAT_MANUAL.md` Parts 8–10

---

## Production roadmap — PENDING / DEFERRED

### Deferred by decision (not blocking demo)

| Item | Priority | Status | Doc |
|------|----------|--------|-----|
| **Registrar Spring Security** | High (prod) | **Deferred** — proposal only | `PROPOSAL_REGISTRAR_SPRING_SECURITY.md` |
| **Overpay: pending carry → refund or credit** | Medium (finance) | Registrar **done**; Enrollment **planned** | `61026.2/overpayment/`; HANDOFF §18 |
| **Explicit curriculum assign (returning students)** | Medium (academic) | **Planned** — both apps phased | `61026.2/curriculum/`; HANDOFF §19 |
| Demo password reset on boot | Medium | Not done | `RegistrarApplication.java` |
| Role matrix hardening | Medium | Blocked on 1A | `PRODUCTION_IMPLEMENTATION_PLAN.md` |
| Official production fee rates | High (business) | Pending registrar input | Program Fees UI |
| CI/CD pipeline | Medium | Not started | TBD |
| DB secrets / non-root user | High (prod) | Not started | TBD |
| Backup / restore runbook | High (prod) | Not started | TBD |
| HTTPS / reverse proxy | High (prod) | Not started | TBD |
| MCP server lockdown | Medium | Enabled in dev | `application.properties` |
| Audit logging | Medium | Not started | TBD |

### Out of scope (unchanged)

- TOR PDF / OCR
- Course equivalency table
- Scheduling automation / room conflicts
- Curriculum CSV **import** (export only)
- Six retired programs (unless official curriculum supplied)

---

## Decision log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-09 | Active term = **1120242025** (1st sem) | Golden-path Y1 block demo |
| 2026-06-09 | Keep admission/downpayment pooling | Business intent for Y1 |
| 2026-06-10 | **Skip Registrar Spring Security for now** | Too long for current sprint; accept pilot risk |
| 2026-06-10 | Proceed with more user inputs after doc sync | UAT largely positive |

---

## Document index (what to read when)

| Need | Read |
|------|------|
| **Status / roadmap** | **This file** |
| Fresh machine setup | `COMPLETE_FRESH_SETUP_AND_DEMO_GUIDE.md` → `registrar/setup/AGENT_FRESH_SETUP.md` |
| Hard test sign-off | `HUMAN_UAT_CHECKLIST.md` |
| Production plan | `PRODUCTION_IMPLEMENTATION_PLAN.md` |
| Security design (future) | `PROPOSAL_REGISTRAR_SPRING_SECURITY.md` |
| Code/doc changelog | `HANDOFF_UPDATES_20260609.md` |
| Deep UAT reference | `MASTER_DEMO_UAT_MANUAL.md` |
| Portable manual bundle | `061026/` |

---

## For the next agent

1. Read **this file** first.
2. Bootstrap: `registrar/setup/RUN_FRESH_SETUP.cmd` from project root.
3. Active term must be **`1120242025`** (`term_id=1`).
4. Do **not** resurrect pre-retirement “248 fee scopes” or term-2-as-default language.
5. Append new changes to `HANDOFF_UPDATES_YYYYMMDD.md` or §13+ of `HANDOFF_UPDATES_20260609.md`.
6. Dean / Faculty irregular new-enrollee advising in Registrar is dormant; do not treat old pre-reg bridge notes as current canon.
7. User has **more inputs coming** — treat as refinements on top of current stable demo build.

---

## Quick config reference

| Item | Value |
|------|--------|
| Registrar | http://localhost:8083/registrar |
| Enrollment | http://localhost:8082 |
| DB | `eacdb` @ `127.0.0.1:3306`, root, empty password |
| Active term | `1120242025` |
| Logins | `admin` / `1234`, `prof.cruz` / `1234` |
