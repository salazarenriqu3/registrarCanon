# 2026-06-21 Withdrawal UAT Checklist

Purpose: run a focused registrar-only withdrawal test on the active canonical project copy.

Base repo:

- `C:\newer\registrarCanon_canon`

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

## 1. Prerequisites

- [ ] Registrar app starts from `C:\newer\registrarCanon_canon` using `mvn spring-boot:run`
- [ ] Login page opens at `/registrar/login`
- [ ] `admin / 1234` logs in successfully
- [ ] Active term remains `1120242025`
- [ ] Student `SPRINT-DEMO-2026-001` exists in `students`

## 2. Seed the Withdrawal Test Data

Run:

```sql
SOURCE C:/newer/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql;
```

Expected result:

- [ ] Student `SPRINT-DEMO-2026-001` is marked active/enrolled
- [ ] Student has 3 committed current-term subjects
- [ ] Current committed sections are `CC101-A`, `CC102-A`, and `GE101-A`
- [ ] Old withdrawal requests for the same student are cleared

## 3. Route Smoke Check

- [ ] Open `/registrar/admin/withdrawals`
- [ ] Page title shows `Registrar Withdrawal Queue`
- [ ] Subtitle says class and full-student requests wait for Registrar approval
- [ ] `/registrar/faculty/withdrawals` is not part of the withdrawal workflow
- [ ] Open `/registrar/admin/withdrawals/report`
- [ ] Page title shows `Withdrawal History`

## 4. Student Profile Wiring Check

- [ ] Open Student Profile
- [ ] Search `SPRINT-DEMO-2026-001`
- [ ] Current load panel is visible
- [ ] Subject action label says `Withdraw Subject`
- [ ] Subject button says `Request Withdrawal`
- [ ] Full-student panel label says `Withdraw Student From Current Term`
- [ ] Full-student button says `Request Full Withdrawal`
- [ ] Page no longer suggests direct instant dropping

## 5. Single-Subject Withdrawal Flow

Action:

1. Open Student Profile for `SPRINT-DEMO-2026-001`
2. Choose one committed subject
3. Select reason `ACADEMIC_LOAD`
4. Enter remarks like `UAT single-subject withdrawal`
5. Click `Request Withdrawal`

Expected:

- [ ] Success message says the class request was submitted for Registrar approval
- [ ] Subject remains on the current load before Registrar approval
- [ ] Request status is `PENDING_REGISTRAR`

Registrar queue:

1. Open `/registrar/admin/withdrawals`
2. Locate the same request
3. Click registrar approve

Expected:

- [ ] Request completes successfully
- [ ] Flash message reports request number, subject count, and applied charge
- [ ] Student current load decreases by 1 subject
- [ ] Student remains active and not fully withdrawn
- [ ] Registrar username and approval timestamp are preserved

History:

1. Open `/registrar/admin/withdrawals/report`

Expected:

- [ ] Completed request is archived
- [ ] Reason is preserved
- [ ] Status is approved/completed

## 6. Full Current-Term Withdrawal Flow

Reset first:

```sql
SOURCE C:/newer/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql;
```

Action:

1. Return to Student Profile for `SPRINT-DEMO-2026-001`
2. In the full-student panel choose `TRANSFER`
3. Enter remarks like `UAT full current-term withdrawal`
4. Click `Request Full Withdrawal`

Expected:

- [ ] Success message says full-student withdrawal was submitted for Registrar approval
- [ ] Student subjects remain on the load before Registrar approval
- [ ] Queue item is visible as `FULL CURRENT TERM`
- [ ] Subject count is shown on the request row

Registrar queue:

1. Open `/registrar/admin/withdrawals`
2. Locate the same request
3. Click `Execute Full Withdrawal`

Expected:

- [ ] Completion flash message appears
- [ ] All current-term committed subjects are removed from current load
- [ ] Request history is archived
- [ ] Student is marked withdrawn from the current term

## 7. Data Verification Checks

Optional SQL verification:

```sql
SELECT request_id, student_number, withdrawal_scope, status, subject_count
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

## 8. Pass Criteria

- [ ] Registrar queue is reachable and usable
- [ ] Student Profile sends formal requests instead of doing direct drop
- [ ] Single-subject withdrawal completes end to end
- [ ] Full current-term withdrawal completes end to end
- [ ] Withdrawal history/report preserves archived records
- [ ] No Dean route or Dean approval is required for withdrawal

## 9. Cleanup

Run:

```sql
SOURCE C:/newer/registrarCanon_canon/handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/21_withdrawal_uat_cleanup.sql;
```

Expected:

- [ ] Withdrawal requests for `SPRINT-DEMO-2026-001` are cleared
- [ ] Student committed enlistments for this focused UAT are cleared
- [ ] Student row remains available for future test reseeding
