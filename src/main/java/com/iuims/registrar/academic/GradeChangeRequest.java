package com.iuims.registrar.academic;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_change_requests")
public class GradeChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "student_name", length = 100)
    private String studentName;

    @Column(name = "course_code", length = 20)
    private String courseCode;

    @Column(name = "faculty_name", length = 100)
    private String facultyName;

    @Column(name = "request_type", length = 40)
    private String requestType;

    @Column(name = "requested_grade", length = 20)
    private String requestedGrade;

    @Column(name = "requested_prelim")
    private BigDecimal requestedPrelim;

    @Column(name = "requested_midterm")
    private BigDecimal requestedMidterm;

    @Column(name = "requested_finals")
    private BigDecimal requestedFinals;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "request_date")
    private LocalDateTime requestDate;

    @Column(name = "applied_action", length = 80)
    private String appliedAction;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    // Getters and Setters
    public Integer getRequestId() { return requestId; }
    public void setRequestId(Integer requestId) { this.requestId = requestId; }

    public Long getGradeId() { return gradeId; }
    public void setGradeId(Long gradeId) { this.gradeId = gradeId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getRequestedGrade() { return requestedGrade; }
    public void setRequestedGrade(String requestedGrade) { this.requestedGrade = requestedGrade; }

    public BigDecimal getRequestedPrelim() { return requestedPrelim; }
    public void setRequestedPrelim(BigDecimal requestedPrelim) { this.requestedPrelim = requestedPrelim; }

    public BigDecimal getRequestedMidterm() { return requestedMidterm; }
    public void setRequestedMidterm(BigDecimal requestedMidterm) { this.requestedMidterm = requestedMidterm; }

    public BigDecimal getRequestedFinals() { return requestedFinals; }
    public void setRequestedFinals(BigDecimal requestedFinals) { this.requestedFinals = requestedFinals; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDateTime requestDate) { this.requestDate = requestDate; }

    public String getAppliedAction() { return appliedAction; }
    public void setAppliedAction(String appliedAction) { this.appliedAction = appliedAction; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
}
