package com.iuims.registrar.repository;
import com.iuims.registrar.entity.Program;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Integer> {
    Program findByProgramCode(String programCode);
}
