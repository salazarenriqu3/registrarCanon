# 2026-06-27 Registrar Audit Trail Handoff

## Scope

Registrar-only audit hardening is now started and wired into key registrar-owned actions. This does not move ownership of cashier/accounting functions into Registrar. Registrar may read finance context when needed, but cashier/accounting operations remain outside registrar scope.

## Schema

The legacy `audit_logs` table remains backward-compatible with `log_id`, `admin_id`, `action`, and `log_date`, but now includes operational columns:

- `actor_username`
- `actor_role`
- `module_name`
- `action_name`
- `target_type`
- `target_key`
- `summary`
- `details`
- `source_table`
- `source_id`

Fresh setup SQL and runtime bootstrap both create or upgrade this schema.

## Covered Registrar Actions

- Student document custody/events through `StudentDocumentTrailService`
- Registration form events through `RegFormEventService`
- Editable student profile updates
- Student curriculum assignment
- Academic scholarship request, approval, rejection, posting, and revocation
- Class scheduling block creation/rematerialization
- Course section open/close
- Schedule slot add/remove
- Faculty assignment to sections
- Slot monitoring capacity updates and section close/bulk-close actions

## Verification SQL

Run after performing a few registrar actions:

```sql
USE eacdb;

SELECT
  log_id,
  actor_username,
  module_name,
  action_name,
  target_type,
  target_key,
  summary,
  log_date
FROM audit_logs
ORDER BY log_id DESC
LIMIT 30;
```

Filter by registrar test account:

```sql
SELECT actor_username, module_name, action_name, target_key, log_date
FROM audit_logs
WHERE actor_username IN ('registrar.main', 'registrar.records', 'registrar.scholar', 'registrar.schedule')
ORDER BY log_id DESC;
```

## Demo Accounts

- `registrar.main` / `1234` - general registrar operator
- `registrar.records` / `1234` - records/document custody operator
- `registrar.scholar` / `1234` - academic scholarship operator
- `registrar.schedule` / `1234` - scheduling/sectioning operator

## Residual Gap

The audit table is query-ready and action-backed, but there is not yet a dedicated UI page for browsing `audit_logs` by actor/module/date. Current visible history pages still use domain-specific trails such as document events and RegForm history.
