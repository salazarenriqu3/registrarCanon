package com.iuims.registrar.repository;
import com.iuims.registrar.entity.CurriculumCatalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurriculumCatalogRepository extends JpaRepository<CurriculumCatalog, String> {
}
