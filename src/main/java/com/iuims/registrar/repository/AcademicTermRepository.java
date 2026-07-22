package com.iuims.registrar.repository;
import com.iuims.registrar.entity.AcademicTerm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Integer> {

    Optional<AcademicTerm> findFirstByTermCodeOrderByTermIdDesc(String termCode);
    Optional<AcademicTerm> findByTermCode(String termCode);

    Optional<AcademicTerm> findFirstByAcademicYearAndSemesterNumberOrderByTermIdDesc(String academicYear, Integer semesterNumber);

    @Modifying
    @Query("UPDATE AcademicTerm t SET t.isActive = 0, t.status = 'INACTIVE' WHERE t.termId <> :targetTermId")
    int deactivateAllExcept(@Param("targetTermId") Integer targetTermId);

    @Modifying
    @Query("UPDATE AcademicTerm t SET t.isActive = 1, t.status = 'ACTIVE' WHERE t.termId = :targetTermId")
    int activateTerm(@Param("targetTermId") Integer targetTermId);
}
