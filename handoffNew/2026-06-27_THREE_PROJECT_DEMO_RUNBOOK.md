# Three-Project Demo Runbook

Date: 2026-06-27  
Workspace: `D:\registrarCanon_canon`  
Scope: Registrar + Enrollment3 + Admission

This runbook is the quickest way to get oriented, start the three projects, and replay the seeded registrar demo stories without re-reading every historical handoff.

## 1. Current live state

Verified in this workspace on 2026-06-27:

- Registrar is reachable at `http://localhost:8083/registrar/login`
- Enrollment3 is reachable at `http://localhost:8082/login`
- Admission is reachable at `http://localhost:8081/admission/admin/login`

## 2. Demo accounts

### Registrar

| Account | Password | Notes |
|---|---|---|
| `admin` | `1234` | Registrar admin demo |
| `prof.cruz` | `1234` | Faculty grading demo |
| `registrar.main` | `1234` | Alternate registrar admin |
| `registrar.records` | `1234` | Records workspace |
| `registrar.scholar` | `1234` | Scholarship workspace |
| `registrar.schedule` | `1234` | Scheduling workspace |

### Enrollment3

| Account | Password | Notes |
|---|---|---|
| `admin` | `1234` | Enrollment admin |
| `cashier` | `1234` | Cashier terminal |
| `prof.cruz` | `1234` | Grading bridge demo |

### Admission

| Account | Password | Notes |
|---|---|---|
| `admin-adms` | `adminadms` | Admission admin bootstrap |
| `encoder-adms` | `encoderadms` | Admission encoder bootstrap |

## 3. How the three systems fit together

- Admission owns the applicant intake and uploaded document trail.
- Enrollment3 owns pre-advising, pre-reg generation, cashier/payment readiness, and the enrollment-side student workflow.
- Registrar owns the official student record, curriculum assignment, document trail, registration form history, subject add/drop, shifting, withdrawals, scholarship review, and grading approval.

## 4. Launch commands

If you need to restart the apps manually, use the packaged WARs directly:

### Registrar

```cmd
C:\Program Files\Java\jdk-21.0.11\bin\java.exe -jar D:\registrarCanon_canon\target\registrar-0.0.1-SNAPSHOT.war
```

### Enrollment3

```cmd
C:\Program Files\Java\jdk-21.0.11\bin\java.exe -jar C:\newws\enrollment3\target\enrollment.war
```

### Admission

```cmd
C:\Program Files\Java\jdk-21.0.11\bin\java.exe ^
  -Dspring.datasource.url=jdbc:mysql://localhost:3306/eacdb ^
  -Dspring.datasource.username=root ^
  -Dspring.datasource.password= ^
  -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect ^
  -jar C:\newws\admission\target\admission.war
```

## 5. Seed and test data

### Canonical registrar demo IDs

| Student / Ref | Purpose |
|---|---|
| `DEMO-SANTOS-001` | Admission applicant with uploaded docs, document trail, and application logs |
| `2026-1001` | Baseline enrolled student, admission bridge, registration form, history, profile, and document trail |
| `ADDCLS-2026-001` | Add-subject demo using block sections |
| `TTRNS-2026-001` | TOR / transfer-credit demo |
| `TSHFT-2026-001` | Program shift demo |
| `SPRINT-DEMO-2026-001` | Withdrawal demo |
| `OVRPAY-2026-001` | Overpayment / ledger demo |
| `SCH-UAT-ELIGIBLE` | Scholarship success case |
| `SCH-UAT-LOWUNITS` | Scholarship failure case |

### Seeder bundle

- Load: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/04_RUNNERS/13_LOAD_FULL_REGISTRAR_DEMO_DATA.cmd`
- Verify: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/06_registrar_feature_demo_verify.sql`
- Matrix: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/REGISTRAR_STUDENT_TEST_MATRIX.md`
- Full seed SQL: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/05_registrar_feature_demo_seed.sql`

### Focused withdrawal UAT

- Seed: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/20_withdrawal_uat_seed.sql`
- Cleanup: `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/21_withdrawal_uat_cleanup.sql`

## 6. Presenter flow

Use this order if you want the smoothest story.

### A. Admission first

1. Open `http://localhost:8081/admission/admin/login`.
2. Sign in as `encoder-adms / encoderadms`.
3. Open the applicant record for `DEMO-SANTOS-001`.
4. Show the uploaded docs:
   - Form 138
   - Good Moral
   - PSA Birth Certificate
   - ID Picture
   - Other document
5. Open the application log or trail view.
6. Explain that the admissions ref becomes the registrar bridge record for `2026-1001`.

### B. Enrollment3 second

1. Open `http://localhost:8082/login`.
2. Sign in as `cashier / 1234` or `admin / 1234`.
3. Show pre-advising / pre-reg generation for the irregular or shifted student flows.
4. Show the cashier side for term readiness and fee visibility.
5. If needed, show `prof.cruz / 1234` for the grading bridge.

### C. Registrar last

1. Open `http://localhost:8083/registrar/login`.
2. Sign in as `admin / 1234`.
3. Open `/admin/student-manager?username=2026-1001`.
4. Show:
   - identity card
   - current load
   - admission snapshot
   - applicant documents
   - document trail
   - reg form history
5. Use `ADDCLS-2026-001`, `TTRNS-2026-001`, `TSHFT-2026-001`, and `SPRINT-DEMO-2026-001` for the feature stories.
6. Use `SCH-UAT-ELIGIBLE` and `SCH-UAT-LOWUNITS` for scholarship eligibility.
7. Use `prof.cruz / 1234` for the grading handoff.

## 7. What to show during the demo

- Real roomed schedules, not TBA placeholders
- Block-section ownership for regular offerings
- Irregular customization using block sections, not dedicated irregular classes
- Room monitoring and room conflict enforcement
- Faculty max-load enforcement
- Schedule conflict enforcement
- Program shift and withdrawal behavior
- Document trail and reg-form history
- Scholarship eligibility based on curriculum/year/semester expectations

## 8. Reference docs

- `handoffNew/2026-06-27_TESTING_DEMO_FILE_MAP.md`
- `handoffNew/2026-06-27_THREE_PROJECT_CUE_CARD.md`
- `handoffNew/2026-06-27_THREE_PROJECT_DEMO_CHECKLIST.md`
- `handoffNew/REGISTRAR_FEATURE_DEMO_MANUAL_20260625.md`
- `handoffNew/THREE_TRACK_LIFECYCLE_DEMO_MANUAL.md`
- `handoffNew/MASTER_DEMO_UAT_MANUAL.md`
- `handoffNew/2026-06-18_FINAL_DEMO_PACKAGE/03_TEST_DATA/README.md`

## 9. Notes for the next agent

- Keep the registrar-first boundary intact.
- Do not reintroduce retired irregular-section logic except for summer/tutorial exceptions.
- Use the packaged WARs for local demo runs when Maven dependency resolution is blocked.
- Treat the seeded IDs above as canonical demo anchors.
