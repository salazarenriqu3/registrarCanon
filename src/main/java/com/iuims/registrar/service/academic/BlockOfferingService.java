package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Regular block cohorts: program + year + semester + group, materialized as
 * one {@code class_sections} row per curriculum course (shared section_code).
 */
@Service
public class BlockOfferingService {

    public static final String BLOCK_SECTION_CODE_PATTERN = "^[A-Z0-9]+-\\d+-\\d+-[A-Z]$";
    /** Match the XAMPP MariaDB 10.4-compatible utf8mb4 collation used by eacdb dumps. */
    private static final String DB_COLLATE = "utf8mb4_unicode_ci";

    private final JdbcTemplate db;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);
    private final Set<Integer> syncedLegacyBlockTerms = ConcurrentHashMap.newKeySet();

    public BlockOfferingService(JdbcTemplate db) {
        this.db = db;
    }

    public synchronized void ensureSchema() {
        if (schemaEnsured.get()) {
            return;
        }
        db.execute(
            "CREATE TABLE IF NOT EXISTS block_offerings (" +
            "block_id INT AUTO_INCREMENT PRIMARY KEY, term_id INT NOT NULL, program_code VARCHAR(32) NOT NULL, " +
            "year_level TINYINT NOT NULL, semester_number TINYINT NOT NULL, section_group VARCHAR(10) NOT NULL DEFAULT 'A', " +
            "max_capacity INT NOT NULL DEFAULT 40, faculty_id INT NULL, curriculum_id INT NULL, " +
            "block_status VARCHAR(20) NOT NULL DEFAULT 'Open', " +
            "UNIQUE KEY uk_block_scope (term_id, program_code, year_level, semester_number, section_group), " +
            "KEY idx_block_term (term_id))");
        try {
            db.execute("ALTER TABLE block_offerings ADD COLUMN irregular_access_override TINYINT NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE class_sections ADD COLUMN block_id INT NULL");
        } catch (Exception ignored) {
            // column already exists
        }
        try {
            db.execute("ALTER TABLE class_sections ADD COLUMN irregular_access_override TINYINT NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE class_sections ADD KEY idx_cs_block (block_id)");
        } catch (Exception ignored) {
        }
        db.execute(
            "CREATE TABLE IF NOT EXISTS irregular_block_access_policies (" +
            "term_id INT NOT NULL, program_code VARCHAR(32) NOT NULL, year_level TINYINT NOT NULL, " +
            "semester_number TINYINT NOT NULL, irregular_open TINYINT(1) NOT NULL DEFAULT 1, " +
            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "PRIMARY KEY (term_id, program_code, year_level, semester_number), " +
            "KEY idx_irregular_block_term (term_id))");
        try {
            db.execute("ALTER TABLE block_offerings CONVERT TO CHARACTER SET utf8mb4 COLLATE " + DB_COLLATE);
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE irregular_block_access_policies CONVERT TO CHARACTER SET utf8mb4 COLLATE " + DB_COLLATE);
        } catch (Exception ignored) {
        }
        schemaEnsured.set(true);
    }

    public List<Map<String, Object>> listPrograms() {
        ensureSchema();
        return db.queryForList(
            "SELECT program_code, program_name, school_name FROM programs " +
            "WHERE COALESCE(active_status, 1) = 1 ORDER BY school_name, program_code");
    }

    public List<Map<String, Object>> listBlockProgramSummariesForTerm(int termId) {
        ensureSchema();
        return db.queryForList(
            "SELECT COALESCE(NULLIF(TRIM(p.school_name), ''), 'Unassigned School') AS school_name, " +
                "bo.program_code, p.program_name, COUNT(*) AS block_count " +
                "FROM block_offerings bo " +
                "JOIN programs p ON p.program_code COLLATE " + DB_COLLATE + " = bo.program_code COLLATE " + DB_COLLATE + " " +
                "WHERE bo.term_id = ? " +
                "GROUP BY p.school_name, bo.program_code, p.program_name " +
                "ORDER BY school_name, bo.program_code",
            termId);
    }

    public List<Map<String, Object>> listBlockHeadersForTerm(int termId, String search, String programCode) {
        return queryBlockHeaders(termId, search, programCode, null);
    }

    public List<Map<String, Object>> listBlockHeadersForTermById(int termId, int blockId) {
        return queryBlockHeaders(termId, null, null, blockId);
    }

    private List<Map<String, Object>> queryBlockHeaders(int termId, String search, String programCode,
                                                          Integer blockId) {
        ensureSchema();
        List<Object> args = new ArrayList<>();
        args.add(termId);
        StringBuilder where = new StringBuilder(" WHERE bo.term_id = ? ");
        if (blockId != null && blockId > 0) {
            where.append("AND bo.block_id = ? ");
            args.add(blockId);
        } else {
            String query = search != null ? search.trim().toUpperCase(java.util.Locale.ROOT) : "";
            String program = programCode != null ? programCode.trim().toUpperCase(java.util.Locale.ROOT) : "";
            if (!query.isBlank()) {
                where.append("AND (UPPER(bo.program_code) LIKE ? OR UPPER(p.program_name) LIKE ? OR ")
                    .append("UPPER(CONCAT(bo.program_code, '-', bo.year_level, '-', bo.semester_number, '-', bo.section_group)) LIKE ?) ");
                for (int i = 0; i < 3; i++) args.add("%" + query + "%");
            }
            if (!program.isBlank()) {
                where.append("AND UPPER(bo.program_code) = ? ");
                args.add(program);
            }
        }
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT bo.block_id, bo.term_id, bo.program_code, p.program_name, p.school_name, " +
                "bo.year_level, bo.semester_number, bo.section_group, bo.max_capacity, bo.block_status, " +
                "bo.curriculum_id, ct.curriculum_name, bo.irregular_access_override AS block_irregular_access_override, " +
                "policy.irregular_open AS program_irregular_open, " +
                "CONCAT(bo.program_code, '-', bo.year_level, '-', bo.semester_number, '-', bo.section_group) AS block_code, " +
                "COALESCE(section_counts.course_slots, 0) AS course_slots, COALESCE(section_counts.scheduled_slots, 0) AS scheduled_slots " +
                "FROM block_offerings bo " +
                "JOIN programs p ON p.program_code COLLATE " + DB_COLLATE + " = bo.program_code COLLATE " + DB_COLLATE + " " +
                "LEFT JOIN curriculum_templates ct ON ct.curriculum_id = bo.curriculum_id " +
                "LEFT JOIN irregular_block_access_policies policy ON policy.term_id = bo.term_id " +
                    "AND policy.program_code COLLATE " + DB_COLLATE + " = bo.program_code COLLATE " + DB_COLLATE + " " +
                    "AND policy.year_level = bo.year_level AND policy.semester_number = bo.semester_number " +
                "LEFT JOIN (SELECT cs.block_id, COUNT(*) AS course_slots, COUNT(DISTINCT sch.section_id) AS scheduled_slots " +
                    "FROM class_sections cs LEFT JOIN class_schedules sch ON sch.section_id = cs.section_id GROUP BY cs.block_id) section_counts " +
                    "ON section_counts.block_id = bo.block_id " + where +
                "ORDER BY p.school_name, bo.program_code, bo.year_level, bo.semester_number, bo.section_group",
            args.toArray());
        for (Map<String, Object> row : rows) applyBlockIrregularAccessState(row);
        return rows;
    }

    public Map<Integer, List<Map<String, Object>>> listBlockCoursesForBlocks(List<Integer> blockIds) {
        ensureSchema();
        Map<Integer, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (blockIds == null || blockIds.isEmpty()) return result;
        for (Integer blockId : blockIds) if (blockId != null) result.put(blockId, new ArrayList<>());
        if (result.isEmpty()) return result;
        String placeholders = String.join(",", java.util.Collections.nCopies(result.size(), "?"));
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT cs.section_id, cs.course_id, cs.faculty_id, c.course_code, c.course_title, c.credit_units, " +
                "cs.section_code, cs.section_status, cs.max_capacity, cs.term_id, cs.block_id, " +
                "cs.irregular_access_override AS class_irregular_access_override, bo.program_code, bo.year_level, bo.semester_number, " +
                "bo.irregular_access_override AS block_irregular_access_override, " +
                "IFNULL(CONCAT(f.first_name, ' ', f.last_name), 'TBA') AS faculty_name " +
                "FROM class_sections cs JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id LEFT JOIN faculty f ON f.faculty_id = cs.faculty_id " +
                "WHERE cs.block_id IN (" + placeholders + ") ORDER BY cs.block_id, c.course_code",
            result.keySet().toArray());
        Map<Integer, Map<String, Object>> bySection = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int sectionId = ((Number) row.get("section_id")).intValue();
            row.put("schedules", new ArrayList<Map<String, Object>>());
            result.get(((Number) row.get("block_id")).intValue()).add(row);
            bySection.put(sectionId, row);
        }
        if (!bySection.isEmpty()) {
            String sectionPlaceholders = String.join(",", java.util.Collections.nCopies(bySection.size(), "?"));
            List<Map<String, Object>> schedules = db.queryForList(
                "SELECT sch.schedule_id, sch.section_id, sch.day_of_week, TIME_FORMAT(sch.start_time,'%h:%i %p') AS start_fmt, " +
                    "TIME_FORMAT(sch.end_time,'%h:%i %p') AS end_fmt, IFNULL(r.room_code,'TBA') AS room_code " +
                    "FROM class_schedules sch LEFT JOIN rooms r ON sch.room_id = r.room_id " +
                    "WHERE sch.section_id IN (" + sectionPlaceholders + ") ORDER BY sch.section_id, sch.day_of_week, sch.start_time",
                bySection.keySet().toArray());
            String[] dayNames = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
            for (Map<String, Object> schedule : schedules) {
                int day = schedule.get("day_of_week") instanceof Number n ? n.intValue() : 0;
                schedule.put("day_name", day >= 1 && day <= 7 ? dayNames[day] : "TBA");
                Map<String, Object> section = bySection.get(((Number) schedule.get("section_id")).intValue());
                ((List<Map<String, Object>>) section.get("schedules")).add(schedule);
            }
        }
        for (Map<String, Object> row : rows) {
            List<Map<String, Object>> schedules = (List<Map<String, Object>>) row.get("schedules");
            row.put("schedule_count", schedules.size());
            row.put("pretty_schedule", schedules.isEmpty() ? "TBA" : schedules.stream()
                .map(s -> s.get("day_name") + " " + s.get("start_fmt") + "-" + s.get("end_fmt") + " " + s.get("room_code"))
                .collect(java.util.stream.Collectors.joining("; ")));
            applySectionIrregularAccessState(row);
        }
        return result;
    }

    public List<Map<String, Object>> listBlockCourses(int blockId) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT cs.section_id, cs.course_id, cs.faculty_id, c.course_code, c.course_title, c.credit_units, " +
            "cs.section_code, cs.section_status, cs.max_capacity, cs.term_id, cs.block_id, " +
            "cs.irregular_access_override AS class_irregular_access_override, " +
            "bo.program_code, bo.year_level, bo.semester_number, " +
            "bo.irregular_access_override AS block_irregular_access_override, " +
            "IFNULL(CONCAT(f.first_name, ' ', f.last_name), 'TBA') AS faculty_name " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
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
            applySectionIrregularAccessState(row);
        }
        return rows;
    }

    @Transactional
    public void setBlockIrregularAccessOverride(int blockId, String accessMode) {
        ensureSchema();
        db.update(
            "UPDATE block_offerings SET irregular_access_override = ? WHERE block_id = ?",
            normalizeOverrideValue(accessMode),
            blockId);
    }

    @Transactional
    public void setClassIrregularAccessOverride(int sectionId, String accessMode) {
        ensureSchema();
        db.update(
            "UPDATE class_sections SET irregular_access_override = ? WHERE section_id = ?",
            normalizeOverrideValue(accessMode),
            sectionId);
    }

    @Transactional
    public void setProgramYearSemesterIrregularAccess(int termId, String programCode,
                                                      int yearLevel, int semesterNumber,
                                                      boolean open) {
        ensureSchema();
        String program = programCode != null ? programCode.trim().toUpperCase() : "";
        if (program.isEmpty()) {
            throw new IllegalArgumentException("Program code is required.");
        }
        if (!open) {
            db.update(
                "DELETE FROM irregular_block_access_policies WHERE term_id = ? AND program_code = ? " +
                    "AND year_level = ? AND semester_number = ?",
                termId, program, yearLevel, semesterNumber);
            return;
        }
        int updated = db.update(
            "UPDATE irregular_block_access_policies SET irregular_open = 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE term_id = ? AND program_code = ? AND year_level = ? AND semester_number = ?",
            termId, program, yearLevel, semesterNumber);
        if (updated == 0) {
            db.update(
                "INSERT INTO irregular_block_access_policies " +
                    "(term_id, program_code, year_level, semester_number, irregular_open, updated_at) " +
                    "VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)",
                termId, program, yearLevel, semesterNumber);
        }
    }

    public IrregularAccessDecision resolveSectionIrregularAccess(int sectionId) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT cs.section_id, cs.term_id, cs.section_code, cs.block_id, " +
                "cs.irregular_access_override AS class_irregular_access_override, " +
                "bo.program_code, bo.year_level, bo.semester_number, " +
                "bo.irregular_access_override AS block_irregular_access_override " +
                "FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "WHERE cs.section_id = ?",
            sectionId);
        if (rows.isEmpty()) {
            return new IrregularAccessDecision(false, "Closed", "MISSING_SECTION", false,
                null, null, false, null, null, null, null, null);
        }
        return resolveSectionIrregularAccess(rows.get(0));
    }

    public boolean isIrregularAccessOpenForSection(int sectionId) {
        return resolveSectionIrregularAccess(sectionId).open();
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
        if (!syncedLegacyBlockTerms.add(termId)) {
            return;
        }
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

    private void applyBlockIrregularAccessState(Map<String, Object> row) {
        Boolean blockOverride = decodeOverride(row.get("block_irregular_access_override"));
        Boolean programPolicyOpen = row.containsKey("program_irregular_open")
            ? decodeOverride(row.get("program_irregular_open"))
            : resolveProgramYearSemesterOpen(
                numberValue(row.get("term_id")),
                text(row.get("program_code")),
                numberValue(row.get("year_level")),
                numberValue(row.get("semester_number")));
        IrregularAccessDecision decision = blockOverride != null
            ? new IrregularAccessDecision(blockOverride,
                blockOverride ? "Open by block" : "Closed by block override",
                blockOverride ? "BLOCK_OVERRIDE_OPEN" : "BLOCK_OVERRIDE_CLOSED",
                true, null, blockOverride, Boolean.TRUE.equals(programPolicyOpen), null,
                text(row.get("program_code")), numberValue(row.get("year_level")),
                numberValue(row.get("semester_number")), numberValue(row.get("term_id")))
            : Boolean.TRUE.equals(programPolicyOpen)
                ? new IrregularAccessDecision(true, "Open by program/year/semester",
                    "PROGRAM_YEAR_SEM_OPEN", true, null, null, true, null,
                    text(row.get("program_code")), numberValue(row.get("year_level")),
                    numberValue(row.get("semester_number")), numberValue(row.get("term_id")))
                : new IrregularAccessDecision(false, "Closed", "DEFAULT_CLOSED", true,
                    null, null, false, null, text(row.get("program_code")),
                    numberValue(row.get("year_level")), numberValue(row.get("semester_number")),
                    numberValue(row.get("term_id")));
        row.put("irregular_access_effective_open", decision.open());
        row.put("irregular_access_state_label", decision.stateLabel());
        row.put("irregular_access_state_source", decision.stateSource());
        row.put("irregular_access_override_mode", encodeOverride(decision.blockOverride()));
        row.put("program_year_semester_access_mode", decision.programPolicyOpen() ? "OPEN" : "CLOSED");
    }

    private void applySectionIrregularAccessState(Map<String, Object> row) {
        IrregularAccessDecision decision = resolveSectionIrregularAccess(row);
        row.put("irregular_access_effective_open", decision.open());
        row.put("irregular_access_state_label", decision.stateLabel());
        row.put("irregular_access_state_source", decision.stateSource());
        row.put("irregular_access_override_mode", encodeOverride(decision.classOverride()));
    }

    private IrregularAccessDecision resolveSectionIrregularAccess(Map<String, Object> row) {
        Integer termId = numberValue(row.get("term_id"));
        String sectionCode = text(row.get("section_code"));
        ParsedBlock parsed = parseBlockCode(sectionCode);
        Integer blockId = numberValue(row.get("block_id"));
        Boolean classOverride = decodeOverride(row.get("class_irregular_access_override"));
        String programCode = text(row.get("program_code"));
        Integer yearLevel = numberValue(row.get("year_level"));
        Integer semesterNumber = numberValue(row.get("semester_number"));
        Boolean blockOverride = decodeOverride(row.get("block_irregular_access_override"));

        if ((programCode == null || yearLevel == null || semesterNumber == null) && parsed != null) {
            programCode = programCode != null ? programCode : parsed.programCode();
            yearLevel = yearLevel != null ? yearLevel : parsed.yearLevel();
            semesterNumber = semesterNumber != null ? semesterNumber : parsed.semesterNumber();
        }

        if (blockId == null && parsed != null && termId != null) {
            try {
                Map<String, Object> blockRow = db.queryForMap(
                    "SELECT block_id, irregular_access_override FROM block_offerings WHERE term_id = ? AND program_code = ? " +
                        "AND year_level = ? AND semester_number = ? AND section_group = ?",
                    termId, parsed.programCode(), parsed.yearLevel(), parsed.semesterNumber(), parsed.sectionGroup());
                blockId = numberValue(blockRow.get("block_id"));
                blockOverride = decodeOverride(blockRow.get("irregular_access_override"));
            } catch (Exception ignored) {
            }
        }

        boolean blockManaged = blockId != null || parsed != null;
        if (!blockManaged) {
            return new IrregularAccessDecision(true, "Special section", "NON_BLOCK_SECTION", false,
                classOverride, blockOverride, false, blockId, programCode, yearLevel, semesterNumber, termId);
        }
        Boolean programPolicyOpen = resolveProgramYearSemesterOpen(termId, programCode, yearLevel, semesterNumber);
        if (classOverride != null) {
            return new IrregularAccessDecision(classOverride,
                classOverride ? "Open by class override" : "Closed by class override",
                classOverride ? "CLASS_OVERRIDE_OPEN" : "CLASS_OVERRIDE_CLOSED",
                true, classOverride, blockOverride, Boolean.TRUE.equals(programPolicyOpen),
                blockId, programCode, yearLevel, semesterNumber, termId);
        }
        if (blockOverride != null) {
            return new IrregularAccessDecision(blockOverride,
                blockOverride ? "Open by block" : "Closed by block override",
                blockOverride ? "BLOCK_OVERRIDE_OPEN" : "BLOCK_OVERRIDE_CLOSED",
                true, classOverride, blockOverride, Boolean.TRUE.equals(programPolicyOpen),
                blockId, programCode, yearLevel, semesterNumber, termId);
        }
        if (Boolean.TRUE.equals(programPolicyOpen)) {
            return new IrregularAccessDecision(true, "Open by program/year/semester",
                "PROGRAM_YEAR_SEM_OPEN", true, classOverride, blockOverride, true,
                blockId, programCode, yearLevel, semesterNumber, termId);
        }
        return new IrregularAccessDecision(false, "Closed", "DEFAULT_CLOSED", true,
            classOverride, blockOverride, false, blockId, programCode, yearLevel, semesterNumber, termId);
    }

    private IrregularAccessDecision resolveBlockIrregularAccess(Integer termId, String programCode,
                                                                Integer yearLevel, Integer semesterNumber,
                                                                Boolean blockOverride) {
        Boolean programPolicyOpen = resolveProgramYearSemesterOpen(termId, programCode, yearLevel, semesterNumber);
        if (blockOverride != null) {
            return new IrregularAccessDecision(blockOverride,
                blockOverride ? "Open by block" : "Closed by block override",
                blockOverride ? "BLOCK_OVERRIDE_OPEN" : "BLOCK_OVERRIDE_CLOSED",
                true, null, blockOverride, Boolean.TRUE.equals(programPolicyOpen),
                null, programCode, yearLevel, semesterNumber, termId);
        }
        if (Boolean.TRUE.equals(programPolicyOpen)) {
            return new IrregularAccessDecision(true, "Open by program/year/semester",
                "PROGRAM_YEAR_SEM_OPEN", true, null, blockOverride, true,
                null, programCode, yearLevel, semesterNumber, termId);
        }
        return new IrregularAccessDecision(false, "Closed", "DEFAULT_CLOSED", true,
            null, blockOverride, false, null, programCode, yearLevel, semesterNumber, termId);
    }

    private Boolean resolveProgramYearSemesterOpen(Integer termId, String programCode,
                                                   Integer yearLevel, Integer semesterNumber) {
        if (termId == null || programCode == null || programCode.isBlank()
            || yearLevel == null || semesterNumber == null) {
            return null;
        }
        try {
            List<Integer> matches = db.queryForList(
                "SELECT irregular_open FROM irregular_block_access_policies WHERE term_id = ? AND program_code = ? " +
                    "AND year_level = ? AND semester_number = ?",
                Integer.class, termId, programCode.trim().toUpperCase(), yearLevel, semesterNumber);
            if (matches.isEmpty()) {
                return null;
            }
            return matches.get(0) != null && matches.get(0) != 0;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer normalizeOverrideValue(String accessMode) {
        String normalized = accessMode != null
            ? accessMode.trim().toUpperCase(java.util.Locale.ROOT)
            : "AUTO";
        return switch (normalized) {
            case "AUTO" -> null;
            case "OPEN" -> 1;
            case "CLOSED" -> 0;
            default -> throw new IllegalArgumentException("Invalid irregular access mode.");
        };
    }

    private static Boolean decodeOverride(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("1".equals(text) || "TRUE".equalsIgnoreCase(text) || "OPEN".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "FALSE".equalsIgnoreCase(text) || "CLOSED".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private static String encodeOverride(Boolean override) {
        if (override == null) {
            return "AUTO";
        }
        return override ? "OPEN" : "CLOSED";
    }

    private static Integer numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
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
    public record IrregularAccessDecision(boolean open,
                                          String stateLabel,
                                          String stateSource,
                                          boolean blockManaged,
                                          Boolean classOverride,
                                          Boolean blockOverride,
                                          boolean programPolicyOpen,
                                          Integer blockId,
                                          String programCode,
                                          Integer yearLevel,
                                          Integer semesterNumber,
                                          Integer termId) {}
}
