package com.iuims.registrar.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgramFeeSettingRepository extends JpaRepository<ProgramFeeSetting, Integer> {

    Optional<ProgramFeeSetting> findFirstByProgramIdAndYearLevelAndSemesterNumberAndTermIdAndIsActiveTrueOrderByFeeSettingIdDesc(
        int programId, int yearLevel, int semester, Integer termId);

    Optional<ProgramFeeSetting> findFirstByProgramIdAndYearLevelAndSemesterNumberAndTermIdIsNullAndIsActiveTrueOrderByFeeSettingIdDesc(
        int programId, int yearLevel, int semester);

    default Optional<ProgramFeeSetting> findActiveForExactScope(
            int programId, int yearLevel, int semester, Integer termId) {
        if (termId == null) {
            return findFirstByProgramIdAndYearLevelAndSemesterNumberAndTermIdIsNullAndIsActiveTrueOrderByFeeSettingIdDesc(
                programId, yearLevel, semester);
        }
        return findFirstByProgramIdAndYearLevelAndSemesterNumberAndTermIdAndIsActiveTrueOrderByFeeSettingIdDesc(
            programId, yearLevel, semester, termId);
    }

    @Query(value = "SELECT * FROM program_fee_settings WHERE program_id = ?1 AND year_level = ?2 AND semester_number = ?3 AND is_active = 1 " +
           "AND (term_id = ?4 OR term_id IS NULL) " +
           "ORDER BY CASE WHEN term_id = ?4 THEN 0 ELSE 1 END, fee_setting_id DESC LIMIT 1", nativeQuery = true)
    Optional<ProgramFeeSetting> findBestMatch(int programId, int yearLevel, int semester, Integer termId);
    
    @Query(value = "SELECT * FROM program_fee_settings WHERE program_id = ?1 AND year_level = ?2 AND semester_number = ?3 AND is_active = 1 " +
           "AND (term_id = ?4 OR term_id IS NULL) " +
           "ORDER BY CASE WHEN term_id = ?4 THEN 0 ELSE 1 END, fee_setting_id DESC LIMIT 1", nativeQuery = true)
    Optional<ProgramFeeSetting> findActiveForScopeOrFallback(int programId, int yearLevel, int semester, Integer termId);

    // Methods for Term-to-Term Import
    java.util.List<ProgramFeeSetting> findByTermIdAndIsActiveTrue(Integer termId);
    
    java.util.List<ProgramFeeSetting> findByTermIdAndProgramIdAndYearLevelAndSemesterNumberAndIsActiveTrue(
        Integer termId, Integer programId, Integer yearLevel, Integer semesterNumber);
    
    Optional<ProgramFeeSetting> findFirstByProgramIdAndYearLevelAndSemesterNumberAndTermIdOrderByFeeSettingIdDesc(
        Integer programId, Integer yearLevel, Integer semesterNumber, Integer termId);
}
