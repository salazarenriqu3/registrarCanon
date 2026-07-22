package com.iuims.registrar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "curriculum_courses")
public class CurriculumCourse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "curriculum_course_id")
    private Integer curriculumCourseId;

    @Column(name = "curriculum_id")
    private Integer curriculumId;

    @Column(name = "course_id")
    private Integer courseId;

    @Column(name = "year_level")
    private Integer yearLevel;

    @Column(name = "semester_number")
    private Integer semesterNumber;

    public Integer getCurriculumCourseId() { return curriculumCourseId; }
    public void setCurriculumCourseId(Integer curriculumCourseId) { this.curriculumCourseId = curriculumCourseId; }
    public Integer getCurriculumId() { return curriculumId; }
    public void setCurriculumId(Integer curriculumId) { this.curriculumId = curriculumId; }
    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }
    public Integer getYearLevel() { return yearLevel; }
    public void setYearLevel(Integer yearLevel) { this.yearLevel = yearLevel; }
    public Integer getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(Integer semesterNumber) { this.semesterNumber = semesterNumber; }
}
