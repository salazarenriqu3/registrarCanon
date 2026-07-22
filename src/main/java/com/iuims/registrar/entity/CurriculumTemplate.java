package com.iuims.registrar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "curriculum_templates")
public class CurriculumTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "curriculum_id")
    private Integer curriculumId;

    @Column(name = "program_id")
    private Integer programId;

    public Integer getCurriculumId() { return curriculumId; }
    public void setCurriculumId(Integer curriculumId) { this.curriculumId = curriculumId; }
    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }
}
