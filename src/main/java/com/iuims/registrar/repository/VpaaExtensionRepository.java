package com.iuims.registrar.repository;
import com.iuims.registrar.entity.VpaaExtension;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VpaaExtensionRepository extends JpaRepository<VpaaExtension, Integer> {
    List<VpaaExtension> findByScheduleIdAndStatus(Integer scheduleId, String status);
    
    @Query("SELECT e FROM VpaaExtension e WHERE e.status = 'PENDING'")
    List<VpaaExtension> findPendingExtensions();

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE VpaaExtension e SET e.status = :status WHERE e.scheduleId = :scheduleId")
    void updateStatusByScheduleId(@org.springframework.data.repository.query.Param("scheduleId") Integer scheduleId, @org.springframework.data.repository.query.Param("status") String status);
}
