package com.iuims.registrar.service.support;
import com.iuims.registrar.entity.Student;
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
 * Shared {@code student_enlistments.enlistment_status} semantics with enrollment3.
 * STAGED = cashier preview before finalize; COMMITTED = official registration.
 */
@Service
public class EnlistmentSchemaService {

    public enum Scope {
        /** Staged + committed — capacity / duplicate checks during cashier enlistment. */
        PREVIEW,
        /** Official load only - registrar Student Profile, COR, fee assessment. */
        COMMITTED_ONLY
    }

    private final JdbcTemplate db;
    private volatile Boolean enlistmentStatusColumn;

    public EnlistmentSchemaService(JdbcTemplate db) {
        this.db = db;
    }

    public boolean hasEnlistmentStatusColumn() {
        if (enlistmentStatusColumn != null) {
            return enlistmentStatusColumn;
        }
        synchronized (this) {
            if (enlistmentStatusColumn != null) {
                return enlistmentStatusColumn;
            }
            try {
                Integer count = db.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_enlistments' "
                        + "AND COLUMN_NAME = 'enlistment_status'",
                    Integer.class);
                enlistmentStatusColumn = count != null && count > 0;
            } catch (Exception e) {
                enlistmentStatusColumn = false;
            }
            return enlistmentStatusColumn;
        }
    }

    /** Filter when the enlistment table has no SQL alias. */
    public String enlistmentStatusFilter(Scope scope) {
        return enlistmentStatusFilter(scope, "student_enlistments");
    }

    /** SQL fragment, e.g. {@code AND se.enlistment_status = 'COMMITTED'} */
    public String enlistmentStatusFilter(Scope scope, String enlistmentAlias) {
        if (!hasEnlistmentStatusColumn() || scope == null) {
            return "";
        }
        String col = enlistmentAlias + ".enlistment_status";
        return switch (scope) {
            case PREVIEW -> " AND (" + col + " IN ('STAGED','COMMITTED'))";
            case COMMITTED_ONLY -> " AND (" + col + " = 'COMMITTED')";
        };
    }

    public String committedStatusValue() {
        return "COMMITTED";
    }

    public String stagedStatusValue() {
        return "STAGED";
    }
}





