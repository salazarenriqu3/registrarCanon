package com.iuims.registrar.service.academic;

import com.iuims.registrar.service.support.EnlistmentSchemaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlotMonitoringService {

    private final JdbcTemplate db;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final AcademicGradingService academicGradingService;

    public SlotMonitoringService(JdbcTemplate db,
                                 EnlistmentSchemaService enlistmentSchemaService,
                                 AcademicGradingService academicGradingService) {
        this.db = db;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.academicGradingService = academicGradingService;
    }

    public List<Map<String, Object>> listSectionsForTerm(int termId, String search) {
        return listSectionsForTerm(termId, search, null);
    }

    public List<Map<String, Object>> listSectionsForTerm(int termId, String search, String programCode) {
        String query = search != null ? search.trim().toUpperCase(Locale.ROOT) : "";
        String selectedProgram = normalizeProgramCode(programCode);
        List<Object> args = new ArrayList<>();
        args.add(termId);

        String committedFilter = enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se");
        String stagedFilter = enlistmentSchemaService.hasEnlistmentStatusColumn()
            ? " AND se.enlistment_status = 'STAGED'"
            : " AND 1=0";

        StringBuilder sql = new StringBuilder(
                "SELECT cs.section_id, cs.section_code, cs.max_capacity, cs.section_status, cs.block_id, " +
                "c.course_id, c.course_code, c.course_title, " +
                "COALESCE(at.term_name, CONCAT('Term #', cs.term_id)) AS term_label, " +
                "COALESCE(committed.enrolled_count, 0) AS enrolled_count, " +
                "COALESCE(staged.prereg_count, 0) AS prereg_count, " +
                "bo.program_code, bo.section_group AS block_section " +
                "FROM class_sections cs " +
                "JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN academic_terms at ON at.term_id = cs.term_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "LEFT JOIN (SELECT se.section_id, COUNT(*) AS enrolled_count " +
                "FROM student_enlistments se WHERE 1=1" + committedFilter + " GROUP BY se.section_id) committed " +
                "ON committed.section_id = cs.section_id " +
                "LEFT JOIN (SELECT se.section_id, COUNT(*) AS prereg_count " +
                "FROM student_enlistments se WHERE 1=1" + stagedFilter + " GROUP BY se.section_id) staged " +
                "ON staged.section_id = cs.section_id " +
                "WHERE cs.term_id = ? ");

        if (!query.isBlank()) {
            sql.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ? OR UPPER(cs.section_code) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        if (!selectedProgram.isBlank()) {
            sql.append("AND (UPPER(bo.program_code) = ? OR UPPER(cs.section_code) LIKE ?) ");
            args.add(selectedProgram);
            args.add(selectedProgram + "-%");
        }
        sql.append("ORDER BY c.course_code, cs.section_code");

        List<Map<String, Object>> rows = db.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            int cap = intVal(row.get("max_capacity"), 40);
            int enrolled = intVal(row.get("enrolled_count"), 0);
            int prereg = intVal(row.get("prereg_count"), 0);
            row.put("slots_left", Math.max(0, cap - enrolled - prereg));
            row.put("is_full", enrolled + prereg >= cap);
            row.put("is_closed", isClosedStatus(String.valueOf(row.get("section_status"))));
        }
        return rows;
    }

    public Map<String, Object> summary(int termId, String programCode) {
        String selectedProgram = normalizeProgramCode(programCode);
        String committedFilter = enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se");
        String stagedFilter = enlistmentSchemaService.hasEnlistmentStatusColumn()
            ? " AND se.enlistment_status = 'STAGED'"
            : " AND 1=0";
        List<Object> args = new ArrayList<>();
        args.add(termId);
        StringBuilder sql = new StringBuilder(
            "SELECT cs.section_id, cs.max_capacity, cs.section_status, " +
                "COALESCE(committed.enrolled_count, 0) AS enrolled_count, " +
                "COALESCE(staged.prereg_count, 0) AS prereg_count " +
                "FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "LEFT JOIN (" +
                "    SELECT se.section_id, COUNT(*) AS enrolled_count " +
                "    FROM student_enlistments se WHERE 1=1" + committedFilter +
                "    GROUP BY se.section_id" +
                ") committed ON committed.section_id = cs.section_id " +
                "LEFT JOIN (" +
                "    SELECT se.section_id, COUNT(*) AS prereg_count " +
                "    FROM student_enlistments se WHERE 1=1" + stagedFilter +
                "    GROUP BY se.section_id" +
                ") staged ON staged.section_id = cs.section_id " +
                "WHERE cs.term_id = ?");
        if (!selectedProgram.isBlank()) {
            sql.append(" AND (UPPER(bo.program_code) = ? OR UPPER(cs.section_code) LIKE ?)");
            args.add(selectedProgram);
            args.add(selectedProgram + "-%");
        }
        List<Map<String, Object>> sections = db.queryForList(sql.toString(), args.toArray());
        int open = 0;
        int closed = 0;
        int full = 0;
        for (Map<String, Object> row : sections) {
            int cap = intVal(row.get("max_capacity"), 40);
            int enrolled = intVal(row.get("enrolled_count"), 0);
            int prereg = intVal(row.get("prereg_count"), 0);
            if (isClosedStatus(String.valueOf(row.get("section_status")))) {
                closed++;
            } else {
                open++;
            }
            if (enrolled + prereg >= cap) {
                full++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", sections.size());
        result.put("open", open);
        result.put("closed", closed);
        result.put("full", full);
        return result;
    }

    public List<Map<String, Object>> listProgramsForFilter(int termId) {
        return db.queryForList(
            "SELECT DISTINCT UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) AS program_code, " +
                "COALESCE(p.program_name, UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1)))) AS program_name " +
                "FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "LEFT JOIN programs p ON UPPER(p.program_code) = UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) " +
                "WHERE cs.term_id = ? " +
                "AND COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1)) IS NOT NULL " +
                "AND TRIM(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) <> '' " +
                "ORDER BY program_code",
            termId);
    }

    @Transactional
    public String updateCapacity(int sectionId, int maxCapacity) {
        if (maxCapacity < 1 || maxCapacity > 500) {
            return "ERROR: Capacity must be between 1 and 500.";
        }
        int changed = db.update("UPDATE class_sections SET max_capacity = ? WHERE section_id = ?", maxCapacity, sectionId);
        return changed > 0 ? "SUCCESS" : "ERROR: Section not found.";
    }

    @Transactional
    public String closeSection(int sectionId) {
        return academicGradingService.closeSection(sectionId);
    }

    @Transactional
    public String bulkClose(int termId, List<Integer> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return "ERROR: No sections selected.";
        }
        int ok = 0;
        for (Integer sectionId : sectionIds) {
            if (sectionId != null && "SUCCESS".equals(academicGradingService.closeSection(sectionId))) {
                ok++;
            }
        }
        return "SUCCESS: Closed " + ok + " of " + sectionIds.size() + " section(s).";
    }

    private boolean isClosedStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("CLOSED") || normalized.equals("DISSOLVED");
    }

    private int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String normalizeProgramCode(String programCode) {
        return programCode != null ? programCode.trim().toUpperCase(Locale.ROOT) : "";
    }
}
