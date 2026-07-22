package com.iuims.registrar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "class_sections")
public class ClassSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Integer sectionId;

    @Column(name = "course_id")
    private Integer courseId;

    @Column(name = "term_id")
    private Integer termId;

    @Column(name = "section_code")
    private String sectionCode;

    @Column(name = "faculty_id")
    private Integer facultyId;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "section_status")
    private String sectionStatus;

    @Column(name = "semester_number")
    private Integer semesterNumber;

    @Column(name = "block_id")
    private Integer blockId;

    // Getters and setters
    public Integer getSectionId() { return sectionId; }
    public void setSectionId(Integer sectionId) { this.sectionId = sectionId; }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public Integer getTermId() { return termId; }
    public void setTermId(Integer termId) { this.termId = termId; }

    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }

    public Integer getFacultyId() { return facultyId; }
    public void setFacultyId(Integer facultyId) { this.facultyId = facultyId; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public String getSectionStatus() { return sectionStatus; }
    public void setSectionStatus(String sectionStatus) { this.sectionStatus = sectionStatus; }

    public Integer getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(Integer semesterNumber) { this.semesterNumber = semesterNumber; }

    public Integer getBlockId() { return blockId; }
    public void setBlockId(Integer blockId) { this.blockId = blockId; }
}
