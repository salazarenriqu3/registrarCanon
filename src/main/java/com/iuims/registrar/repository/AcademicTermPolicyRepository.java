package com.iuims.registrar.repository;
import com.iuims.registrar.entity.AcademicTermPolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcademicTermPolicyRepository extends JpaRepository<AcademicTermPolicy, Integer> {
}
