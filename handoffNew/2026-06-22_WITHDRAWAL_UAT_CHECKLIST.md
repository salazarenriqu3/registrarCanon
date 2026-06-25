# 2026-06-25 Withdrawal UAT Checklist

Purpose: run a focused registrar-only withdrawal test on the active canonical project copy after the restored 25% / 50% / 100% charge rules.

Base repo:

- `D:\registrarCanon_canon`

Base URL:

- `http://localhost:8083/registrar`

Current expected demo term:

- `term_id = 1`
- `term_code = 1120242025`
- `A.Y. 2024-2025 - 1st Semester`

Primary demo user:

- `admin / 1234`

Primary withdrawal test student:

- `SPRINT-DEMO-2026-001`

Seed file:

- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql`

Cleanup file:

- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/21_withdrawal_uat_cleanup.sql`

## 1. What this demo must prove

- Single-subject withdrawal is requested from Student Profile, approved from the Registrar queue, and archived with a reason trail.
- Full-student withdrawal is requested from Student Profile, approved from the Registrar queue, and marks the student withdrawn.
- Outstanding balance visibly changes after approval because formal withdrawal charges now flow back into the finance summary.
- The affected class slot frees up and `slots_left` increases by 1 after approval.
- Withdrawal charges follow the restored rule set:
  - first-week subjects = `25% charge / 75% refund`
  - second to third week subjects = `50% charge / 50% refund`
  - after three weeks = `100% charge / 0% refund`

## 2. Prerequisites

- [ ] Registrar app starts from `D:\registrarCanon_canon` using `.\mvnw.cmd spring-boot:run`
- [ ] Login page opens at `/registrar/login`
- [ ] `admin / 1234` logs in successfully
- [ ] Active term remains `1120242025`
- [ ] Student `SPRINT-DEMO-2026-001` exists in `students`

## 3. Seed the withdrawal test data

Run:

```sql
SOURCE D:/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql;
```

Expected result:

- [ ] Student `SPRINT-DEMO-2026-001` is marked active/enrolled
- [ ] Student has 3 committed current-term subjects
- [ ] Current committed sections are `CC101-A`, `CC102-A`, and `GE101-A`
- [ ] Seeded enrolled-day buckets are about `3`, `10`, and `22` days respectively
- [ ] Withdrawal policy settings are `7 / 21 / 25 / 50` for half threshold, full threshold, first-week charge, and half-charge percent
- [ ] Old withdrawal requests and prior withdrawal ledger artifacts for the same student are cleared

## 4. Route smoke check

- [ ] Open `/registrar/admin/withdrawals`
- [ ] Page title shows `Registrar Withdrawal Queue`
- [ ] Subtitle says class and full-student requests wait for Registrar approval
- [ ] `/registrar/faculty/withdrawals` is not part of the workflow
- [ ] Open `/registrar/admin/withdrawals/report`
- [ ] Page title shows `Withdrawal History`

## 5. Student Profile wiring check

- [ ] Open Student Profile
- [ ] Search `SPRINT-DEMO-2026-001`
- [ ] Current load panel is visible
- [ ] Subject action label says `Withdraw Subject`
- [ ] Subject button says `Request Withdrawal`
- [ ] Full-student panel label says `Withdraw Student From Current Term`
- [ ] Full-student button says `Request Full Withdrawal`
- [ ] Page no longer suggests direct instant dropping

## 6. Single-subject withdrawal flow

Action:

1. Open Student Profile for `SPRINT-DEMO-2026-001`
2. Choose one committed subject
3. Select reason `ACADEMIC_LOAD`
4. Enter remarks like `UAT single-subject withdrawal`
5. Click `Request Withdrawal`

Expected after request:

- [ ] Success message says the class request was submitted for Registrar approval
- [ ] Subject remains on the current load before Registrar approval
- [ ] Request status is `PENDING_REGISTRAR`

Registrar queue:

1. Open `/registrar/admin/withdrawals`
2. Locate the same request
3. Click `Registrar Approve`

Expected after approval:

- [ ] Request completes successfully
- [ ] Flash message reports request number, subject count, and applied charge
- [ ] The applied charge matches the section's seeded timing bucket:
  - `CC101-A` should behave like the `25%` bucket
  - `CC102-A` should behave like the `50%` bucket
  - `GE101-A` should behave like the `100%` bucket
- [ ] Student current load decreases by 1 subject
- [ ] Student remains active and not fully withdrawn
- [ ] Registrar username and approval timestamp are preserved
- [ ] Student finance summary changes after approval

History:

1. Open `/registrar/admin/withdrawals/report`

Expected:

- [ ] Completed request is archived
- [ ] Reason is preserved
- [ ] Status is approved/completed

## 7. Full current-term withdrawal flow

Reset first:

```sql
SOURCE D:/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql;
```

Action:

1. Return to Student Profile for `SPRINT-DEMO-2026-001`
2. In the full-student panel choose `TRANSFER`
3. Enter remarks like `UAT full current-term withdrawal`
4. Click `Request Full Withdrawal`

Expected after request:

- [ ] Success message says full-student withdrawal was submitted for Registrar approval
- [ ] Student subjects remain on the load before Registrar approval
- [ ] Queue item is visible as `FULL CURRENT TERM`
- [ ] Subject count is shown on the request row

Registrar queue:

1. Open `/registrar/admin/withdrawals`
2. Locate the same request
3. Click `Execute Full Withdrawal`

Expected after approval:

- [ ] Completion flash message appears
- [ ] All current-term committed subjects are removed from current load
- [ ] Request history is archived
- [ ] Student is marked withdrawn from the current term
- [ ] The total charge reflects the mixed 25% / 50% / 100% buckets captured on the 3 line items

## 8. Data verification checks

Optional SQL verification:

```sql
SELECT request_id, student_number, withdrawal_scope, status, subject_count, estimated_charge
FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001'
ORDER BY request_id DESC;

SELECT request_id, section_id, course_id, status, timing_bucket, charge_percent, estimated_charge
FROM student_withdrawal_request_lines
WHERE student_number = 'SPRINT-DEMO-2026-001'
ORDER BY line_id DESC;

SELECT student_number, admission_status, status, is_active
FROM students
WHERE student_number = 'SPRINT-DEMO-2026-001';
```

Expected:

- [ ] Header records exist in `student_withdrawal_requests`
- [ ] Line records exist in `student_withdrawal_request_lines`
- [ ] Single-subject request keeps one line
- [ ] Full current-term request keeps one header plus multiple lines
- [ ] `registrar_approved_by`, `registrar_approved_at`, and `completed_at` are populated after approval
- [ ] Final student status reflects the full-current-term withdrawal case

## 9. Pass criteria

- [ ] Registrar queue is reachable and usable
- [ ] Student Profile sends formal requests instead of doing direct drop
- [ ] Single-subject withdrawal completes end to end
- [ ] Full current-term withdrawal completes end to end
- [ ] Withdrawal history/report preserves archived records
- [ ] Finance summary reflects formal withdrawal charges
- [ ] No Dean route or Dean approval is required for withdrawal

## 10. Cleanup

Run:

```sql
SOURCE D:/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/21_withdrawal_uat_cleanup.sql;
```

Expected:

- [ ] Withdrawal requests for `SPRINT-DEMO-2026-001` are cleared
- [ ] Student committed enlistments for this focused UAT are cleared
- [ ] Withdrawal ledger artifacts for this focused UAT are cleared
- [ ] Student row remains available for future test reseeding
