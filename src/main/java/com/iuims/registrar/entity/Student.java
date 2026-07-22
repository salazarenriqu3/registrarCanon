package com.iuims.registrar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @Column(name = "student_number", length = 100)
    private String studentNumber;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "archive_key", length = 80)
    private String archiveKey;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "real_name", length = 200)
    private String realName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "program_code", length = 100)
    private String programCode;

    @Column(name = "year_level")
    private Integer yearLevel;

    @Column(name = "semester")
    private Integer semester;

    @Column(name = "term_year", length = 50)
    private String termYear;

    @Column(name = "student_type", length = 50)
    private String studentType;

    @Column(name = "enrollment_status_type", length = 50)
    private String enrollmentStatusType;

    @Column(name = "admission_status", length = 50)
    private String admissionStatus;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "enrollment_blocked")
    private Boolean enrollmentBlocked;

    @Column(name = "scholarship_type", length = 50)
    private String scholarshipType;

    @Column(name = "scholarship_approved")
    private Boolean scholarshipApproved;

    @Column(name = "scholarship_amount")
    private BigDecimal scholarshipAmount;

    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;

    // Getters and Setters
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getArchiveKey() { return archiveKey; }
    public void setArchiveKey(String archiveKey) { this.archiveKey = archiveKey; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public Integer getYearLevel() { return yearLevel; }
    public void setYearLevel(Integer yearLevel) { this.yearLevel = yearLevel; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public String getTermYear() { return termYear; }
    public void setTermYear(String termYear) { this.termYear = termYear; }

    public String getStudentType() { return studentType; }
    public void setStudentType(String studentType) { this.studentType = studentType; }

    public String getEnrollmentStatusType() { return enrollmentStatusType; }
    public void setEnrollmentStatusType(String enrollmentStatusType) { this.enrollmentStatusType = enrollmentStatusType; }

    public String getAdmissionStatus() { return admissionStatus; }
    public void setAdmissionStatus(String admissionStatus) { this.admissionStatus = admissionStatus; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public Boolean getEnrollmentBlocked() { return enrollmentBlocked; }
    public void setEnrollmentBlocked(Boolean enrollmentBlocked) { this.enrollmentBlocked = enrollmentBlocked; }

    public String getScholarshipType() { return scholarshipType; }
    public void setScholarshipType(String scholarshipType) { this.scholarshipType = scholarshipType; }

    public Boolean getScholarshipApproved() { return scholarshipApproved; }
    public void setScholarshipApproved(Boolean scholarshipApproved) { this.scholarshipApproved = scholarshipApproved; }

    public BigDecimal getScholarshipAmount() { return scholarshipAmount; }
    public void setScholarshipAmount(BigDecimal scholarshipAmount) { this.scholarshipAmount = scholarshipAmount; }

    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }
}
