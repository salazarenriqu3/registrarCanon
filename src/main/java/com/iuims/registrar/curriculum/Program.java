package com.iuims.registrar.curriculum;

import jakarta.persistence.*;

@Entity
@Table(name = "programs")
public class Program {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_id")
    private Integer programId;

    @Column(name = "program_code")
    private String programCode;
    
    @Column(name = "program_name")
    private String programName;

    @Column(name = "department_id")
    private Integer departmentId;

    @Column(name = "school_name")
    private String schoolName;

    @Column(name = "duration_years")
    private Integer durationYears;

    @Column(name = "active_status")
    private Integer activeStatus;

    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }
    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }
    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public Integer getDurationYears() { return durationYears; }
    public void setDurationYears(Integer durationYears) { this.durationYears = durationYears; }
    public Integer getActiveStatus() { return activeStatus; }
    public void setActiveStatus(Integer activeStatus) { this.activeStatus = activeStatus; }
}
