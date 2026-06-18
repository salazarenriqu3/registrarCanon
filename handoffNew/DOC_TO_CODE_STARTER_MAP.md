# Interview Doc To Current Registrar Map

Last updated: 2026-06-17

## Purpose

This file converts the interview/spec document into a practical starter map for the current registrar system.

Scope note:

- The old admission/pre-reg/dean bridge is retired and intentionally excluded.
- This map only covers the registrar-side academic and post-enrollment work that is still useful.

## What The Document Gives Us

### 1. System boundaries and timing

Useful inputs:

- Academic term configuration
- Enrollment window control
- Class scheduling window control
- Grading lock windows
- Add/drop deadlines
- Section offering approval rules

Why it matters:

- These are the policy rails that keep registrar behavior consistent across terms.

### 2. Student profile canon

Useful inputs:

- Student Profile replaces the older Student Manager naming
- Profile keeps personal info, active academic status, and curriculum year
- Registrar can update curriculum year
- Student deficiency tagging is part of the profile
- Account-block behavior is part of the profile
- Old vs new curriculum history must be preserved

Why it matters:

- This is the main academic identity model that all downstream modules depend on.

### 3. Class scheduling and section management

Useful inputs:

- Use `Section`, not `Group`
- Room assignment is mandatory
- Block sections need explicit control
- Subject name should appear alongside subject code
- Slot monitor should show enrolled counts
- Schedule changes affect faculty loading
- Course taxonomy should distinguish regular, tutorial, and petition subjects

Why it matters:

- This is the registrar’s operational backbone for load, sections, and rooming.

### 4. Enrollment alterations

Useful inputs:

- Use `Withdraw`, not `Drop`
- Withdrawal needs reason tracking and reporting
- Program shift / section shift needs a workflow
- Registration document history is useful
- Reprint of the registration form should follow approved changes
- Add/drop deadlines affect tuition and lock-out behavior

Why it matters:

- This is the post-enrollment change pipeline that still belongs in registrar scope.

### 5. Student records and evaluation

Useful inputs:

- Curriculum evaluation matrix
- Color-coded pass/fail/in-progress indicators
- TOR engine
- Copy of Grades generation
- Dean evaluation access
- Registrar edit rights when student number exists
- Student grade file should be protected from dean access

Why it matters:

- This is the long-term academic record layer.

### 6. Scholarship and grading

Useful inputs:

- Scholarship catalog and criteria
- GWA thresholds
- Minimum units
- Registrar endorsement, president approval, cashier posting
- Grading periods: prelim, midterm, finals
- Class standing and exam components
- Faculty access windows and approvals

Why it matters:

- These are the rules behind academic status, awards, and grade integrity.

## Current Registrar Alignment

Already reflected in the current registrar codebase:

- Student profile / student manager surfaces
- Curriculum builder and curriculum history
- Class scheduling and section control
- Withdrawal queue and reports
- Scholarship admin and scholarship cashier
- Grade approvals and faculty encoding
- TOR / COG printing
- Document trail and reg form history

This means the document mostly confirms and sharpens existing registrar behavior instead of introducing a brand-new system shape.

## Gaps And Clarifications

These are the parts from the document that still need policy or implementation decisions:

- Exact ownership of term-fee behavior versus accounting
- Exact scope of curriculum-year override
- Exact rules for scholarship posting and discount structure
- Exact grading policy thresholds for promotion / hold / INC handling
- Exact add/drop charge rules and timing
- Whether every academic change must write a registration document trail row

## Priority Starter Checklist

1. Lock down the academic policy constants.
   - active term
   - enrollment window
   - class scheduling window
   - grading lock window
   - add/drop deadline

2. Normalize student profile behavior.
   - preserve old/new curriculum history
   - keep deficiency and block tagging visible
   - keep curriculum year editable only where intended

3. Keep scheduling strict.
   - section naming
   - room assignment
   - faculty-loading sensitivity
   - block vs irregular behavior

4. Tighten change workflows.
   - withdrawals
   - program shifts
   - registration document history

5. Preserve records and outputs.
   - TOR
   - COG
   - evaluation
   - grading approvals

6. Keep scholarship and grading policy explicit.
   - scholarship criteria
   - grade windows
   - approval flow

## Practical Next Step

If we continue from this doc, the best order is:

1. confirm which of the gaps are already implemented and which are only documented
2. write a short code-to-doc delta list
3. turn the delta list into the next implementation checklist

Implementation plan:

- `IMPLEMENTATION_PLAN_REGISTRAR_ACADEMIC_REFINEMENT_20260617.md`
