# 2026-07-01 Subject Drop Rename Note

## What Changed
- The registrar profile action for individual class removal is now presented as **Drop**, not **Withdrawal**.
- The Student Profile table column now says **Drop**.
- The registrar action button now says **Drop Now**.
- The subject-drop confirmation prompt now uses class-drop wording.
- The subject-drop form no longer exposes a reason selector to staff.

## Internal Archival Behavior
- The backend still archives subject-drop events in the withdrawal archive tables because the archive is the canonical trail store.
- Subject drops now use an internal hidden reason code: `CLASS_DROP`.
- That reason is seeded automatically, but it is filtered out of the public withdrawal reason lists so it does not leak into the full-withdrawal or shift-cleanup UI.

## What Stayed the Same
- Full-student withdrawal from school still uses the reason selector and keeps the existing withdrawal governance.
- Shift cleanup still uses the registrar shift flow and remains separate from the new class-drop wording.
- The compatibility overload for the old subject-drop service signature was kept so existing tests and older callers do not break.

## Verification
- `mvn -q -DskipTests package` completed successfully after the change.

