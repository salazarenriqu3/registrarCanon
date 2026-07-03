package com.iuims.registrar.core;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_users")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "real_name", length = 100)
    private String realName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "role", length = 30)
    private String role;

    @Column(name = "password", length = 200)
    private String password;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "program_code", length = 100)
    private String programCode;

    @Column(name = "granted_permissions", length = 1000)
    private String grantedPermissions;

    @Column(name = "semester")
    private Integer semester;

    @Column(name = "year_level")
    private Integer yearLevel;

    @Column(name = "term_year", length = 50)
    private String termYear;

    @Column(name = "admission_status", length = 50)
    private String admissionStatus;

    @Column(name = "student_type", length = 50)
    private String studentType;

    @Column(name = "enrollment_start_time")
    private LocalDateTime enrollmentStartTime;

    // Getters and Setters
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public String getGrantedPermissions() { return grantedPermissions; }
    public void setGrantedPermissions(String grantedPermissions) { this.grantedPermissions = grantedPermissions; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public Integer getYearLevel() { return yearLevel; }
    public void setYearLevel(Integer yearLevel) { this.yearLevel = yearLevel; }

    public String getTermYear() { return termYear; }
    public void setTermYear(String termYear) { this.termYear = termYear; }

    public String getAdmissionStatus() { return admissionStatus; }
    public void setAdmissionStatus(String admissionStatus) { this.admissionStatus = admissionStatus; }

    public String getStudentType() { return studentType; }
    public void setStudentType(String studentType) { this.studentType = studentType; }

    public LocalDateTime getEnrollmentStartTime() { return enrollmentStartTime; }
    public void setEnrollmentStartTime(LocalDateTime enrollmentStartTime) { this.enrollmentStartTime = enrollmentStartTime; }
}
