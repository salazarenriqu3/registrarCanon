# Registrar Canon Workflow

This file is the short operating canon for future Codex passes on the registrar repo.

## Purpose

Keep the registrar project aligned, demo-safe, and easy to continue without re-learning the whole history each time.

## Read First

1. `README.md`
2. `handoffNew/START_HERE_NEW_PC_HANDOFF.md`
3. `handoffNew/FINAL_SYSTEM_DOCUMENTATION_20260618.md`
4. `handoffNew/FINAL_DEMO_AND_TEST_MANUAL_20260618.md`
5. `handoffNew/FINAL_HANDOVER_20260618.md`
6. `docs/business_logic/BUSINESS_LOGIC_MASTER.md`
7. `docs/SETUP_AND_DEMO_MANUAL.md`

## Non-Negotiables

- Treat this as registrar-first scope.
- Do not revive retired admission, enrollment, or dean bridge logic unless the user explicitly reopens that scope.
- Compare the current repo state against any newer copy before editing.
- Preserve existing working behavior unless the change is clearly requested and verified.
- If a UI change affects a flow, verify it in the browser.
- If a SQL change affects setup or demo data, provide fresh setup and seed steps.
- If a business rule changes, add or update tests.
- If a change affects the handoff, update the handoff docs in the same pass.

## Working Order

1. Orient
2. Compare
3. Implement
4. Test
5. Document
6. Package
7. Push

## How To Compare

- Prefer the newest usable copy when multiple project snapshots exist.
- Keep the useful parts, but do not assume newer means correct.
- Merge only after checking whether the behavior already exists in the active registrar repo.

## Delivery Standard

Every meaningful change should leave behind:

- The code change
- Any required SQL or seed updates
- Test coverage or test instructions
- Demo instructions if user-facing
- Handover notes if the workflow changed

## Done Means

A change is not really done until it passes the relevant build or test path, works in the browser when applicable, and is documented well enough for the next agent to continue.

