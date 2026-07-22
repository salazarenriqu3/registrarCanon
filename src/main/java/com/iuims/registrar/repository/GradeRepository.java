package com.iuims.registrar.repository;
import com.iuims.registrar.entity.Grade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Integer> {
    
    List<Grade> findBySectionId(Integer sectionId);
    List<Grade> findByStudentId(String studentId);
    List<Grade> findByStudentName(String studentName);
    Optional<Grade> findTopByStudentIdAndSectionIdOrderByIdDesc(String studentId, Integer sectionId);

    @Query(value = "SELECT s.term_id FROM grades g JOIN class_sections s ON g.section_id = s.section_id WHERE g.id = :gradeId", nativeQuery = true)
    Integer findTermIdByGradeId(@Param("gradeId") int gradeId);

    @Modifying
    @Query("UPDATE Grade g SET g.status = :status WHERE g.sectionId = :sectionId")
    int updateStatusBySectionId(@Param("sectionId") int sectionId, @Param("status") String status);

    @Modifying
    @Query("UPDATE Grade g SET g.status = :status WHERE g.sectionId = :sectionId AND g.status = :currentStatus")
    int updateStatusBySectionIdAndStatus(@Param("sectionId") int sectionId, @Param("status") String newStatus, @Param("currentStatus") String currentStatus);
}
