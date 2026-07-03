# 2026-07-01 Three-System Test Handoff

This is the current quick-start handoff for running and testing the latest local Admission, Enrollment, and Registrar projects together.

## Canon Paths

| System | Local path | Port | Role |
| --- | --- | --- | --- |
| Admission | `E:\AdmitLatest\admission` | `8081` | Applicant intake, applicant documents, qualification |
| Enrollment | `E:\EnrollLatest\enrollment3` | `8082` | Pre-registration, cashier/accounting, student-number issuance, enrollment finalization |
| Registrar | `E:\registrarCanon_canon` | `8083` | Academic master data, student records, grades, withdrawal, documents, official academic gates |

All three systems use the shared MariaDB database `eacdb` at `127.0.0.1:3306`, username `root`, empty password.

## Open First

1. `JULY_1_ONE_PAGE_BRIEF.md`
1. `RUNBOOK_THREE_APPS.md`
1. `SQL_FEED_AND_VERIFY.md`
1. `TESTING_STORYBOARD.md`
1. `NEXT_AGENT_HANDOFF.md`

## Current Runtime Notes

- Admission was started successfully on `http://localhost:8081/`.
- Admission needed an explicit Hibernate MySQL dialect in `application.properties` and `application-local.properties`.
- Registrar remains the canonical source for active term, curriculum, sections, schedules, rooms, withdrawal governance, document custody, grade records, and LEC/LAB component handling.
- Registrar finance reads now refresh the current-term assessment rows before computing the mini-ledger snapshot or full student ledger, so the profile summary and `/admin/scholar-ledger` page stay aligned.
- Enrollment owns cashier/accounting, pre-registration, student-number issuance, and final enrollment commit.
- Admission owns applicant intake and documents, then hands applicant context forward.

## Login Hints

| System | Account | Password |
| --- | --- | --- |
| Registrar | `admin` | `1234` |
| Registrar | `registrar.main` | `1234` |
| Registrar | `registrar.records` | `1234` |
| Registrar | `registrar.scholar` | `1234` |
| Registrar | `registrar.schedule` | `1234` |
| Admission | `admin-adms` | `adminadms` |
| Admission | `encoder-adms` | `encoderadms` |
| Enrollment | `admin` | `admin123` |

## Short Rule

Use Registrar setup as the database baseline. Admission and Enrollment should then run against that same `eacdb`; do not let either app replace the registrar-owned academic schema.
