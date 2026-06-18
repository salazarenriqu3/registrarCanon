# Program fee CSV files (demo)

Use these when bootstrap materialization left gaps, or when you want to **edit fees in Excel** and re-import.

**Active term after fresh setup:** `1120242025` (`term_id = 1`)  
**Registrar UI:** http://localhost:8083/registrar/admin/term-fees?termId=1

---

## Files

| File | When to use |
|------|-------------|
| `term-fee-import-template-1120242025.csv` | Full 21-program × 8-scope template. Upload via **Zone 1 → Or upload completed CSV**. |
| `term-fee-readiness-gaps-export.csv` | Example gap-list format. Regenerate live from UI: **Export gap list**. |
| `term-fee-global-demo-seed-reference.csv` | BSCPE + BSIT demo rates (S1/S2) — quick scoped import reference |

---

## Import rules

- Each row: `program_code`, `year_level`, `semester` (1 = curriculum S1, 2 = curriculum S2).
- Primary rate required: `TUITION_PER_UNIT`, `LEC_FEE_PER_UNIT`, or `RLE_FEE_PER_UNIT`.
- Blank cells do **not** overwrite existing exact term rows.
- **Zone 1 Global import** (UI): copy entire prior term → target term without CSV.

---

## Preferred workflows

| Goal | Action |
|------|--------|
| Fresh DB | Run `RUN_FRESH_SETUP.cmd` — usually no CSV needed |
| Fix one program | Program Fees → Zone 2 scoped import |
| Bulk external sheet | Fill template CSV → Zone 1 upload |
| Copy to next term | Zone 1 Global import: source `1120242025` → target `2120242025` |

Demo rate source SQL: `registrar/db/capss-demo-required/01_setup/03_seed_program_fees_full_lifecycle.sql`
