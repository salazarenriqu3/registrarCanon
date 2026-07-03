package com.iuims.registrar.curriculum;

import jakarta.persistence.*;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Integer courseId;

    @Column(name = "course_code", length = 40)
    private String courseCode;

    @Column(name = "course_title", length = 150)
    private String courseTitle;

    @Column(name = "department_id")
    private Integer departmentId;

    @Column(name = "credit_units")
    private Integer creditUnits;

    @Column(name = "lec_units")
    private Integer lecUnits;

    @Column(name = "lab_units")
    private Integer labUnits;

    @Column(name = "component_type", length = 10)
    private String componentType;

    @Column(name = "course_family_code", length = 40)
    private String courseFamilyCode;

    @Column(name = "parent_course_id")
    private Integer parentCourseId;

    // Getters and Setters
    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public Integer getCreditUnits() { return creditUnits; }
    public void setCreditUnits(Integer creditUnits) { this.creditUnits = creditUnits; }

    public Integer getLecUnits() { return lecUnits; }
    public void setLecUnits(Integer lecUnits) { this.lecUnits = lecUnits; }

    public Integer getLabUnits() { return labUnits; }
    public void setLabUnits(Integer labUnits) { this.labUnits = labUnits; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getCourseFamilyCode() { return courseFamilyCode; }
    public void setCourseFamilyCode(String courseFamilyCode) { this.courseFamilyCode = courseFamilyCode; }

    public Integer getParentCourseId() { return parentCourseId; }
    public void setParentCourseId(Integer parentCourseId) { this.parentCourseId = parentCourseId; }
}
