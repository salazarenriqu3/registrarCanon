package com.iuims.registrar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "curriculum_catalog")
public class CurriculumCatalog {
    @Id
    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "description")
    private String description;

    @Column(name = "units")
    private Integer units;

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
}
