# Repo Commit and PR Template for `registrarCanon`

Date: 2026-06-25
Repo: `https://github.com/salazarenriqu3/registrarCanon`
Remote: `registrarCanon`
Base branch: `canon-main`
Head branch: `codex/registrar-withdrawal-governance`

## 1. Purpose

This file gives another agent a ready-to-use publish template for the current Registrar system state.

It includes:

- exact commit title options
- recommended push command
- recommended PR title
- a ready PR body
- a short reviewer checklist

## 2. Current GitHub state

At the time this file was written:

- the target repo default branch is `canon-main`
- no open PR was found for `codex/registrar-withdrawal-governance`

That means another agent should normally:

1. push the branch
2. open a new PR from `codex/registrar-withdrawal-governance` into `canon-main`

## 3. Recommended single-commit title

If publishing as one commit, use:

```text
Publish registrar scheduling hardening and demo dataset refresh
```

## 4. Recommended two-commit split

If another agent wants a cleaner split:

Commit 1:

```text
Finalize registrar scheduling hardening and dataset cleanup
```

Commit 2:

```text
Add full registrar demo dataset and updated handoff specification
```

## 5. Recommended push command

From `D:\registrarCanon_canon`:

```cmd
git push registrarCanon codex/registrar-withdrawal-governance
```

## 6. Recommended PR title

```text
Harden scheduling and publish full registrar demo dataset
```

## 7. Ready PR body

Use this as the PR description:

```md
## Summary

This PR publishes the current updated Registrar system state from the working branch.

It includes:

- scheduling hardening for room conflicts, faculty conflicts, same-section overlaps, and faculty max-load enforcement
- faculty-load integrity auditing plus suspicious-assignment repair support
- active-term demo faculty alignment and demo login/data fixes
- schedule dataset cleanup and stronger readiness verification
- a full registrar feature demo dataset with seeded rooms, profile/demo students, withdrawal/add-class/shift/transfer coverage, and applicant document demo assets
- updated handoff, setup, and system-specification documentation
- synchronized dated-package copies and refreshed package checksums

## Included feature areas

### Scheduling hardening

- blocks same-term room overlaps
- blocks same-term faculty overlaps
- blocks same-section self-overlaps
- enforces faculty max load before assignment
- exposes conflict preview and faculty-load audit surfaces

### Demo data and reset flow

- adds a repeatable registrar demo loader
- removes TBA from the named demo blocks/open sections used in the presentation flow
- seeds Maria `2026-1001` with admission-linked document viewing and print/history data
- seeds dedicated students for add-class, transfer credit, program shift, scholarship, withdrawal, and overpayment demos

### Documentation

- adds a new front-door Registrar system specification
- updates setup and handoff entry points to start from the latest canon
- adds a repo-publish guide and commit/PR template for future agents

## Validation performed

```cmd
cmd /c setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
mvn -q -DskipTests compile
mvn -q "-Dtest=FacultyLoadServiceTest,BlockOfferingServiceTest,ScheduleConflictValidatorTest" test
```

Expected/observed key outcomes:

- full registrar demo dataset verifier passes
- curated demo blocks and selected `IRREG-A` sections have no roomless rows
- focused scheduling/faculty-load tests pass

## Important notes

- this PR targets `registrarCanon/canon-main` from `codex/registrar-withdrawal-governance`
- the correct remote for this branch is `registrarCanon`, not `origin`
- this publish intentionally includes the dated handoff/demo package mirror so the repo remains internally consistent
- the baseline bootstrap may still leave non-demo sections as TBA; the full demo loader assigns concrete rooms only to the curated presentation dataset

## Reviewer checklist

- verify scheduling conflict enforcement logic and tests
- verify faculty-load audit/repair additions
- verify demo SQL and package mirror files are included together
- verify `handoffNew/REGISTRAR_SYSTEM_SPECIFICATION_20260625.md` is present
- verify `setup/LOAD_FULL_REGISTRAR_DEMO_DATA.cmd` and package runner equivalents are present
```

## 8. Optional shorter PR title

If a shorter title is preferred:

```text
Publish registrar demo refresh and scheduling hardening
```

## 9. Suggested reviewer focus

Ask the reviewer to concentrate on:

- scheduling integrity
- faculty load enforcement
- demo SQL correctness
- package/source documentation consistency

## 10. Final caution

If another agent opens the PR after additional local edits, they should update the validation section and summary before submitting.
