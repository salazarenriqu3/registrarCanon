package com.iuims.registrar.academic;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grades")
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "student_id")
    private String studentId;

    @Column(name = "section_id")
    private Integer sectionId;

    @Column(name = "course_id")
    private Integer courseId;

    @Column(name = "student_name")
    private String studentName;

    private BigDecimal prelim;
    private BigDecimal midterm;
    
    @Column(name = "final_grade")
    private BigDecimal finalGrade;
    
    @Column(name = "semestral_grade")
    private BigDecimal semestralGrade;

    private String remarks;

    @Column(name = "previous_grade")
    private String previousGrade;

    @Column(name = "grade_lock_status")
    private String gradeLockStatus;

    @Column(name = "grade_lock_reason")
    private String gradeLockReason;

    @Column(name = "registrar_final_grade")
    private BigDecimal registrarFinalGrade;

    @Column(name = "registrar_final_remarks")
    private String registrarFinalRemarks;

    @Column(name = "registrar_finalized_at")
    private LocalDateTime registrarFinalizedAt;

    @Column(name = "curriculum_year")
    private Integer curriculumYear;

    @Column(name = "grade")
    private Double grade;

    @Column(name = "date_recorded")
    private LocalDateTime dateRecorded;

    @Column(name = "status")
    private String status;

    // Getters and Setters

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public Integer getSectionId() { return sectionId; }
    public void setSectionId(Integer sectionId) { this.sectionId = sectionId; }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public BigDecimal getPrelim() { return prelim; }
    public void setPrelim(BigDecimal prelim) { this.prelim = prelim; }

    public BigDecimal getMidterm() { return midterm; }
    public void setMidterm(BigDecimal midterm) { this.midterm = midterm; }

    public BigDecimal getFinalGrade() { return finalGrade; }
    public void setFinalGrade(BigDecimal finalGrade) { this.finalGrade = finalGrade; }

    public BigDecimal getSemestralGrade() { return semestralGrade; }
    public void setSemestralGrade(BigDecimal semestralGrade) { this.semestralGrade = semestralGrade; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getPreviousGrade() { return previousGrade; }
    public void setPreviousGrade(String previousGrade) { this.previousGrade = previousGrade; }

    public String getGradeLockStatus() { return gradeLockStatus; }
    public void setGradeLockStatus(String gradeLockStatus) { this.gradeLockStatus = gradeLockStatus; }

    public String getGradeLockReason() { return gradeLockReason; }
    public void setGradeLockReason(String gradeLockReason) { this.gradeLockReason = gradeLockReason; }

    public BigDecimal getRegistrarFinalGrade() { return registrarFinalGrade; }
    public void setRegistrarFinalGrade(BigDecimal registrarFinalGrade) { this.registrarFinalGrade = registrarFinalGrade; }

    public String getRegistrarFinalRemarks() { return registrarFinalRemarks; }
    public void setRegistrarFinalRemarks(String registrarFinalRemarks) { this.registrarFinalRemarks = registrarFinalRemarks; }

    public LocalDateTime getRegistrarFinalizedAt() { return registrarFinalizedAt; }
    public void setRegistrarFinalizedAt(LocalDateTime registrarFinalizedAt) { this.registrarFinalizedAt = registrarFinalizedAt; }

    public Integer getCurriculumYear() { return curriculumYear; }
    public void setCurriculumYear(Integer curriculumYear) { this.curriculumYear = curriculumYear; }

    public Double getGrade() { return grade; }
    public void setGrade(Double grade) { this.grade = grade; }

    public LocalDateTime getDateRecorded() { return dateRecorded; }
    public void setDateRecorded(LocalDateTime dateRecorded) { this.dateRecorded = dateRecorded; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
