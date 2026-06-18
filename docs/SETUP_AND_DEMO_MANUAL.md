# Registrar System: Setup & Demo Manual

This document provides a streamlined, step-by-step guide to setting up the Registrar application from scratch and demonstrating its new business logic capabilities on a fresh PC.

## 1. Prerequisites

Ensure you have the following installed and available in your system's PATH:
- **Java 17+**
- **Maven 3.6+** (`mvn`)
- **MySQL 8.0+** (Default port `3306`, root user with no password)
- **PowerShell** (for Windows users executing the automated script)

---

## 2. One-Click Sandbox Setup (Recommended for Demos)

To perfectly demonstrate the new Term Fees logic and Admissions pipeline in absolute isolation—without the noise of 100,000 legacy rows—we use the **Sandbox Environment**. This provides a pure, minimalistic dataset with zero legacy pollution.

1. Open PowerShell and navigate to the project root directory:
   ```powershell
   cd path\to\projects\registrar
   ```
2. Execute the automation script:
   ```powershell
   .\run_sandbox_demo.ps1
   ```

**What the script does:**
- Drops any existing `registrar_sandbox` database to prevent data pollution.
- Recreates `registrar_sandbox` and loads the highly-optimized, wide-table schema from `db/manual_tests/01_MANUAL_SCHEMA_CORE.sql`.
- Seeds essential test data (Term 10 rates, Fallback rates, basic students) from `db/manual_tests/02_MANUAL_SEED_TEST_DATA.sql`.
- Injects the Sandbox Database URL and automatically starts the Spring Boot backend (`mvn spring-boot:run`).

3. Once the server says `Started RegistrarApplication`, open your browser and navigate to the Admin Dashboard: 
   [http://localhost:8083/registrar](http://localhost:8083/registrar)
   **(Login: admin / 1234)**

For the admission-profile bridge to show applicant documents correctly, the Registrar app should be pointed at the same admission upload root as the Admission system. If the files live in a custom folder, set `APP_UPLOAD_DIR` before starting Registrar so the Student Profile page can resolve the linked uploads.

---

## 3. Demo Walkthroughs

Use the following flows to verify and demonstrate the system's new UX and core capabilities.

### Demo A: The "Locked" Dynamic Semester UI
This demonstrates how the new UI prevents users from configuring impossible academic term combinations.

1. Navigate to the **Term Fees Admin** page (`/admin/term-fees`).
2. Under "Select scope", choose **A.Y. 2025-2026 - 1st Semester** from the Academic Term dropdown.
3. **Notice:** The **Semester** dropdown immediately switches to "1st" and visually **locks**. You can no longer mistakenly attempt to set "2nd semester fees" inside a "1st semester term".
4. Now, change the Academic Term dropdown to **-- Global Fallback Templates --**.
5. **Notice:** The **Semester** dropdown immediately **unlocks**, allowing you to toggle between editing the 1st or 2nd semester fallback templates (since fallbacks are term-agnostic).

### Demo B: Granular vs. Bulk Term Fee Import
This demonstrates the new strict decoupling logic where fees are natively copied to upcoming terms without legacy table bloat.

1. Ensure the top "Select scope" is set to: **A.Y. 2025-2026 - 2nd Semester** (Term 11), **Program:** BSIT, **Year Level:** Y1. Click **Load**.
2. Notice the fee inputs below are mostly blank/zero because Term 11 hasn't been configured yet.
3. Scroll down to **Explicit Term Import**.
4. Set the **Source Term** to **A.Y. 2025-2026 - 1st Semester** (Term 10).
5. **Scoped Import:** Click the green **Import from Source** button directly.
   - *Result:* The system will strictly copy *only* the fees for BSIT, Year 1. It acts like a surgical scalpel.
6. **Bulk Import:** Now, check the box labeled **Bulk import for ALL programs & years**, then click **Import from Source** again.
   - *Result:* The system bypasses your scope selection and duplicates the *entire university's* fee schedule from Term 10 into Term 11 in one click.

### Demo C: Global Fallback Templates (Failsafe)
This demonstrates how the system protects itself if an admin forgets to run the import scripts for a new term.

1. Under "Select scope", choose **-- Global Fallback Templates --**.
2. Set the Program to BSIT, Year Level to Y1, Semester to 1st.
3. Observe the base figures (e.g., Tuition: `1500`, Library: `2000`). These are universally mapped as `term_id = NULL` in the database.
4. If an admin ever clicks "Prepare selected term" on an empty future term, the system automatically hunts down these exact fallback figures to ensure the cashiering module never crashes during enrollment.

### Demo D: Admission snapshot and applicant documents
This demonstrates the registrar-side read-only bridge to the Admission system.

1. Open `/admin/student-manager` and search for a student number that is linked to an applicant record in Admission.
2. Open the profile and confirm the new **Admission Snapshot** card appears above the editable registrar profile.
3. Confirm the new **Applicant Documents** card shows the submitted files and their current status.
4. Click a document with a **View** action and confirm it opens inline without exposing registrar write controls.
5. Confirm the editable registrar profile still only changes registrar-owned fields and does not mutate admission-owned applicant data.
6. If the page redirects to `/login`, sign in again and return to Student Profile. A redirect here usually means the session expired, not that the feature is broken.

---

## 4. Troubleshooting on a Fresh PC

- **MySQL Connection Errors**: If `run_sandbox_demo.ps1` fails with connection refused, ensure your MySQL service is running on `localhost:3306`. If your root user has a password, edit lines 25, 28, and 31 in `run_sandbox_demo.ps1` to include it (`--password=yourpassword`).
- **Port Conflicts**: If the app fails to start because port `8083` is in use, kill the blocking process or alter `server.port` in `src/main/resources/application.properties`.
- **Login redirect on admin pages**: The browser session may have expired. Sign in again with the local demo account and retry the page instead of assuming the route is missing.
