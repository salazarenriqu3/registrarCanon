# Registrar Dependency Matrix

Date: 2026-06-27
Workspace: `D:\registrarCanon_canon`

## 1. Purpose

This document defines the registrar ownership graph in canonical form.

It answers three questions:

1. Which entity owns each piece of data.
2. Which entities may reference that data.
3. Which parts of the system are term-scoped versus master data.

Use this as the structural map for future work on curriculum, scheduling, sections, room monitoring, faculty load, student load, shifting, withdrawal, scholarship, and grading.

## 2. Core rule

- One entity owns the record.
- Other entities may reference it.
- The active academic term overlays the whole graph and determines what is currently enforceable.

Ownership and reference are not the same thing.

## 3. Canonical hierarchy

The registrar stack should be understood in this order:

`Department -> Program -> Curriculum -> Curriculum Course Placement -> Section -> Schedule -> Room`

Student-side operational flow:

`Student -> Program Assignment -> Current Curriculum Assignment -> Enrollment -> Grade / History`

The active term applies to both chains as the current runtime lens.

## 4. Dependency matrix

| Owner | Source of Truth | Can Reference | Must Not Own | Term Scope |
|---|---|---|---|---|
| Active Term | `system_settings.CURRENT_ACADEMIC_TERM`, academic term tables | all operational modules | master academic identity | global runtime lens |
| Department | academic master data | programs, faculty, shared course context | sections, schedules, student load | mostly master data |
| Program | program master data | department, curricula, students | room assignments, schedules | versioned through curriculum |
| Curriculum | curriculum templates and lifecycle records | program, course catalog, student assignment | rooms, faculty, section rows directly | versioned and term-aware |
| Course | shared course catalog | prerequisites, curricula, sections, grades | program ownership, room ownership, student ownership | master data reused across terms |
| Curriculum Course Placement | curriculum placement rows | curriculum, course, year level, semester | scheduling directly, enrollment directly | strongly term-meaningful |
| Section | class offering / block offering | curriculum placement, term, course, capacity | catalog ownership, program ownership | term-scoped |
| Schedule | meeting-time rows for a section | section, room, faculty, day, time | course master ownership | term-scoped |
| Room | physical room master | schedules, room monitoring | curriculum, student, section ownership | master data plus term usage |
| Faculty | faculty master data | schedules, load checks, department | curriculum ownership, room ownership | master data plus term load |
| Student | student master record | program, curriculum assignment, enrollments, grades | section ownership, room ownership | persistent record with term state |
| Student Curriculum Assignment | explicit student-curriculum mapping | student, curriculum, program | legacy global load fallback | source of truth for live load |
| Enrollment / Add Subjects | transactional student-term action | student, curriculum assignment, section, prerequisites, conflicts, load | master curriculum ownership | term-scoped |
| Withdrawal / Drop | transactional student-term action | student, section, term, academic status | curriculum master data | term-scoped |
| Grades | official academic record | student, section, course, term, faculty context | curriculum builder ownership | permanent record with term origin |
| Transfer Credit / TOR | academic history / crediting record | student, course, curriculum, program | schedule ownership | history record affecting eligibility |
| Fees / Term Readiness | enrollment / cashier-owned finance data | term, student, program, load context | registrar fee authoring | term-scoped, read-only in registrar |
| Scholarship Eligibility | academic rules engine | student, curriculum assignment, grades, term, standing | fee ownership | term-scoped and curriculum-aware |
| Slot Monitoring | section capacity monitoring | section, term, committed/staged counts | room master ownership | term-scoped operational view |
| Room Monitoring | physical room monitoring | room, term, schedule, section, faculty gaps | curriculum master ownership | term-scoped operational view |

## 5. What the active term changes

The active term does not own master data.

It decides which version of the data is currently in force:

- current curriculum selection
- valid section offerings
- live schedules
- room occupancy and conflict checks
- faculty load enforcement
- student max-load enforcement
- scholarship eligibility checks
- fee readiness checks
- withdrawal and shifting behavior
- add-subject eligibility

## 6. Source-of-truth rules

### 6.1 Curriculum and load

- Current student load must resolve from the explicit current curriculum assignment.
- Year-level and semester-specific curriculum load is the live rule.
- Legacy global load caps must not override live curriculum load.
- Missing curriculum assignment or missing curriculum rows is a data error, not a cue to fall back to old defaults.

### 6.2 Scheduling and rooming

- Sections and schedules are term-scoped operational records.
- Every schedule save must reference a concrete room.
- Same-term room conflicts, faculty conflicts, and same-section overlaps must be blocked.
- Room monitoring is a separate operational view from slot monitoring.

### 6.3 Student movement

- Program shifting must preserve or replace the student's explicit current curriculum assignment.
- A student may temporarily have zero current-term load after shifting or withdrawal cleanup.
- A zero current-term load must not hide Add Subjects when the explicit curriculum assignment still exists.

### 6.4 Academic authority boundaries

- Registrar owns academic master data, official academic posting, and schedule integrity.
- Enrollment3 / Cashier owns irregular and continuing draft generation, fee authoring, payment gates, and finalization.
- Admission owns applicant intake and qualification.
- Registrar reads readiness and official finance state; it does not own fee authoring.

## 7. How to use this matrix

Use this document when deciding whether a feature belongs to:

- master data
- term-scoped operations
- downstream academic records
- financial readiness
- operational monitoring

If a change alters the ownership graph, update this document and the related handoff notes in the same pass.

## 8. Practical reading order

For the current canon, read these together:

1. `START_HERE_NEW_PC_HANDOFF.md`
2. `FINAL_SYSTEM_DOCUMENTATION_20260618.md`
3. `FINAL_DEMO_AND_TEST_MANUAL_20260618.md`
4. `CURRENT_STATE_MAP.md`
5. `REGISTRAR_SYSTEM_SPECIFICATION_20260625.md`
6. This dependency matrix

