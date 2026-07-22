package com.iuims.registrar.support;
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

public final class GradeOutcomeSql {

    private GradeOutcomeSql() {}

    public static String passed(String alias) {
        return outcome(alias) + " = 'PASSED'";
    }

    public static String failedOrInc(String alias) {
        return outcome(alias) + " IN ('FAILED', 'INC')";
    }

    public static String failed(String alias) {
        return outcome(alias) + " = 'FAILED'";
    }

    public static String outcome(String alias) {
        return "UPPER(COALESCE(" + alias + ".registrar_final_remarks, " + alias + ".remarks, ''))";
    }
}





