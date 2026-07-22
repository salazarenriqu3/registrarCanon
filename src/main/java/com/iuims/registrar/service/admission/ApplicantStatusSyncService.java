package com.iuims.registrar.service.admission;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Keeps {@code applicants.applicant_status} aligned with registrar {@code students.admission_status}
 * on the shared eacdb. Applicants always remain in {@code applicants}; {@code students} is added on admit.
 */
@Service
public class ApplicantStatusSyncService {

    private final JdbcTemplate db;

    public ApplicantStatusSyncService(JdbcTemplate db) {
        this.db = db;
    }

    /** Registrar admit: applicant stays in table, status becomes ADMITTED. */
    public int markAdmitted(String referenceNumber) {
        if (referenceNumber == null || referenceNumber.isBlank()) {
            return 0;
        }
        try {
            return db.update(
                    "UPDATE applicants SET applicant_status = 'ADMITTED', updated_at = NOW() "
                            + "WHERE reference_number = ? AND applicant_status = 'QUALIFIED FOR ENROLLMENT'",
                    referenceNumber.trim());
        } catch (Exception e) {
            return db.update(
                    "UPDATE applicants SET applicant_status = 'ADMITTED' WHERE reference_number = ?",
                    referenceNumber.trim());
        }
    }

    /**
     * First subject / enrollment finalize: promote ADMITTED → ENROLLED in applicants and sys_users.
     */
    public void markEnrolled(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return;
        }
        String sn = studentNumber.trim();
        try {
            db.update(
                    "UPDATE applicants a "
                            + "INNER JOIN students s ON s.reference_number = a.reference_number "
                            + "SET a.applicant_status = 'ENROLLED', a.updated_at = NOW() "
                            + "WHERE s.student_number = ? AND a.applicant_status = 'ADMITTED'",
                    sn);
        } catch (Exception e) {
            System.err.println("[ApplicantStatusSync] applicants ENROLLED sync failed for "
                    + sn + ": " + e.getMessage());
        }
        try {
            db.update(
                    "UPDATE sys_users SET admission_status = 'ENROLLED' "
                            + "WHERE username = ? AND (admission_status = 'ADMITTED' OR admission_status IS NULL)",
                    sn);
        } catch (Exception e) {
            System.err.println("[ApplicantStatusSync] sys_users ENROLLED sync failed for "
                    + sn + ": " + e.getMessage());
        }
    }

    public String findAdmissionStatusByReference(String referenceNumber) {
        if (referenceNumber == null || referenceNumber.isBlank()) {
            return null;
        }
        try {
            return db.queryForObject(
                    "SELECT admission_status FROM students WHERE reference_number = ? LIMIT 1",
                    String.class, referenceNumber.trim());
        } catch (Exception e) {
            return null;
        }
    }
}



