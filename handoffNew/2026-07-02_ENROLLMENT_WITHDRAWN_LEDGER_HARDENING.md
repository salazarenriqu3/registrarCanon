# 2026-07-02 Enrollment Withdrawn Ledger Hardening

Purpose:

- Close the remaining enrollment-side gap where a withdrawn registrar archive record could still be acted on from the enrollment ledger surface.
- Keep the historical ledger readable while blocking term-advance and scholarship actions for withdrawn students.

## What was verified before the patch

- Registrar Student Profile correctly resolved withdrawn archive identities such as `ARCH-807E99E49B8B`.
- Admission status lookup still worked for active bridge case `DEMO-SANTOS-001` / `2026-1001`.
- Enrollment cashier and walk-in already had withdrawn-student guards.
- Enrollment ledger still rendered active controls for a withdrawn archive record:
  - `Advance to open term`
  - `Update Scholarship`
- In the live `E:\EnrollLatest\enrollment3` runtime, a normal enrolled student lookup such as `OVRPAY-2026-001` did **not** reproduce the older mutation bug; status stayed `ACTIVE / ENROLLED`.

## Patch applied

Live runtime copy:

- `E:\EnrollLatest\enrollment3`

Latest downloaded canon copy:

- `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

### Controller changes

- `AdminController`
  - Ledger model now exposes `withdrawnStudentLocked`.
  - Scholarship update now hard-blocks withdrawn/inactive students and redirects back to ledger with an error message.
- `EnrollmentController`
  - `/admin/advance-student-term` now hard-blocks withdrawn/inactive students before any term close/advance work runs.

### Template changes

- `admin_ledger.html`
  - hides `Advance to open term` for withdrawn archive records and replaces it with a read-only warning note
  - hides the scholarship update form for withdrawn archive records and replaces it with a registrar-history note
  - renders withdrawn archive identity context instead of the normal active-enrollment helper copy
  - renders plain `WITHDRAWN` status styling instead of a misleading mixed active/pending interpretation

### Registrar-side companion change

- `E:\registrarCanon_canon\src\main\java\com\iuims\registrar\portal\EnrollmentController.java`
  - Student Profile now emits a student-specific Enrollment deep link:
    - `http://localhost:8082/admin/cashier?keyword=<student_number>`
  - the generic fallback cashier URL is only used when no student profile is currently resolved

### D-copy realignment

- The newer D enrollment copy still called `ensurePreRegEnrollmentFinalized(...)` inside the ledger GET path.
- That call was removed from the D ledger route so the latest canon no longer carries that pre-reg finalization side effect on ledger open.

## Validation

Build:

- `mvn -q -DskipTests package` passed for:
  - `E:\EnrollLatest\enrollment3`
  - `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`

Live browser checks:

- Withdrawn archive case `ARCH-807E99E49B8B`
  - ledger still opens
  - historical balance and penalty rows remain visible
  - `Advance to open term` is replaced by a withdrawn warning
  - scholarship update form is no longer rendered
  - generic active-term helper copy is no longer shown
  - withdrawn status now renders as plain `WITHDRAWN`
- Active enrolled case `OVRPAY-2026-001`
  - ledger still opens normally
  - scholarship controls remain available
  - shared DB status remained `ACTIVE / ENROLLED / Irregular` after lookup
- Registrar active profile case `2026-1001`
  - Student Profile still renders normally
  - `Open Enrollment System (External)` now points to the matching enrollment cashier search instead of the generic cashier landing page

## Current conclusion

Enrollment is now aligned with the withdrawn-student governance rule on this ledger surface:

- historical lookup is allowed
- cashier/walk-in/finalization remain blocked
- term advance is blocked
- scholarship edits are blocked
- registrar can deep-link into the matching enrollment identity without forcing the tester to re-search manually

The remaining ledger/cashier caveat is still the broader documented `repair-on-view` behavior in some enrollment financial paths; this patch only closes the withdrawn-record action gap and removes the D ledger's pre-reg finalization side effect.
