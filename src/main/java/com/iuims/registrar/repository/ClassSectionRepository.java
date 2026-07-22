package com.iuims.registrar.repository;
import com.iuims.registrar.entity.ClassSection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, Integer> {
    List<ClassSection> findByTermId(Integer termId);
    List<ClassSection> findByFacultyId(Integer facultyId);
    List<ClassSection> findBySectionStatus(String sectionStatus);
    java.util.Optional<ClassSection> findByCourseIdAndTermIdAndSectionCode(Integer courseId, Integer termId, String sectionCode);

    @Modifying
    @Query("UPDATE ClassSection s SET s.sectionStatus = :status WHERE s.sectionId = :sectionId")
    int updateStatus(@Param("sectionId") int sectionId, @Param("status") String status);

    @Modifying
    @Query("UPDATE ClassSection s SET s.sectionStatus = :newStatus WHERE s.sectionId = :sectionId AND s.sectionStatus IN :currentStatuses")
    int updateStatusIfIn(@Param("sectionId") int sectionId, @Param("newStatus") String newStatus, @Param("currentStatuses") List<String> currentStatuses);
}
