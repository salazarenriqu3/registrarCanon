package com.iuims.registrar.repository;
import com.iuims.registrar.entity.Course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {
    
    @Query("SELECT c FROM Course c WHERE LOWER(c.courseCode) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.courseTitle) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Course> searchCourses(@Param("query") String query);
}
