package com.iuims.registrar.academic;

import com.iuims.registrar.core.EnlistmentSchemaService;
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
        String query = search != null ? search.trim().toUpperCase(Locale.ROOT) : "";
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
                "(SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = cs.section_id" + committedFilter + ") AS enrolled_count, " +
                "(SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = cs.section_id" + stagedFilter + ") AS prereg_count, " +
                "bo.program_code, bo.section_group AS block_section " +
                "FROM class_sections cs " +
                "JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN academic_terms at ON at.term_id = cs.term_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "WHERE cs.term_id = ? ");

        if (!query.isBlank()) {
            sql.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ? OR UPPER(cs.section_code) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
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

    public Map<String, Object> summary(int termId) {
        List<Map<String, Object>> sections = listSectionsForTerm(termId, null);
        int open = 0;
        int closed = 0;
        int full = 0;
        for (Map<String, Object> row : sections) {
            if (Boolean.TRUE.equals(row.get("is_closed"))) {
                closed++;
            } else {
                open++;
            }
            if (Boolean.TRUE.equals(row.get("is_full"))) {
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
}
