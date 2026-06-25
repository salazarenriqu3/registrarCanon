# Withdrawal Demo Manual

Use this manual when you need a quick registrar-only walkthrough for the restored withdrawal charge model.

Base repo:

- `D:\registrarCanon_canon`

Base app:

- Registrar: `http://localhost:8083/registrar`

Login:

- Username: `admin`
- Password: `1234`

Demo student:

- Student number: `SPRINT-DEMO-2026-001`

Important rule:

- Re-run the seed before every separate scenario.
- One approved withdrawal changes the student's load, balance, and slot counts.

## 1. Reset the demo state

Start the app:

```powershell
cd "D:\registrarCanon_canon"
.\mvnw.cmd spring-boot:run
```

Then run the seed:

```sql
SOURCE D:/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql;
```

## 2. First-week charge demo

Use section `CC101-A`.

Expected rule:

- `25% charge / 75% refund`

Steps:

1. Open Student Manager and search `SPRINT-DEMO-2026-001`
2. On `CC101-A`, click `Withdraw Subject`
3. Use reason `ACADEMIC_LOAD`
4. Submit the request
5. Open `/admin/withdrawals`
6. Approve the request
7. Return to Student Profile and confirm the subject is gone
8. Confirm the finance summary changed

## 3. Second-third-week charge demo

Reset first, then use section `CC102-A`.

Expected rule:

- `50% charge / 50% refund`

Repeat the same flow and confirm the approved request reports a 50% bucket.

## 4. Past-three-weeks charge demo

Reset first, then use section `GE101-A`.

Expected rule:

- `100% charge / 0% refund`

Repeat the same flow and confirm the approved request reports a full-charge bucket.

## 5. Full-student withdrawal demo

Reset first.

Expected result:

- all 3 subjects are removed
- student status becomes `WITHDRAWN`
- total charge reflects the mixed `25% + 50% + 100%` line snapshots

Steps:

1. Open Student Profile for `SPRINT-DEMO-2026-001`
2. In the full-student withdrawal panel choose `TRANSFER`
3. Submit the request
4. Open `/admin/withdrawals`
5. Execute the full withdrawal
6. Return to Student Profile and confirm the student is now withdrawn

## 6. Useful pages

- Pending queue: `http://localhost:8083/registrar/admin/withdrawals`
- History report: `http://localhost:8083/registrar/admin/withdrawals/report`

## 7. Cleanup

```sql
SOURCE D:/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/21_withdrawal_uat_cleanup.sql;
```
