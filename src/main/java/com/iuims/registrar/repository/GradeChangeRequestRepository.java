package com.iuims.registrar.repository;
import com.iuims.registrar.entity.GradeChangeRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeChangeRequestRepository extends JpaRepository<GradeChangeRequest, Integer> {
    List<GradeChangeRequest> findByStatus(String status);
    List<GradeChangeRequest> findByGradeId(Long gradeId);
}
