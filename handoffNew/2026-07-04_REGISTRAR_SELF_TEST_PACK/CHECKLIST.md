# Registrar Self-Test Checklist

Use this after the full demo seed is loaded and the three apps are running.

## 1. Bring Up The Environment

1. Open a fresh terminal.
2. Go to the registrar canon repo:
   ```cmd
   cd /d E:\registrarCanon_canon
   ```
3. If you are starting from scratch, run:
   ```cmd
   setup\CHECK_PREREQUISITES.cmd
   setup\RUN_FRESH_SETUP.cmd
   setup\LOAD_FULL_REGISTRAR_DEMO_DATA.cmd
   ```
4. Start the apps in separate terminals:
   ```cmd
   cd /d E:\registrarCanon_canon
   mvn spring-boot:run
   ```
   ```cmd
   cd /d E:\EnrollLatest\enrollment3
   mvn spring-boot:run
   ```
   ```cmd
   cd /d E:\AdmitLatest\admission
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

## 2. Confirm The Pages Open

- Registrar: `http://localhost:8083/registrar/login`
- Enrollment: `http://localhost:8082`
- Admission: `http://localhost:8081/`

If any app fails to open, stop there and fix the startup before testing flows.

## 3. Test In This Order

1. Admission applicant snapshot.
   - Open the registrar student profile for `2026-1001`.
   - Confirm applicant snapshot and applicant documents are visible.
2. Student profile.
   - Search `2026-1001`, `ADDCLS-2026-001`, `TTRNS-2026-001`, `TSHFT-2026-001`, `OVRPAY-2026-001`, `SPRINT-DEMO-2026-001`.
   - Confirm the profile shows load, finance, withdrawal history, reg-form history, and document trail.
3. Curriculum and course usage.
   - Open Curriculum Management.
   - Open Course Catalog.
   - Confirm current/legacy labels and where-used drilldown.
4. Scheduling.
   - Open Class Scheduling.
   - Confirm room assignment is required.
   - Confirm room, faculty, and section collisions are blocked.
5. Student load and shifting.
   - Use `TSHFT-2026-001` for shift testing.
   - Confirm pre-enrollment shift and post-enrollment cleanup behavior.
   - Confirm destination curriculum is filtered by program.
6. Withdrawal.
   - Use `SPRINT-DEMO-2026-001`.
   - Confirm single-subject drop, full withdrawal, archive tracking, and student-number release behavior.
7. Scholarship.
   - Use the seeded scholarship cases.
   - Confirm only academic scholarship is evaluated.
   - Confirm GWA, unit-load, and PE/NSTP rules are enforced.
8. Grade records.
   - Open Grade Records and Grade Approvals.
   - Confirm official rows, approvals, rejections, and event history are visible.
9. Accreditation.
   - Confirm registrar can approve credit/accreditation requests.
10. Finance and ledger.
   - Open the finance surfaces for `OVRPAY-2026-001`.
   - Confirm overpay and ledger visibility do not mutate withdrawn history.

## 4. What Good Looks Like

- Withdrawn students stay historical and do not behave like active students.
- Released student numbers stay searchable through archive-aware lookup only.
- Lecture/lab split subjects behave as separate enlistable rows.
- Program shifts do not trigger a full school withdrawal.
- Academic scholarship uses seeded official grade rows, not a live grading app.
- Schedule saves refuse overlaps instead of silently accepting them.

## 5. Stop And Report If You See

- Whitelabel error pages.
- A withdrawn student becoming active again.
- Ledger or cashier pages changing a student status unexpectedly.
- Course/curriculum options that are not filtered by the selected program.
- Missing document download/view actions on a student profile.
- A schedule row saving without a room.

## 6. Reference Data

Use these records during the test pass:

- `2026-1001`
- `ADDCLS-2026-001`
- `TTRNS-2026-001`
- `TSHFT-2026-001`
- `OVRPAY-2026-001`
- `SPRINT-DEMO-2026-001`
- `DEMO-SANTOS-001`
- `3SYS-SMOKE-0001`

## 7. Source Of Truth

If you need the full background, read:

- `README.md`
- `RUNBOOK_THREE_APPS.md`
- `SQL_FEED_AND_VERIFY.md`
- `TESTING_STORYBOARD.md`
- `NEXT_AGENT_HANDOFF.md`

