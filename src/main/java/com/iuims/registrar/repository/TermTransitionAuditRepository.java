package com.iuims.registrar.repository;
import com.iuims.registrar.entity.TermTransitionAudit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermTransitionAuditRepository extends JpaRepository<TermTransitionAudit, Long> {
}
