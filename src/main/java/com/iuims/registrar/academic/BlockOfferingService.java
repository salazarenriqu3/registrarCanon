package com.iuims.registrar.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regular block cohorts: program + year + semester + group, materialized as
 * one {@code class_sections} row per curriculum course (shared section_code).
 */
@Service
public class BlockOfferingService {

    public static final String BLOCK_SECTION_CODE_PATTERN = "^[A-Z0-9]+-\\d+-\\d+-[A-Z]$";
    /** Match MariaDB 12 / programs.program_code default collation. */
    private static final String DB_COLLATE = "utf8mb4_uca1400_ai_ci";

    private final JdbcTemplate db;

    public BlockOfferingService(JdbcTemplate db) {
        this.db = db;
    }

    public void ensureSchema() {
        db.execute(
            "CREATE TABLE IF NOT EXISTS block_offerings (" +
            "block_id INT AUTO_INCREMENT PRIMARY KEY, term_id INT NOT NULL, program_code VARCHAR(32) NOT NULL, " +
            "year_level TINYINT NOT NULL, semester_number TINYINT NOT NULL, section_group VARCHAR(10) NOT NULL DEFAULT 'A', " +
            "max_capacity INT NOT NULL DEFAULT 40, faculty_id INT NULL, curriculum_id INT NULL, " +
            "block_status VARCHAR(20) NOT NULL DEFAULT 'Open', " +
            "UNIQUE KEY uk_block_scope (term_id, program_code, year_level, semester_number, section_group), " +
            "KEY idx_block_term (term_id))");
        try {
            db.execute("ALTER TABLE class_sections ADD COLUMN block_id INT NULL");
        } catch (Exception ignored) {
            // column already exists
        }
        try {
            db.execute("ALTER TABLE class_sections ADD KEY idx_cs_block (block_id)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE block_offerings CONVERT TO CHARACTER SET utf8mb4 COLLATE " + DB_COLLATE);
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> listPrograms() {
        ensureSchema();
        return db.queryForList(
            "SELECT program_code, program_name, school_name FROM programs " +
            "WHERE COALESCE(active_status, 1) = 1 ORDER BY school_name, program_code");
    }

    public List<Map<String, Object>> listBlocksForTerm(int termId) {
        ensureSchema();
        syncLegacyBlockLinks(termId);
        return db.queryForList(
            "SELECT bo.block_id, bo.term_id, bo.program_code, p.program_name, p.school_name, " +
            "bo.year_level, bo.semester_number, bo.section_group, bo.max_capacity, bo.block_status, " +
            "bo.curriculum_id, ct.curriculum_name, " +
            "CONCAT(bo.program_code, '-', bo.year_level, '-', bo.semester_number, '-', bo.section_group) AS block_code, " +
            "(SELECT COUNT(*) FROM class_sections cs WHERE cs.block_id = bo.block_id) AS course_slots, " +
            "(SELECT COUNT(DISTINCT cs.section_id) FROM class_sections cs " +
            " JOIN class_schedules sch ON sch.section_id = cs.section_id WHERE cs.block_id = bo.block_id) AS scheduled_slots " +
            "FROM block_offerings bo " +
            "JOIN programs p ON p.program_code COLLATE " + DB_COLLATE + " = bo.program_code COLLATE " + DB_COLLATE + " " +
            "LEFT JOIN curriculum_templates ct ON ct.curriculum_id = bo.curriculum_id " +
            "WHERE bo.term_id = ? " +
            "ORDER BY p.program_code, bo.year_level, bo.semester_number, bo.section_group",
            termId);
    }

    public List<Map<String, Object>> listBlockCourses(int blockId) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT cs.section_id, cs.course_id, cs.faculty_id, c.course_code, c.course_title, c.credit_units, " +
            "cs.section_code, cs.section_status, cs.max_capacity, " +
            "IFNULL(CONCAT(f.first_name, ' ', f.last_name), 'TBA') AS faculty_name " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "LEFT JOIN faculty f ON f.faculty_id = cs.faculty_id " +
            "WHERE cs.block_id = ? ORDER BY c.course_code",
            blockId);
        String[] dayNames = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        for (Map<String, Object> row : rows) {
            int sectionId = ((Number) row.get("section_id")).intValue();
            List<Map<String, Object>> scheds = db.queryForList(
                "SELECT sch.schedule_id, sch.day_of_week, " +
                "TIME_FORMAT(sch.start_time,'%h:%i %p') AS start_fmt, " +
                "TIME_FORMAT(sch.end_time,'%h:%i %p') AS end_fmt, " +
                "IFNULL(r.room_code,'TBA') AS room_code " +
                "FROM class_schedules sch LEFT JOIN rooms r ON sch.room_id = r.room_id " +
                "WHERE sch.section_id = ? ORDER BY sch.day_of_week, sch.start_time",
                sectionId);
            for (Map<String, Object> s : scheds) {
                int d = s.get("day_of_week") instanceof Number n ? n.intValue() : 0;
                s.put("day_name", d >= 1 && d <= 7 ? dayNames[d] : "TBA");
            }
            row.put("schedules", scheds);
            row.put("schedule_count", scheds.size());
            StringBuilder pretty = new StringBuilder();
            for (int i = 0; i < scheds.size(); i++) {
                Map<String, Object> s = scheds.get(i);
                if (i > 0) pretty.append("; ");
                pretty.append(s.get("day_name")).append(' ')
                    .append(s.get("start_fmt")).append('-').append(s.get("end_fmt"))
                    .append(' ').append(s.get("room_code"));
            }
            row.put("pretty_schedule", scheds.isEmpty() ? "TBA" : pretty.toString());
        }
        return rows;
    }

    @Transactional
    public String createAndMaterializeBlock(int termId, String programCode, int yearLevel, int semesterNumber,
                                            String sectionGroup, int maxCapacity, Integer facultyId,
                                            Integer curriculumId) {
        ensureSchema();
        String program = programCode != null ? programCode.trim().toUpperCase() : "";
        String group = sectionGroup != null && !sectionGroup.isBlank()
            ? sectionGroup.trim().toUpperCase() : "A";
        if (program.isEmpty()) {
            return "ERROR: Program is required.";
        }
        if (yearLevel < 1 || yearLevel > 4 || semesterNumber < 1 || semesterNumber > 2) {
            return "ERROR: Year level must be 1–4 and semester 1–2.";
        }

        Integer resolvedCurriculumId = curriculumId;
        if (resolvedCurriculumId == null || resolvedCurriculumId <= 0) {
            return "ERROR: Choose a curriculum for this block.";
        }
        if (!curriculumBelongsToProgram(resolvedCurriculumId, program)) {
            return "ERROR: Selected curriculum does not belong to " + program + ".";
        }

        Integer existing = null;
        try {
            existing = db.queryForObject(
                "SELECT block_id FROM block_offerings WHERE term_id = ? AND program_code = ? " +
                "AND year_level = ? AND semester_number = ? AND section_group = ?",
                Integer.class, termId, program, yearLevel, semesterNumber, group);
        } catch (Exception ignored) {
        }

        int blockId;
        if (existing != null) {
            blockId = existing;
            db.update(
                "UPDATE block_offerings SET max_capacity = ?, faculty_id = ?, curriculum_id = ?, block_status = 'Open' " +
                "WHERE block_id = ?",
                maxCapacity, facultyId != null && facultyId > 0 ? facultyId : null, resolvedCurriculumId, blockId);
        } else {
            db.update(
                "INSERT INTO block_offerings (term_id, program_code, year_level, semester_number, section_group, " +
                "max_capacity, faculty_id, curriculum_id, block_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Open')",
                termId, program, yearLevel, semesterNumber, group, maxCapacity,
                facultyId != null && facultyId > 0 ? facultyId : null, resolvedCurriculumId);
            blockId = db.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        }

        try {
            MaterializeResult result = materializeBlockCourses(blockId);
            return "SUCCESS: Block " + program + "-" + yearLevel + "-" + semesterNumber + "-" + group
                + " — created " + result.created + " course slot(s), linked " + result.linked + ", skipped " + result.skipped + ".";
        } catch (IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Transactional
    public String rematerializeBlock(int blockId) {
        ensureSchema();
        try {
            MaterializeResult result = materializeBlockCourses(blockId);
        return "SUCCESS: Refreshed block — created " + result.created + ", linked " + result.linked
            + ", skipped " + result.skipped + ".";
        } catch (IllegalStateException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Link legacy block-coded sections only when an explicit block row already exists. */
    @Transactional
    public void syncLegacyBlockLinks(int termId) {
        ensureSchema();
        List<Map<String, Object>> legacy = db.queryForList(
            "SELECT DISTINCT cs.term_id, cs.section_code " +
            "FROM class_sections cs WHERE cs.term_id = ? AND cs.section_code REGEXP ?",
            termId, "^[A-Z0-9]+-[0-9]+-[0-9]+-[A-Z]$");
        for (Map<String, Object> row : legacy) {
            String code = String.valueOf(row.get("section_code"));
            ParsedBlock parsed = parseBlockCode(code);
            if (parsed == null) continue;
            Integer blockId = findExistingBlockRow(parsed, termId);
            if (blockId == null) continue;
            db.update(
                "UPDATE class_sections SET block_id = ? WHERE term_id = ? AND section_code = ? AND block_id IS NULL",
                blockId, termId, code);
        }
    }

    private Integer findExistingBlockRow(ParsedBlock parsed, int termId) {
        try {
            return db.queryForObject(
                "SELECT block_id FROM block_offerings WHERE term_id = ? AND program_code = ? " +
                "AND year_level = ? AND semester_number = ? AND section_group = ?",
                Integer.class, termId, parsed.programCode, parsed.yearLevel, parsed.semesterNumber, parsed.sectionGroup);
        } catch (Exception e) {
            return null;
        }
    }

    private MaterializeResult materializeBlockCourses(int blockId) {
        Map<String, Object> block = db.queryForMap(
            "SELECT block_id, term_id, program_code, year_level, semester_number, section_group, " +
            "max_capacity, faculty_id, curriculum_id FROM block_offerings WHERE block_id = ?", blockId);

        int termId = ((Number) block.get("term_id")).intValue();
        int yearLevel = ((Number) block.get("year_level")).intValue();
        int semester = ((Number) block.get("semester_number")).intValue();
        String sectionCode = block.get("program_code") + "-" + yearLevel + "-" + semester + "-"
            + block.get("section_group");
        int maxCapacity = block.get("max_capacity") != null ? ((Number) block.get("max_capacity")).intValue() : 40;
        Integer facultyId = block.get("faculty_id") instanceof Number n ? n.intValue() : null;
        Integer curriculumId = block.get("curriculum_id") instanceof Number n ? n.intValue() : null;
        if (curriculumId == null) {
            throw new IllegalStateException("Block has no curriculum assigned. Create or update it with an explicit curriculum.");
        }

        List<Map<String, Object>> courses = db.queryForList(
            "SELECT cc.course_id, " +
            "CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "  THEN c.coordinator_equivalent_units ELSE c.credit_units END AS load_units " +
            "FROM curriculum_courses cc " +
            "JOIN courses c ON c.course_id = cc.course_id " +
            "WHERE cc.curriculum_id = ? AND cc.year_level = ? AND cc.semester_number = ? " +
            "AND COALESCE(c.onlist, c.active_status, 1) = 1 ORDER BY c.course_code",
            curriculumId, yearLevel, semester);

        if (facultyId != null && facultyId > 0) {
            int pendingUnits = 0;
            for (Map<String, Object> course : courses) {
                int courseId = ((Number) course.get("course_id")).intValue();
                List<Map<String, Object>> existing = db.queryForList(
                    "SELECT 1 FROM class_sections WHERE course_id = ? AND term_id = ? AND section_code = ? LIMIT 1",
                    courseId, termId, sectionCode);
                if (existing.isEmpty()) {
                    pendingUnits += course.get("load_units") instanceof Number n ? n.intValue() : 0;
                }
            }
            String loadConflict = new ScheduleConflictValidator(db)
                .validateFacultyLoadCap(facultyId, termId, pendingUnits, null);
            if (loadConflict != null) {
                throw new IllegalStateException(loadConflict);
            }
        }

        int created = 0, linked = 0, skipped = 0;
        for (Map<String, Object> course : courses) {
            int courseId = ((Number) course.get("course_id")).intValue();
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT section_id, block_id FROM class_sections " +
                "WHERE course_id = ? AND term_id = ? AND section_code = ? LIMIT 1",
                courseId, termId, sectionCode);
            if (existing.isEmpty()) {
                db.update(
                    "INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, " +
                    "semester_number, faculty_id, block_id) VALUES (?, ?, ?, ?, 'Open', ?, ?, ?)",
                    courseId, termId, sectionCode, maxCapacity, semester,
                    facultyId != null && facultyId > 0 ? facultyId : null, blockId);
                created++;
            } else {
                Integer sectionId = ((Number) existing.get(0).get("section_id")).intValue();
                Object currentBlock = existing.get(0).get("block_id");
                if (currentBlock == null) {
                    db.update("UPDATE class_sections SET block_id = ? WHERE section_id = ?", blockId, sectionId);
                    linked++;
                } else {
                    skipped++;
                }
            }
        }
        return new MaterializeResult(created, linked, skipped);
    }

    private boolean curriculumBelongsToProgram(Integer curriculumId, String programCode) {
        if (curriculumId == null || programCode == null || programCode.isBlank()) return false;
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_templates ct " +
                "JOIN programs p ON p.program_id = ct.program_id " +
                "WHERE ct.curriculum_id = ? AND p.program_code COLLATE " + DB_COLLATE + " = ? COLLATE " + DB_COLLATE,
                Integer.class, curriculumId, programCode.trim().toUpperCase());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static ParsedBlock parseBlockCode(String sectionCode) {
        if (sectionCode == null || !sectionCode.matches(BLOCK_SECTION_CODE_PATTERN)) {
            return null;
        }
        String[] parts = sectionCode.split("-");
        if (parts.length < 4) return null;
        try {
            return new ParsedBlock(
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ParsedBlock(String programCode, int yearLevel, int semesterNumber, String sectionGroup) {}
    record MaterializeResult(int created, int linked, int skipped) {}
}
