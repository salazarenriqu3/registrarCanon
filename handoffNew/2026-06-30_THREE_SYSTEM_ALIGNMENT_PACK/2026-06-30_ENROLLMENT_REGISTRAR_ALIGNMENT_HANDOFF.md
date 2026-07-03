# Enrollment to Registrar Alignment Handoff

Date: 2026-06-30

Canonical systems used for this handoff:

| System | Canon path | Runtime role |
| --- | --- | --- |
| Registrar | `D:\registrarCanon_canon` | Academic authority, curriculum/section/schedule authority, official record custodian, approval gate |
| Enrollment | `D:\EnrollLatest\enrollment3` | Pre-advising, pre-registration, cashier/accounting, student-number issuance, enrollment finalization |
| Admission | `D:\AdmitLatest\admission` | Applicant intake, applicant documents, applicant qualification |

This document is written for the Enrollment team. It explains where Enrollment must align with Registrar, which tables and lifecycle points are shared, and which actions should not be duplicated in Enrollment.

## Bottom Line

Registrar is not the owner of applicant pre-registration, cashier payments, or fee authoring. Enrollment owns those. Registrar owns the academic source of truth that Enrollment must consume: active term, program/curriculum/course structure, section offerings, schedules, rooms, faculty assignment, official student academic history, official grades, accreditation approval, withdrawal governance, document custody, and registration form history.

The integration is currently shared-database centric through `eacdb`. Treat the shared database as a contract, not as permission for every app to write every table.

## Non-Negotiable Rules

| Rule | Required behavior |
| --- | --- |
| Active term | Registrar is the active-term authority through `system_settings.CURRENT_ACADEMIC_TERM` and `academic_terms`. Enrollment and Admission must read this, not maintain a separate active-term truth. |
| Official enrollment | A student is only truly current-term enrolled when identity/status, payment/finalization, and committed current-term enlistments all agree. A status string alone is not enough. |
| Enlistment status | `student_enlistments.enlistment_status = 'COMMITTED'` means official load. `STAGED` or pre-reg rows are not official Registrar load. |
| Sections | All regular section offerings are block-section offerings. Irregular students do not get special "IRREG" sections. They customize by joining classes owned by different block sections. Exceptions are summer, tutorial, or explicitly approved special-class offerings. |
| Max units | Max load is dynamic from the student's assigned curriculum and specific year/semester/program. Do not use a global regular max-unit rule as the source of truth. |
| Accreditation | Enrollment Dean authors/submits accreditation. Registrar only approves or rejects and posts approved credits into official records. |
| Fees | Enrollment/Accounting/Cashier owns fee authoring, payments, assessments, and ledger. Registrar reads term fee readiness and financial state for readiness/status display. |
| Withdrawn students | A school-withdrawn student is a historical/archived identity. Enrollment must block payment, enlistment, shifting, and finalization for withdrawn/inactive/blocked students except for an explicit reissue/reactivation process. |
| Student number reissue | Registrar releases withdrawn numbers through `student_number_release_registry`; Enrollment may consume available released numbers during automatic issuance. |
| Documents | Admission uploads applicant documents. Registrar displays, trails, archives, and prints student-facing document/reg-form history after handoff. |

## Ownership Boundaries

| Area | Admission owns | Enrollment owns | Registrar owns |
| --- | --- | --- | --- |
| Applicant intake | Application profile, documents, qualification, admission status before enrollment | Reads applicant context for cashier/pre-reg | Read-only applicant snapshot/document view after handoff |
| Pre-advising | None after qualification handoff | New irregular pre-advising, continuing irregular pre-advising, Dean draft/credit input | Academic rules and approval gates that make results official |
| Pre-registration | May show applicant-facing context | Generates applicant/irregular pre-reg and pre-reg basis for payment/finalization | Reads resulting official load only after committed |
| Fees and payments | No cashier ownership | Fee setup, cashier, accounting, ledger, receipts, downpayment threshold, final assessment | Read-only readiness/state for student profile and Registrar checks |
| Student number | Does not issue | Issues student number after payment/pre-reg gate | Reads assigned number; can release a withdrawn number for reuse |
| Curriculum/catalog | Does not author | Reads for advising/pre-reg | Authors and publishes programs, curricula, courses, prerequisites, year/sem unit rules |
| Sections/schedules | Does not author | Reads available offerings for pre-reg/enlistment | Creates sections, rooms, schedules, faculty assignments, conflict rules |
| Grades | Does not author official grades | Dean may originate accreditation/grade-credit request only | Official grade gate, grade records, audit, scholarship eligibility basis |
| Accreditation | Supplies applicant history/doc context | Dean authors/submits accreditation requests | Approves/rejects and posts approved credit to `grades` |
| Withdrawal | Does not withdraw students | Must respect withdrawn/blocked state | Subject withdrawal, load cleanup for shift, school withdrawal, archive, release registry |
| Reports/printing | Applicant documents | Receipts/accounting reports/pre-reg | Registration form, COG, document trail, reg-form history, academic reports |

## Lifecycle Alignment Points

| Step | Source system | Registrar interaction | Enrollment requirement |
| --- | --- | --- | --- |
| 1. Applicant qualifies | Admission | Registrar later reads applicant snapshot and documents | Do not write Registrar student records yet |
| 2. Applicant pre-advising / pre-reg | Enrollment | Use Registrar-owned active term, curriculum, courses, prerequisites, sections, schedules, and max-unit rules | Pre-reg must resolve to real course and section rows, no TBA/fake demo rows in production |
| 3. Payment/downpayment | Enrollment Cashier | Registrar reads finance state for readiness | Payment should not imply official load unless finalization commits enlistments |
| 4. Student number issuance | Enrollment Cashier | Registrar sees `students` and `sys_users` identity | Issue only after pre-reg/payment gates; consume release registry only when available |
| 5. Enrollment finalization | Enrollment | Registrar sees current load through `student_enlistments` | Commit eligible current-term lines as `COMMITTED`; do not leave `ENROLLED` students with zero committed load |
| 6. Continuing/irregular advising | Enrollment Dean | Registrar supplies rules and later reads official outcome | Dean/adviser drafts load; finalization commits after accounting gate |
| 7. Accreditation | Enrollment Dean then Registrar | Registrar approval screen reviews pending requests | Submit dean-origin requests with source metadata; wait for Registrar decision |
| 8. Program shift | Registrar for enrolled students | Registrar updates profile/curriculum/history and clears load if needed | Enrollment may handle pre-enrollment reassignment only before official enrollment |
| 9. Withdrawal from school | Registrar | Registrar archives, blocks, and optionally releases number | Enrollment must block cashier/enlistment/finalization for withdrawn identity |
| 10. Grade/record reporting | Registrar | Registrar is official store for grades/audit | Enrollment reports must read official Registrar grades or approved sync state |

## Shared Data Contracts

### Active Term

Enrollment and Admission should resolve the current term from:

| Table/field | Meaning |
| --- | --- |
| `system_settings.setting_key = 'CURRENT_ACADEMIC_TERM'` | Global current academic term code |
| `academic_terms` | Term metadata and term id used by schedules/sections |

Current Enrollment code already reads Registrar term settings in:

| File | Purpose |
| --- | --- |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\AcademicTermService.java` | Reads `CURRENT_ACADEMIC_TERM` |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\AdmissionSchoolTermLookupService.java` | Resolves school term from Registrar settings |
| `D:\AdmitLatest\admission\src\main\java\com\example\enrollment\service\AdmissionRegistrarTermSyncService.java` | Admission-side term sync/read |

Required behavior:

- Do not introduce a second active-term switch in Enrollment.
- Do not finalize pre-reg/enlistments into a different term than Registrar active term.
- Do not switch `CURRENT_ACADEMIC_TERM` until Registrar term readiness passes.

### Academic Catalog and Scheduling

Registrar owns these entities:

| Entity/table area | Registrar meaning | Enrollment use |
| --- | --- | --- |
| `programs` | Programs and school/department ownership | Read for applicant/program selection and advising |
| `curriculum_templates` | Published/current curriculum versions | Read to determine applicable curriculum |
| `curriculum_courses` | Year/semester course placement and unit totals | Read for required courses and dynamic max load |
| Course/prerequisite tables | Course catalog and prerequisite rules | Read for eligible subject filtering |
| `class_sections` | Official block/special/summer/tutorial sections | Read/select; do not create regular sections from Enrollment |
| `course_schedules` | Official schedule rows | Read for section time/room/faculty conflict awareness |
| `rooms` | Official room inventory | Read only unless Registrar grants a room admin path |
| Faculty tables | Official faculty identity and department assignment | Read for schedule display and constraints |

Required behavior:

- Enrollment pre-reg must select real Registrar section offerings.
- Regular and irregular students use block-owned section offerings.
- Irregularity means the student can mix classes from multiple block sections.
- Do not create dedicated "IRREG" regular-term sections for irregular students.
- Special-class, tutorial, and summer offerings are explicit exceptions and should be flagged distinctly.
- No production schedule/enlistment rows should contain TBA faculty, TBA room, or fake `DEMO-SEC` names when the purpose is official enrollment.

### Dynamic Max Units

The max unit source of truth is the assigned curriculum:

| Student context | Max load source |
| --- | --- |
| BSIT first year, first semester | Total units of BSIT active curriculum, year 1, semester 1 |
| Criminology first year, first semester | Total units of Criminology active curriculum, year 1, semester 1 |
| Shifted/irregular student | Assigned post-shift curriculum plus eligible no-prerequisite or satisfied-prerequisite courses |
| Graduating/overload/underload case | Registrar-governed exception path, not a global override |

Required behavior:

- Enrollment should compute regular load expectations from `curriculum_courses` for the assigned curriculum/year/semester.
- Scholarship/minimum-unit checks should align to the curriculum-specific expected load, not a global minimum.
- Prerequisite, schedule conflict, faculty load, room conflict, capacity, and current-term status still apply.

### Student Identity and Official Enrollment

Enrollment owns student-number issuance in the normal Admission-to-Cashier flow.

Relevant current Enrollment files:

| File | Purpose |
| --- | --- |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\ApplicantStudentNumberService.java` | Issues student number after payment/pre-reg gate |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\PreRegEnrollmentFinalizeService.java` | Converts pre-reg/staged lines into committed official enlistments |

Required identity rows:

| Table | Requirement |
| --- | --- |
| `students` | Student identity row with `student_number`, `reference_number`, program/year/semester/status |
| `sys_users` | Login/account row aligned with `students.username`/student number |
| `student_curriculum_assignments` | Assigned curriculum if the student has a concrete curriculum |

Official current-term enrollment requires:

| Gate | Expected state |
| --- | --- |
| Identity | `students` and `sys_users` agree on student number, program, type, active/enrollment status |
| Load | At least one valid current-term `student_enlistments` row with `enlistment_status = 'COMMITTED'` for normal enrolled load |
| Section | Each committed row resolves to a valid `class_sections` row for the active term |
| Course | Each committed row resolves to a curriculum-covered course, unless Registrar-approved exception |
| Finance | Enrollment ledger/assessment indicates payment/downpayment/finalization gate passed |

Important current-code warning:

`ApplicantStudentNumberService` currently inserts `students` and `sys_users` with `admission_status = 'ENROLLED'` before or around `finalizeFromPreReg(...)`. This is acceptable only if the transaction cannot leave the database in an `ENROLLED` plus zero committed load state. If finalization fails because pre-reg rows do not resolve to real courses/sections, Enrollment must roll back or keep the applicant/student in a non-enrolled blocked state.

Acceptance condition:

- Registrar Student Manager must never show a new applicant as `ENROLLED` with `0 units` unless the user is explicitly in a documented shifted/withdrawn/no-current-load historical state.

### Enlistment Status

| Status | Meaning |
| --- | --- |
| `STAGED` | Draft/pre-reg/current-term candidate, not official load |
| `COMMITTED` | Official current-term load visible to Registrar and reports |
| Withdrawn/drop statuses | Historical lines governed by Registrar withdrawal logic |

Required behavior:

- Cashier finalization should promote eligible staged/pre-reg rows to `COMMITTED`.
- Registrar current load views should rely on `COMMITTED` current-term rows.
- Ledger/assessment should match committed load, not stale withdrawn or duplicate rows.
- If a student is school-withdrawn, no new committed load should be created by Enrollment.

### Fees, Accounting, and Readiness

Enrollment/Accounting/Cashier owns:

| Area | Enrollment responsibility |
| --- | --- |
| Fee setup | Program fees, miscellaneous fees, normalized fee rates |
| Assessment | Pre-reg and final term assessment |
| Payments | Cashier payments, receipts, payment re-keying from applicant ref to student number |
| Ledger | Student ledger, balances, forwarded balances, official balance display |
| Clearance | Downpayment/payment gates for finalization |

Registrar needs read access to:

| Data | Registrar use |
| --- | --- |
| Student payment/ledger state | Term readiness and alerts |
| Fee readiness/blockers | Registrar-side term readiness display |
| Current balance | Student profile context |

Required behavior:

- Registrar should not author live cashier transactions.
- Enrollment should not use withdrawn or archived current-load rows as billable enrolled subjects.
- If Registrar withdraws all current load for shift, Enrollment ledger should not keep treating withdrawn lines as active tuition unless local accounting policy explicitly records historical charges/refunds.
- If Registrar marks school withdrawal, Enrollment must block new cashier/finalization actions for that student number unless an approved reissue/reactivation flow exists.

### Transfer Credit / TOR Accreditation

Steady-state workflow:

| Step | Owner | Required data/result |
| --- | --- | --- |
| Dean review/credit input | Enrollment Dean | Dean-created accreditation draft/request |
| Request submission | Enrollment | Pending request with dean-origin metadata |
| Approval/rejection | Registrar | Registrar approves or rejects |
| Official posting | Registrar | Approved credit is posted to official `grades` |
| Upstream sync | Registrar/Enrollment | Enrollment reads Registrar decision or source sync columns |

Registrar-side approval gate:

| Table/field | Required value |
| --- | --- |
| `transfer_credit_requests.status` | `PENDING` before approval/rejection |
| `transfer_credit_requests.requested_by_role` | `Dean`, `Enrollment Dean`, or `Enrollment_Dean` |
| `transfer_credit_requests.source_system` | Recommended: `ENROLLMENT3` |
| `transfer_credit_requests.source_table` | `tentative_credited_subject` or `applicant_credit_accreditation_lines` |
| `transfer_credit_requests.source_row_id` | Enrollment source row id |

Registrar posts approved credits into:

| Table | Meaning |
| --- | --- |
| `grades` | Official grade/credit record |
| `grade_record_events` | Audit trail of grade/credit action |
| `student_document_events` / reg-form events | Student-facing trail where applicable |

Current-code reality:

- Older notes mention `RegistrarTransferCreditRequestBridgeService`, but that standalone class is not present in the current `D:\EnrollLatest\enrollment3` copy.
- Current Enrollment credit paths include `PreAdmissionDraftCreditService` and `ApplicantDeanAccreditationService`.
- If Enrollment wants an automatic bridge, implement it against the table contract above rather than relying on the missing class name.

Required behavior:

- Enrollment Dean may author and submit accreditation.
- Registrar must be the only actor that turns accreditation into official grades.
- Enrollment must not directly insert official accepted transfer grades into `grades` without Registrar approval metadata and audit.
- Registrar rejection must remain visible back to Enrollment so the Dean can correct/resubmit.

### Official Grades and Reports

Registrar owns official grades and grade governance.

Registrar areas:

| Area | Registrar responsibility |
| --- | --- |
| Grade sheet | Official class grade submission gate |
| Grade changes | Request, approval, rejection, audit |
| INC handling | Official temporary grade state; scholarship eligibility should treat INC as disqualifying until resolved |
| Scholar eligibility | Uses official grades/load, not draft Enrollment records |
| Reports | Uses official current/historical academic records |

Enrollment requirement:

- Do not revive legacy Enrollment grade encoding as an official source.
- If Enrollment needs grade reports, read Registrar official records.
- If an external grade source is added later, route it through Registrar approval/audit before it becomes official.

### Withdrawal and Student Number Release

Registrar owns withdrawal governance.

Relevant Registrar routes/services:

| Area | Registrar route/service |
| --- | --- |
| Drop subject | `/admin/withdrawals/drop-subject` |
| Clear load for shift | `/admin/withdrawals/clear-load-for-shift` |
| Withdraw student from school | `/admin/withdrawals/drop-student` |
| Release withdrawn student number | `StudentIdentityReleaseService` and `student_number_release_registry` |

Required withdrawn-state effects:

| State | Enrollment must enforce |
| --- | --- |
| `students.admission_status = 'WITHDRAWN'` | Block payment/finalization/enlistment/shift for this identity |
| `students.status = 'WITHDRAWN'` if present | Block active student workflows |
| `is_active = 0` if present | Block active student workflows |
| `enrollment_blocked = 1` if present | Block active student workflows |
| `student_number_release_registry.release_status = 'AVAILABLE'` | May be consumed for a new applicant only through issuance flow |
| `student_number_release_registry.release_status = 'REISSUED'` | Number has already been assigned to a new applicant |

Student number rule:

- A withdrawn student keeps historical archive identity.
- A released student number can be reused for a new enrollee only after Registrar archives the old identity and marks the number available.
- Enrollment must not continue using the old withdrawn live profile for new cashier/enlistment actions.

### Documents, Registration Forms, and Custody

| Flow | Owner | Registrar interaction |
| --- | --- | --- |
| Applicant documents | Admission | Registrar reads and trails after applicant becomes student |
| Applicant pre-reg PDF | Admission/Enrollment | Format reference for Registrar registration form alignment |
| Registration form history | Registrar | Registrar prints and records official reg-form events |
| COG/academic documents | Registrar | Uses official grades and academic history |
| Archive custody | Registrar | Tracks physical/digital document custody, releases, returns, scanning |

Required behavior:

- Admission should keep applicant-uploaded files accessible by reference number/student number.
- Registrar should not mutate admission uploads except by adding trail/custody events in Registrar-owned tables.
- Enrollment should not generate official Registrar registration forms from draft loads; it may generate pre-reg/payment documents.

## Enrollment Implementation Checklist

Use this checklist before calling the systems aligned.

| Check | Expected pass condition |
| --- | --- |
| Active term | Enrollment, Admission, and Registrar resolve the same current term from Registrar settings |
| Regular applicant | Admission qualified applicant becomes Enrollment pre-reg, pays, receives student number, receives committed load, appears in Registrar Student Manager with units |
| No zero-load enrollment | No new applicant can become `ENROLLED` with zero current-term committed subjects because pre-reg rows failed to resolve |
| Irregular applicant | Enrollment Dean/pre-advising can select eligible classes from real block sections, not dedicated IRREG regular-term sections |
| Dynamic max units | Enrollment and Registrar both compute load limits from assigned curriculum/year/semester |
| Prerequisites | Enrollment cannot pre-reg/finalize courses outside the student's curriculum eligibility unless Registrar-approved exception |
| Schedule/room/faculty constraints | Enrollment does not finalize rows that violate Registrar section/schedule truth |
| Fees | Ledger reflects Enrollment-owned fee policy and committed load, not stale withdrawn rows |
| Accreditation | Enrollment Dean request appears in Registrar pending queue; Registrar approval posts official grade; Enrollment sees approved/rejected result |
| School withdrawal | Withdrawn student is blocked from Enrollment cashier, finalization, enlistment, and shift |
| Reissue | Registrar-released student number can be claimed once and marked `REISSUED` by Enrollment issuance |
| Documents | Registrar Student Manager can view applicant documents and reg-form/document history after handoff |

## Suggested SQL Smoke Checks

Run these against `eacdb` during integration UAT.

```sql
SELECT setting_value
FROM system_settings
WHERE setting_key = 'CURRENT_ACADEMIC_TERM';
```

```sql
SELECT s.student_number, s.reference_number, s.program_code, s.admission_status,
       COUNT(se.enlistment_id) AS committed_load_count
FROM students s
LEFT JOIN student_enlistments se
  ON se.student_id = s.student_number
 AND UPPER(COALESCE(se.enlistment_status, 'COMMITTED')) = 'COMMITTED'
WHERE s.student_number = '<student-number>'
GROUP BY s.student_number, s.reference_number, s.program_code, s.admission_status;
```

```sql
SELECT se.student_id, c.course_code, cs.section_code, cs.term_id, se.enlistment_status
FROM student_enlistments se
JOIN class_sections cs ON cs.section_id = se.section_id
LEFT JOIN courses c ON c.course_id = se.course_id
WHERE se.student_id = '<student-number>'
ORDER BY cs.term_id, c.course_code;
```

```sql
SELECT request_id, student_number, course_code, status, requested_by_role,
       source_system, source_table, source_row_id
FROM transfer_credit_requests
WHERE student_number = '<student-number>'
ORDER BY request_id DESC;
```

```sql
SELECT student_number, admission_status, status, is_active, enrollment_blocked
FROM students
WHERE student_number = '<student-number>';
```

```sql
SELECT released_student_number, release_status, reissued_reference_number,
       reissued_student_number, reissued_at
FROM student_number_release_registry
ORDER BY updated_at DESC;
```

## Known Current Gaps To Watch

| Gap/risk | Why it matters | Expected resolution |
| --- | --- | --- |
| Enrollment inserts `ENROLLED` around student-number issuance | Can recreate "enrolled but no subjects" if finalization fails | Wrap issuance/finalization atomically or defer `ENROLLED` status until committed load exists |
| Older handoffs mention a missing bridge class | Causes agents/devs to chase non-existent `RegistrarTransferCreditRequestBridgeService` | Use the table contract or implement a new bridge explicitly |
| Fee language in old Registrar docs is transitional | Registrar used to own more fee logic | Treat Enrollment/Accounting as fee owner; Registrar reads readiness |
| Collation/index differences between MySQL/MariaDB | Can surface as SQL errors in cross-app joins | Standardize collations on shared integration tables |
| Withdrawal/ledger semantics need policy decision | A withdrawn class can be academic history but should not be active tuition by accident | Enrollment accounting must decide refund/charge policy, but active-load billing must exclude withdrawn rows |
| Special-class/summer/tutorial exceptions | These are legitimate non-block offerings | Flag them explicitly so regular irregular advising does not create fake IRREG sections |

## Recommended Communication Pattern

For now, keep the shared database integration but treat each write as owned by one system.

| Direction | Data | Pattern |
| --- | --- | --- |
| Registrar to Enrollment | Active term, curriculum, sections, schedules, constraints | Enrollment reads Registrar-owned tables |
| Admission to Enrollment | Applicant, documents, qualification | Enrollment reads applicant tables/reference number |
| Enrollment to Registrar | Student identity, committed load, ledger state, dean accreditation requests | Registrar reads shared tables and approves where required |
| Registrar to Enrollment | Accreditation decision, withdrawn/block state, released number registry | Enrollment reads Registrar-owned status/sync tables |

If APIs are added later, keep the same ownership:

| Proposed API area | Owner |
| --- | --- |
| Active term/catalog/offerings read API | Registrar |
| Pre-reg/finalization/payment API | Enrollment |
| Accreditation request submit API | Enrollment submits to Registrar |
| Accreditation decision API/webhook | Registrar returns approval/rejection |
| Withdrawal status API/webhook | Registrar publishes blocked/withdrawn/released state |

## References

| Reference | Why it matters |
| --- | --- |
| `D:\registrarCanon_canon\handoffNew\2026-06-30_REGISTRAR_DONE_PENDING_BLOCKED_MATRIX.md` | Current Registrar done/pending/blocked map |
| `D:\registrarCanon_canon\handoffNew\2026-06-30_REALIGNMENT_AND_WITHDRAWAL_SOLIDITY_NOTE.md` | Latest ownership and withdrawal solidity summary |
| `D:\registrarCanon_canon\handoffNew\2026-06-29_TRANSFER_CREDIT_DEAN_GATE_NOTE.md` | Registrar dean-gated accreditation notes; beware missing Enrollment bridge class name |
| `D:\registrarCanon_canon\handoffNew\2026-06-30_REGISTRAR_GRADE_GOVERNANCE_NOTE.md` | Registrar grade governance and official record notes |
| `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\curriculum\CreditGradeService.java` | Registrar accreditation approval gate |
| `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\withdrawal\WithdrawalService.java` | Registrar withdrawal governance |
| `D:\registrarCanon_canon\src\main\java\com\iuims\registrar\core\StudentIdentityReleaseService.java` | Registrar student-number release archive |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\ApplicantStudentNumberService.java` | Enrollment student-number issuance |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\PreRegEnrollmentFinalizeService.java` | Enrollment finalization into committed load |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\PreAdmissionDraftCreditService.java` | Enrollment pre-admission credit draft path |
| `D:\EnrollLatest\enrollment3\src\main\java\com\example\enrollment\service\ApplicantDeanAccreditationService.java` | Enrollment dean accreditation path |
| `D:\AdmitLatest\admission\src\main\java\com\example\enrollment\service\AdmissionRegistrarTermSyncService.java` | Admission active-term sync/read |

## Handoff Summary For Enrollment Team

Implement against Registrar as the academic authority, not as a duplicate Enrollment subsystem.

The safest integration target is:

1. Read Registrar active term, curriculum, courses, prerequisites, sections, schedules, rooms, and faculty.
2. Build applicant/irregular/continuing pre-reg in Enrollment using only real Registrar offerings.
3. Let Cashier/Accounting finalize payment and commit eligible rows into `student_enlistments` as `COMMITTED`.
4. Do not mark a student officially `ENROLLED` unless committed current-term load exists or the state is a documented non-load state such as withdrawn/shifted historical handling.
5. Submit accreditation from Enrollment Dean to Registrar with dean-origin source metadata.
6. Let Registrar approve/reject accreditation and post official grades.
7. Respect Registrar withdrawn/block/release state in every Enrollment workflow.
8. Keep fees and ledger in Enrollment, but expose enough readiness for Registrar to read.

If this contract is followed, the three systems remain aligned without duplicating authority or accidentally creating fake academic records.
