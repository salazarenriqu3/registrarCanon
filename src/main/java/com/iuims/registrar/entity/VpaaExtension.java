package com.iuims.registrar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vpaa_extensions")
public class VpaaExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ext_id")
    private Integer extId;

    @Column(name = "schedule_id")
    private Integer scheduleId;

    @Column(name = "faculty_id")
    private Integer facultyId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", length = 30)
    private String status;

    // Getters and Setters
    public Integer getExtId() { return extId; }
    public void setExtId(Integer extId) { this.extId = extId; }

    public Integer getScheduleId() { return scheduleId; }
    public void setScheduleId(Integer scheduleId) { this.scheduleId = scheduleId; }

    public Integer getFacultyId() { return facultyId; }
    public void setFacultyId(Integer facultyId) { this.facultyId = facultyId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
