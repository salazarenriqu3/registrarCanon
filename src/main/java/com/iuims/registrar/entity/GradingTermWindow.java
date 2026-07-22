package com.iuims.registrar.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grading_term_windows")
public class GradingTermWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "window_id")
    private Long windowId;

    @Column(name = "term_id", nullable = false)
    private Integer termId;

    @Column(name = "grading_period", nullable = false, length = 20)
    private String gradingPeriod;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "override_status", nullable = false, length = 20)
    private String overrideStatus = "AUTO";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and Setters
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }

    public Integer getTermId() { return termId; }
    public void setTermId(Integer termId) { this.termId = termId; }

    public String getGradingPeriod() { return gradingPeriod; }
    public void setGradingPeriod(String gradingPeriod) { this.gradingPeriod = gradingPeriod; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getOverrideStatus() { return overrideStatus; }
    public void setOverrideStatus(String overrideStatus) { this.overrideStatus = overrideStatus; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
