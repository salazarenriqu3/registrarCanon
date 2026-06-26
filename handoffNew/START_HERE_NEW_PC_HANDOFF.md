# Start Here - Registrar Handover

Last updated: 2026-06-19

**Canonical packaged handover:** `2026-06-18_FINAL_DEMO_PACKAGE\00_START_HERE.md`

**Current front-door specification:** `REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`

**Cross-system boundary correction:** `CROSS_SYSTEM_ALIGNMENT_REANALYSIS_20260626.md`

**Cross-system implementation plan:** `IMPLEMENTATION_PLAN_CROSS_SYSTEM_REALIGNMENT_20260626.md`

The dated package contains sorted documentation, a self-contained fresh database chain, current test SQL, runners, checksums, and execution evidence. Use it for a new terminal/demo setup.

Use this documentation order:

1. `REGISTRAR_SYSTEM_SPECIFICATION_20260625.md` - current front-door specification for scope, ownership, workflows, boundaries, and module map.
2. `CROSS_SYSTEM_ALIGNMENT_REANALYSIS_20260626.md` - corrected ownership model for Admission, Enrollment3, and Registrar, especially irregular workflow and fees.
3. `IMPLEMENTATION_PLAN_CROSS_SYSTEM_REALIGNMENT_20260626.md` - phased plan to make code and docs match the corrected ownership model.
4. `FINAL_SYSTEM_DOCUMENTATION_20260618.md` - authoritative release-state summary, architecture, capabilities, and readiness.
5. `FINAL_DEMO_AND_TEST_MANUAL_20260618.md` - fresh setup, build gate, presentation flow, complete UAT, and sign-off form.
6. `FINAL_HANDOVER_20260618.md` - build evidence, open risks, production blockers, and successor instructions.

## Current verdict

- Controlled Registrar demo: ready after the documented preflight.
- Production deployment: not approved.
- Active demo term: `1120242025` (`term_id = 1`).
- Retired Registrar irregular new-enrollee advising/pre-registration: out of scope.
- Academic builder canon: Program Builder -> Course Catalog -> Curriculum Builder -> Class Scheduling -> Slot Monitoring.
- Program shift cleanup is separate from school withdrawal: it can clear every current-term enrolled/staged subject without deactivating the student.
- Academic scholarship now uses official SQL-seeded grades, assigned curriculum unit load, configurable GWA/period caps, and 3rd/4th-year PE/NSTP disqualification; Student Profile includes archive custody tracking for physical record movements.

## Quick start

From `C:\newer\new` on a disposable demo database:

```powershell
registrar\setup\CHECK_PREREQUISITES.cmd
registrar\setup\RUN_FRESH_SETUP.cmd
```

Warning: fresh setup drops and recreates `eacdb`.

Start Registrar from `C:\newer\new\registrar` with `mvn spring-boot:run` and Enrollment from `C:\newer\new\enrollment3` with `.\mvnw.cmd spring-boot:run`.

Older files in this folder are detailed supporting history. The specification plus the three final documents above take precedence whenever wording or status conflicts.
