# 2026-07-02 Three-System Realignment Runtime Pass

Purpose:

- Execute a focused runtime pass after the latest registrar/enrollment realignment work.
- Confirm which withdrawn-student and identity-bridge behaviors are now solid across Registrar, Enrollment, and Admission.
- Record the remaining defects before the next testing loop.

## Canon apps used

- Registrar: `E:\registrarCanon_canon`
- Enrollment (latest canon used for recheck): `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Enrollmoko1\enrollment3`
- Admission (latest canon used for recheck): `D:\downloads\Latest-20260701T151156Z-3-001\Latest\Admitmoko1\admission`

## Identities used

Active bridge case:

- reference number: `DEMO-SANTOS-001`
- admission status email: `maria.santos@demo.eac.edu.ph`
- student number: `2026-1001`

Withdrawn archive case:

- archive key / current withdrawn identity: `ARCH-807E99E49B8B`
- released former live student number: `SCH-UAT-LOWUNITS`

## What passed

### Registrar

- Active Student Profile for `2026-1001` still loads.
- `Open Enrollment System (External)` now deep-links correctly to:
  - `http://localhost:8082/admin/cashier?keyword=2026-1001`
- Withdrawn Student Profile for `ARCH-807E99E49B8B` still loads as history.
- Withdrawn profile still shows:
  - withdrawal history
  - archive & custody tracking
  - outstanding-balance document-release hold notice
- Withdrawn profile no longer exposes active actions that should stay blocked:
  - no external enrollment action
  - no program-shift action
- Official document-release routes were runtime-checked and remained blocked:
  - `print-cor`
  - `print-cog`

### Enrollment

- Withdrawn ledger for `ARCH-807E99E49B8B` now behaves as historical-only:
  - shows the historical warning
  - shows plain `WITHDRAWN`
  - does not render the scholarship update form
- Walk-in payment page for `ARCH-807E99E49B8B` now renders the withdrawn lock state:
  - payment fields are disabled
  - finalization/payment action is blocked in the UI
- Released former live number `SCH-UAT-LOWUNITS` did **not** resolve as the withdrawn student on Enrollment walk-in.
- Active cashier path for `2026-1001` still loads the normal assessment/cashier surface.

### Admission

- Public status lookup for `DEMO-SANTOS-001` still works.
- Status page still shows:
  - reference number
  - applicant status
  - bridged student number `2026-1001`
- Admin dashboard login still works with:
  - `admin-adms / adminadms`
- Admin dashboard search still shows the target applicant in the current admission queue context.

## Expanded registrar bridge sweep

This pass was widened beyond the withdrawn-only check so we could verify the registrar surfaces that actively depend on Admission or Enrollment data.

### Registrar pages that load correctly against shared Admission / Enrollment data

- `GET /registrar/admin/admission-acceptance?refNo=DEMO-SANTOS-001`
  - loads successfully
  - shows the regular-applicant ownership note that regular pre-registration, sectioning, payment, and student-number issuance remain in Admission + Enrollment/Cashier
  - detects cashier-side payment presence for the applicant handoff
- `GET /registrar/admin/student-manager?username=2026-1001`
  - loads successfully
  - shows:
    - Admission Snapshot
    - Applicant Documents
    - registrar profile / load / alerts panels
- `GET /registrar/admin/document-trail?query=2026-1001`
  - loads successfully
- `GET /registrar/admin/reg-form-history?studentNumber=2026-1001`
  - loads successfully
  - export route is present
- `GET /registrar/admin/scholar-ledger?keyword=2026-1001`
  - loads successfully
  - shows the student ledger surface for `2026-1001`
- `GET /registrar/admin/print-cor?username=2026-1001`
  - returns `200`
  - returns PDF content:
    - `Content-Type: application/pdf`
    - `Content-Disposition: inline; filename="registration-form-2026-1001.pdf"`
- `GET /registrar/admin/print-cog?username=2026-1001`
  - returns `200`
  - renders the Certificate of Grades print page for Maria Santos / `2026-1001`
- `GET /registrar/admin/slot-monitoring`
  - returns `200`
  - page text confirms the intended mixed-data view:
    - committed enrollment
    - staged pre-registration
    - section capacity
- `GET /registrar/admin/room-monitoring`
  - returns `200`
  - room-usage / room-conflict monitoring page is up

### Enrollment pages that still align with registrar for the active bridge case

- `GET /admin/cashier?keyword=2026-1001`
  - returns `200`
  - shows the cashier terminal for Maria / `2026-1001`
- `GET /admin/ledger?keyword=2026-1001`
  - returns `200`
  - shows the ledger for Maria / `2026-1001`

### Admission pages that still align with registrar for the active bridge case

- Public status lookup:
  - `GET /status?searchRef=DEMO-SANTOS-001&email=maria.santos@demo.eac.edu.ph`
  - returns `200`
  - shows:
    - applicant name
    - reference number
    - `QUALIFIED FOR ENROLLMENT`
    - bridged student number `2026-1001`
- Admin queue search:
  - `GET /admin/dashboard?keyword=DEMO-SANTOS-001`
  - returns `200`
  - still shows the applicant inside the current admission queue context

## Defects found

### 1. Enrollment cashier withdrawn lookup still breaks

Route:

- `GET /admin/cashier?keyword=ARCH-807E99E49B8B`

Observed result:

- returns `HTTP 500`

Why this matters:

- ledger already treats the withdrawn archive identity correctly as read-only history
- walk-in already treats the withdrawn archive identity correctly as blocked
- cashier should do the same, but instead it crashes on the archive-key case

Practical reading:

- this is a real cross-system alignment defect
- withdrawn students are still not fully safe on the cashier surface until this is fixed

### 2. Registrar still accepts the released former live number as a historical alias

Route:

- `GET /registrar/admin/student-manager?username=SCH-UAT-LOWUNITS`

Observed result:

- resolves to the withdrawn historical profile for Liam Low Units
- profile displays under archive key `ARCH-807E99E49B8B`

Why this matters:

- it may be acceptable as a historical convenience alias today
- but it is risky for future student-number reuse, because the released former live number is supposed to become reusable for a new enrollee

Practical reading:

- this is at least a policy seam, and likely a defect if we want strict separation between:
  - archive lookup keys
  - released/reissuable live student numbers

### 3. Registrar admission-document actions are broken at runtime

Routes tested from the Student Profile document panel:

- `GET /registrar/admin/student-manager/admission-document?studentNumber=2026-1001&documentKey=legacy:form138&mode=view`
- `GET /registrar/admin/student-manager/admission-document?studentNumber=2026-1001&documentKey=legacy:form138&mode=download`

Observed result:

- both return `404`

What the registrar UI already proves:

- Student Profile correctly shows document metadata rows for Maria:
  - Form 138 / Report Card
  - Good Moral Certificate
  - PSA Birth Certificate
  - ID Picture

What the shared DB proves:

- `applicants` still contains legacy file metadata for `DEMO-SANTOS-001`:
  - `DEMO-SANTOS-001-form138.svg`
  - `DEMO-SANTOS-001-good-moral.svg`
  - `DEMO-SANTOS-001-psa-birth-cert.svg`
  - `DEMO-SANTOS-001-id-picture.svg`

What the filesystem check proved:

- registrar resolves admission files from `${user.home}/AdmissionEAC/uploads`
- those expected files are not present there in the current runtime environment

Practical reading:

- this is a real registrar/admission bridge defect
- the metadata bridge is alive
- the file-storage handoff is not
- likely causes:
  - seeded demo applicant rows point to filenames whose actual files were not copied into the shared upload directory
  - or the latest Admission runtime stored the files elsewhere before this registrar pass

### 4. Registrar history pages currently prefer archive key links even for an active student

Pages:

- `GET /registrar/admin/document-trail?query=2026-1001`
- `GET /registrar/admin/reg-form-history?studentNumber=2026-1001`

Observed result:

- active events for Maria show `student_number = 2026-1001`
- but the `Open Profile` action often points to:
  - `username=ARCH-9287C82268F4`
  - instead of `username=2026-1001`

Code reading confirms this is intentional template behavior today:

- history templates explicitly prefer:
  - `archive_key` when present
  - otherwise `student_number`

Practical reading:

- not data corruption
- but still a real presentation / navigation seam
- it makes active-student history look archival first, even though the student is still active

### 5. Registrar `print-tor` is not clean at runtime

Route:

- `GET /registrar/admin/print-tor?username=2026-1001`

Observed result:

- the response does not complete normally
- client sees a premature connection close / chunked-encoding failure

Important nuance:

- document trail now contains fresh `TOR / PRINTED` events for Maria during this pass
- that means the controller path is being entered and the event is being recorded before the response dies

Practical reading:

- this is a probable registrar-only defect on the TOR render path
- likely in the template/render phase or response streaming phase
- it is not yet proven to be an Admission or Enrollment contract issue

## Non-defects / clarifications

- Admission admin dashboard search did not visibly show `2026-1001` in the broad list snapshot we captured, but the public status tracker did show the bridged student number correctly.
- Withdrawn Student Profile did not show a `Release Student Number` action during this pass because the tested withdrawn case already has a released-number registry row.
- History pages opening Maria through `ARCH-9287C82268F4` still land on Maria's active profile. The issue is the archive-first navigation preference, not a wrong-person misroute.

## Not yet mutation-tested in this pass

These surfaces were verified as readable / reachable, but their write path was not exercised in this runtime pass:

- registrar-side admission validation action from the read-only handoff screen
- actual enrollment cashier payment posting during this pass
- actual admission upload submission during this pass
- registrar document-trail generation for successful admission document view/download
- registrar TOR output after a clean render fix

## Recommended next fix order

1. Fix Enrollment cashier archive-key handling so withdrawn lookup returns a blocked/read-only state instead of `HTTP 500`.
2. Decide and enforce the canonical behavior for released former live student numbers on Registrar lookup:
   - either stop resolving them once released
   - or keep historical aliasing only until a number is actually reissued, then hard-switch to archive-key-only lookup
3. Fix registrar admission-document view/download so Student Profile can actually open the admission files it already advertises.
4. Stabilize registrar `print-tor` so TOR output is demo-safe alongside COR and COG.
5. Decide whether registrar history pages should keep archive-first navigation for active students or fall back to live student number first.
6. Re-run this same pass after those items before moving deeper into broader three-system UAT.

---

## 2026-07-02 Post-Fix Runtime Recheck

This section records the immediate live recheck after the same-day bridge patches were applied and the latest D Admission + Enrollment copies were restarted on:

- Admission: `http://localhost:8081`
- Enrollment: `http://localhost:8082`
- Registrar: `http://localhost:8083/registrar`

### Recheck result summary

- Admission startup regression in the newer D copy was real and reproducible.
- Cause: the newer Admission copy no longer pinned `spring.jpa.database-platform` / `hibernate.dialect`, so Hibernate probed MySQL-only metadata and failed on MariaDB `INFORMATION_SCHEMA.KEYWORDS.RESERVED`.
- Fix applied in the D Admission canon:
  - `src/main/resources/application.properties`
  - `src/main/resources/application-local.properties`
- Outcome: Admission now boots successfully on `8081`.

### Items now verified closed

#### 1. Enrollment cashier withdrawn archive lookup

Route:

- `GET /admin/cashier?keyword=ARCH-807E99E49B8B`

Current result:

- route now returns `200`
- page renders the withdrawn lock state
- historical archive key is shown
- transactional cashier actions are blocked from the main surface

Visible confirmation strings:

- `WITHDRAWN STUDENT LOCK`
- `ARCH-807E99E49B8B`

#### 2. Registrar released former live number alias

Route:

- `GET /registrar/admin/student-manager?username=SCH-UAT-LOWUNITS`

Current result:

- released former live number no longer resolves the withdrawn profile
- historical access is now archive-key-first as intended

Practical meaning:

- released student numbers are no longer acting as live historical aliases on Registrar lookup
- this is the safer base behavior for eventual student-number reuse

#### 3. Registrar admission-document view/download bridge

Route tested:

- `GET /registrar/admin/student-manager/admission-document?studentNumber=2026-1001&documentKey=legacy:form138&mode=view`

Current result:

- route now returns `200`
- content type returned was `image/svg+xml`

Practical meaning:

- the registrar document panel is no longer advertising dead admission files for this bridge case
- the document-storage handoff is functioning again for the seeded demo files

#### 4. Registrar history links for active students

Routes:

- `GET /registrar/admin/document-trail?query=2026-1001`
- `GET /registrar/admin/reg-form-history?studentNumber=2026-1001`

Current result:

- `Open Profile` links now point back to:
  - `username=2026-1001`
- active history is no longer archive-first for this case

#### 5. Registrar `print-tor`

Route:

- `GET /registrar/admin/print-tor?username=2026-1001`

Current result:

- route now returns a complete HTML response
- response contains:
  - `Transcript of Records`
  - `TRANSCRIPT OF RECORDS`
  - `Maria Reyes Santos`
  - `2026-1001`

Practical meaning:

- the earlier chunked-response failure is no longer reproducing on this route
- TOR is now demo-safe at least for the active bridge case

### Additional recheck findings

#### Registrar active profile still loads

Route:

- `GET /registrar/admin/student-manager?username=2026-1001`

Current result:

- page returns `200`
- rendered student identity is:
  - `Maria Reyes Santos`

#### Enrollment lookup did not mutate tracked identities during this recheck

Database rows were checked before and after live cashier lookups for:

- `2026-1001`
- `ARCH-807E99E49B8B`

Observed result:

- no row mutation was observed from the lookup itself during this pass

Important nuance:

- `2026-1001` already starts this pass in `sys_users` as:
  - `admission_status = PENDING`
  - `enrollment_status_type = REGULAR`
- so if the UI still looks semantically mixed on Enrollment, that is currently a pre-existing data-state inconsistency for this demo identity, not a fresh mutation caused by merely opening cashier in this pass

### Current remaining concern after the recheck

The major same-day bridge defects from the first runtime note were closed in this recheck. The remaining issue to investigate is no longer the withdrawn-lookup crash itself, but the semantic inconsistency of demo data such as `2026-1001`, where shared status fields do not yet cleanly express the student's real lifecycle state across all three systems.
