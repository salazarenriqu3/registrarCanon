package com.iuims.registrar.repository;
import com.iuims.registrar.entity.CurriculumTemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CurriculumTemplateRepository extends JpaRepository<CurriculumTemplate, Integer> {
    List<CurriculumTemplate> findByProgramId(Integer programId);
}
