package com.iuims.registrar.config;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String sharedUploadPath;

    public WebConfig(@Value("${registrar.shared-upload-dir:${user.home}/enrollment_uploads}") String sharedUploadPath) {
        this.sharedUploadPath = sharedUploadPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + ensureTrailingSlash(sharedUploadPath));
    }

    private String ensureTrailingSlash(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}


