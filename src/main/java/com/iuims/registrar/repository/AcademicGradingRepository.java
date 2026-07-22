package com.iuims.registrar.repository;
import com.iuims.registrar.dto.ClassInfoDto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AcademicGradingRepository {

    private final JdbcTemplate db;

    public AcademicGradingRepository(JdbcTemplate db) {
        this.db = db;
    }

    public ClassInfoDto findClassInfo(int scheduleId) {
        String sql = """
            SELECT cs.section_id AS schedule_id, cs.section_id, cs.section_code AS section, cs.section_code,
            cs.term_id, COALESCE(cs.section_status, 'Open') AS status, c.course_code, c.course_title AS description,
            IFNULL(f.first_name,'') AS faculty_first, IFNULL(f.last_name,'') AS faculty_last
            FROM class_sections cs
            JOIN courses c ON cs.course_id = c.course_id
            LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id
            WHERE cs.section_id = ?
            """;

        return db.queryForObject(sql, (rs, rowNum) -> new ClassInfoDto(
            rs.getInt("schedule_id"),
            rs.getInt("section_id"),
            rs.getString("section_code"),
            rs.getInt("term_id"),
            rs.getString("status"),
            rs.getString("course_code"),
            rs.getString("description"),
            rs.getString("faculty_first"),
            rs.getString("faculty_last"),
            "TBA"
        ), scheduleId);
    }
}
