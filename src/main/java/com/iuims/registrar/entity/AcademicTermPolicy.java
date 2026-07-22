package com.iuims.registrar.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_term_policies")
public class AcademicTermPolicy {

    @Id
    @Column(name = "term_id")
    private Integer termId;

    @Column(name = "inc_expiration_date")
    private LocalDate incExpirationDate;

    @Column(name = "midterm_exam_date")
    private LocalDate midtermExamDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and Setters
    public Integer getTermId() { return termId; }
    public void setTermId(Integer termId) { this.termId = termId; }

    public LocalDate getIncExpirationDate() { return incExpirationDate; }
    public void setIncExpirationDate(LocalDate incExpirationDate) { this.incExpirationDate = incExpirationDate; }

    public LocalDate getMidtermExamDate() { return midtermExamDate; }
    public void setMidtermExamDate(LocalDate midtermExamDate) { this.midtermExamDate = midtermExamDate; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
