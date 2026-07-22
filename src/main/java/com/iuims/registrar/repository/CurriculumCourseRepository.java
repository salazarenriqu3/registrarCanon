package com.iuims.registrar.repository;
import com.iuims.registrar.entity.CurriculumCourse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CurriculumCourseRepository extends JpaRepository<CurriculumCourse, Integer> {
    List<CurriculumCourse> findByCurriculumId(Integer curriculumId);
    List<CurriculumCourse> findByCourseId(Integer courseId);
}
