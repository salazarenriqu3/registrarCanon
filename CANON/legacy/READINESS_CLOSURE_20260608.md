# Readiness Closure Notes - 2026-06-08

> **Closure event log (2026-06-08).** Post-closure UX/bootstrap changes (term fees 2-zone UI, finance policy, `11_bootstrap_materialize`) are in **`HANDOFF_UPDATES_20260609.md`**.

Workspace:

`C:\Users\sune\Downloads\new-20260606T044759Z-3-001\new`

## What Was Closed

### Fee readiness

The remaining active-term fee readiness problem was caused by an internal rule mismatch:

- fee readiness/copy logic treated `TUITION_PER_UNIT` or `RLE_FEE_PER_UNIT` as a valid primary rate
- the CSV importer documentation also allowed `TUITION_PER_UNIT` or `RLE_FEE_PER_UNIT`
- but the shared `hasPrimaryRate(...)` helper only accepted `TUITION_PER_UNIT` or `LEC_FEE_PER_UNIT`

Patch applied:

- `registrar/src/main/java/com/iuims/registrar/finance/TermFeeAdminService.java`
- `hasPrimaryRate(...)` now accepts:
  - `TUITION_PER_UNIT`
  - `LEC_FEE_PER_UNIT`
  - `RLE_FEE_PER_UNIT`

Compile:

- `mvn -q -DskipTests compile` passed.

Runtime import:

- Used existing file:
  - `registrar/handoffNew/fee_import/term-fee-import-template-term-1-unresolved.csv`
- Imported through:
  - `POST /admin/term-fees/import-template`
- Result:

```json
{"RowsChecked":248,"RowsImported":248,"RowsCreated":0,"RowsUpdated":248,"RowsSkipped":0,"FeeValuesApplied":4960}
```

Verified on `/admin/term-fees?termId=1`:

```text
0 missing fee scope(s), 0 fallback fee scope(s), 0 incomplete primary-rate scope(s), 6 program(s) without active curriculum.
```

Fee readiness is now clean for active term `1`.

## Remaining Readiness Blocker

Only curriculum readiness remains blocked.

The 6 programs still without operational active curricula are:

- `BSBA`
- `BSCE`
- `BSCS`
- `BSECE`
- `BSED`
- `BSMATH`

Important:

- The user already confirmed source curriculum files for these are not currently available.
- Do not invent curriculum content.
- Empty placeholders are intentionally non-operational and should not satisfy term readiness.

## Placeholder/Draft Work Queue

All six programs already have editable empty placeholder/draft curricula. No new shells need to be created.

| Program | Curriculum ID | Status |
| --- | ---: | --- |
| `BSBA` | `112` | editable, picker ready, empty |
| `BSCE` | `114` | editable, picker ready, empty |
| `BSCS` | `117` | editable, picker ready, empty |
| `BSECE` | `118` | editable, picker ready, empty |
| `BSED` | `119` | editable, picker ready, empty |
| `BSMATH` | `121` | editable, picker ready, empty |

Runtime verification:

```json
[
  {"Program":"BSBA","CurriculumId":112,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true},
  {"Program":"BSCE","CurriculumId":114,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true},
  {"Program":"BSCS","CurriculumId":117,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true},
  {"Program":"BSECE","CurriculumId":118,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true},
  {"Program":"BSED","CurriculumId":119,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true},
  {"Program":"BSMATH","CurriculumId":121,"PageRendered":true,"Editable":true,"PickerReady":true,"Empty":true}
]
```

## How to Complete Each Curriculum

For each program:

1. Open the placeholder:
   - `/registrar/admin/curriculum/view/{curriculumId}`
2. Add existing catalog courses using the picker.
3. Set year level and semester placement.
4. Add prerequisites if available.
5. Repeat until the official curriculum structure is complete.
6. Finalize only after the curriculum is official and complete.

Do not finalize empty or guessed curricula.

## Current Operational Conclusion

The active term is no longer blocked by fee setup.

The active term is still not fully ready because the six curriculum placeholders are empty and non-operational by design.

The next real-world step is to obtain official curriculum content for the six programs, then fill and finalize their placeholders.

## Addendum: Empty Curriculum Blockers Soft-Retired

The user decided these six programs should be removed from active offerings instead of forcing fake curriculum content:

- `BSBA`
- `BSCE`
- `BSCS`
- `BSECE`
- `BSED`
- `BSMATH`

Business decision:

- This is a soft retirement, not a hard delete.
- `programs.active_status` was set to `0`.
- Historical records, fee rows, placeholders, and applicant references remain intact.
- The programs can be reactivated later if official curriculum content becomes available.

Reproducible SQL patch:

- `registrar/db/manual_patches/20260608_retire_empty_curriculum_blockers.sql`

Runtime result:

- First pass retired five programs and skipped `BSCS`.
- `BSCS` was skipped because the first guard counted a legacy `sys_users` student row.
- Read-only audit found:
  - no canonical `students` row for `BSCS`
  - one legacy `sys_users` row: `EAC-12E89F39`, `Maron Javier`, `ENROLLED`
  - applicant references still exist, which is acceptable for history.
- Guard was tightened to block only canonical active/admitted/enrolled `students` records.
- Second pass retired `BSCS`.

Final verification:

```json
{"WorkspaceRendered":false,"ReadinessReady":true}
```

Current conclusion:

- Fee readiness is clean.
- Curriculum completion workspace is empty/hidden.
- Active term readiness is now ready for operation.
