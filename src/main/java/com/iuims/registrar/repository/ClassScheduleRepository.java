package com.iuims.registrar.repository;
import com.iuims.registrar.entity.ClassSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Integer> {
    List<ClassSchedule> findBySectionId(Integer sectionId);
    List<ClassSchedule> findByFacultyId(Integer facultyId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE ClassSchedule c SET c.isUnlocked = :unlockStatus WHERE c.scheduleId = :scheduleId")
    void updateIsUnlockedByScheduleId(@org.springframework.data.repository.query.Param("scheduleId") Integer scheduleId, @org.springframework.data.repository.query.Param("unlockStatus") Integer unlockStatus);
}
