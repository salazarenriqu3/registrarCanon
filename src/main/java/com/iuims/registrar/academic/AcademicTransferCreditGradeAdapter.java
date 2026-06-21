package com.iuims.registrar.academic;

import com.iuims.registrar.curriculum.TransferCreditGradePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AcademicTransferCreditGradeAdapter implements TransferCreditGradePort {

    private final GradeRepository gradeRepository;
    private final JdbcTemplate db;

    public AcademicTransferCreditGradeAdapter(GradeRepository gradeRepository, JdbcTemplate db) {
        this.gradeRepository = gradeRepository;
        this.db = db;
    }

    @Override
    public void saveTransferCredit(String studentNumber,
                                   int courseId,
                                   String studentName,
                                   String lockReason,
                                   BigDecimal numericGrade) {
        Grade grade = findExistingGrade(studentNumber, courseId).orElseGet(Grade::new);
        grade.setStudentId(studentNumber);
        grade.setCourseId(courseId);
        grade.setSectionId(null);
        grade.setStudentName(studentName);
        grade.setRemarks("Passed");
        grade.setStatus("SUBMITTED");
        grade.setGradeLockStatus("LOCKED");
        grade.setGradeLockReason(lockReason);

        if (numericGrade != null) {
            grade.setRegistrarFinalGrade(numericGrade);
            grade.setSemestralGrade(numericGrade);
            grade.setRegistrarFinalRemarks("Passed");
            grade.setRegistrarFinalizedAt(LocalDateTime.now());
        } else {
            grade.setRegistrarFinalGrade(null);
            grade.setSemestralGrade(null);
            grade.setRegistrarFinalRemarks(null);
            grade.setRegistrarFinalizedAt(null);
        }

        gradeRepository.save(grade);
    }

    private java.util.Optional<Grade> findExistingGrade(String studentNumber, int courseId) {
        for (Object key : gradeLookupKeys(studentNumber)) {
            List<Grade> rows = gradeRepository.findByStudentId(String.valueOf(key));
            for (Grade row : rows) {
                if (row.getCourseId() != null && row.getCourseId() == courseId) {
                    return java.util.Optional.of(row);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private List<Object> gradeLookupKeys(String studentNumber) {
        List<Object> keys = new ArrayList<>();
        keys.add(studentNumber);
        try {
            Integer userId = db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class, studentNumber);
            if (userId != null) {
                keys.add(String.valueOf(userId));
            }
        } catch (Exception ignored) {
        }
        return keys;
    }
}
