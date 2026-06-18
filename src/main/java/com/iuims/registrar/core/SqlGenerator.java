package com.iuims.registrar.core;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlGenerator {

    private static final Map<String, String> courseIdCache = new HashMap<>();
    private static final String[] SLOT_STARTS = {"'07:30:00'", "'09:30:00'", "'13:00:00'", "'15:00:00'"};
    private static final String[] SLOT_ENDS   = {"'09:00:00'", "'11:00:00'", "'14:30:00'", "'16:30:00'"};
    private static final int[]    SLOT_DAYS   = {1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5};
    private static final Map<String, Integer> scheduleIndexMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        File curriculumsDir = new File("src/main/resources/curriculums");
        if (!curriculumsDir.exists()) {
            System.err.println("Cannot find " + curriculumsDir.getAbsolutePath());
            return;
        }

        File outFile = new File("db/04_seed_full_curriculum.sql");
        outFile.getParentFile().mkdirs();
        try (PrintWriter out = new PrintWriter(outFile)) {
            out.println("-- Generated from curriculums folder");
            out.println("USE eacdb;");
            out.println("SET FOREIGN_KEY_CHECKS = 0;");
            out.println("SET SQL_SAFE_UPDATES = 0;");
            out.println();

            // Departments
            out.println("-- DEPARTMENTS");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('GEN', 'General Education');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('NURS', 'Marian School of Nursing');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('NUTR', 'School of  Nutrition and Diabetics');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('ARTS', 'School of Arts and Sciences');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('BUS', 'School of Business Education');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('CRIM', 'School of Criminology');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('DENT', 'School of Dentistry');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('ENGR', 'School of Engineering and Technology');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('HOSP', 'School of Hospitality and Tourism Management');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('MED', 'School of Medical Technology');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('PHAR', 'School of Pharmacy');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('PHYS', 'School of Physical, Occupational, And Respiratory Therapy_');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('RAD', 'School of Radiologic Technology');");
            out.println("INSERT IGNORE INTO departments (department_code, department_name) VALUES ('EDUC', 'School of Teacher Education');");
            out.println();
            
            // Seed Universal Gen Eds
            out.println("-- UNIVERSAL GEN EDS");
            String[][] genEds = {
                {"AUS0 11", "Understanding the Self", "3"},
                {"ARPH 11", "Readings in Philippine History", "3"},
                {"ACW0 12", "The Contemporary World", "3"},
                {"SMMW 11", "Mathematics in the Modern World", "3"},
                {"APC0 12", "Purposive Communication", "3"},
                {"ANS1 11", "NSTP 1", "3"},
                {"ANS2 12", "NSTP 2", "3"},
                {"AET0 12", "Ethics", "3"},
                {"AHU1 11", "Arts Appreciation", "3"},
                {"ASS6 21", "The Life & Works of Rizal", "3"},
                {"AECO 11", "Emilian Culture", "1"}
            };
            for (String[] ge : genEds) {
                out.printf("INSERT IGNORE INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, active_status) VALUES ('%s', '%s', %s, %s, 0, 1, 1);%n",
                        ge[0], ge[1].replace("'", "''"), ge[2], ge[2]);
            }
            out.println();

            processDirectory(curriculumsDir, out);

            out.println();
            out.println("SET FOREIGN_KEY_CHECKS = 1;");
            out.println("SET SQL_SAFE_UPDATES = 1;");
        }
        System.out.println("Successfully generated db/04_seed_full_curriculum.sql");
    }

    private static void processDirectory(File dir, PrintWriter out) throws Exception {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                processDirectory(file, out);
            } else if (file.getName().endsWith(".docx") && !file.getName().startsWith("~")) {
                String schoolName = dir.getName();
                processCurriculumResource(schoolName, file, out);
            }
        }
    }

    private static void processCurriculumResource(String schoolName, File file, PrintWriter out) throws Exception {
        String fileName = file.getName();
        String displayName = fileName.replace(".docx", "");
        String programCode = generateProgramCode(displayName);

        out.println("-- PROGRAM: " + displayName);
        out.printf("INSERT IGNORE INTO programs (program_code, program_name, school_name) VALUES ('%s', '%s', '%s');%n",
                programCode, displayName.replace("'", "''"), schoolName.replace("'", "''"));

        String currName = displayName + " Curriculum";
        out.printf("INSERT IGNORE INTO curriculum_templates (program_id, curriculum_name, academic_year, version_number, approval_status, is_active) " +
                "SELECT program_id, '%s', '2024-2025', 1, 'Draft', 1 FROM programs WHERE program_code = '%s';%n",
                currName.replace("'", "''"), programCode);

        try (FileInputStream is = new FileInputStream(file); XWPFDocument doc = new XWPFDocument(is)) {
            int year = 1, sem = 1;
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    String text = ((XWPFParagraph) element).getText().toUpperCase().trim();
                    if (text.contains("FIRST YEAR")) year = 1;
                    else if (text.contains("SECOND YEAR")) year = 2;
                    else if (text.contains("THIRD YEAR")) year = 3;
                    else if (text.contains("FOURTH YEAR")) year = 4;
                    else if (text.contains("FIFTH YEAR")) year = 5;
                    if (text.contains("FIRST SEMESTER")) sem = 1;
                    else if (text.contains("SECOND SEMESTER")) sem = 2;
                    else if (text.contains("SUMMER") || text.contains("MIDYEAR")) sem = 3;
                } else if (element instanceof XWPFTable) {
                    parseTable((XWPFTable) element, programCode, year, sem, out);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse " + file.getName() + ": " + e.getMessage());
        }
        out.println();
    }

    private static void parseTable(XWPFTable table, String programCode, int year, int sem, PrintWriter out) {
        boolean headerFound = false;
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.size() < 3) continue;

            String col0 = cells.get(0).getText().trim();
            if (col0.equalsIgnoreCase("Course Code") || col0.equalsIgnoreCase("Subject Code")) {
                headerFound = true;
                continue;
            }
            if (!headerFound || col0.isEmpty() || col0.equalsIgnoreCase("TOTAL")) continue;

            String courseCode = col0;
            String courseTitle = cells.get(1).getText().trim().replace("'", "''");
            int units = parseUnits(cells);
            String preReqCode = cells.get(cells.size() - 1).getText().trim();
            if (preReqCode.equalsIgnoreCase("NONE") || preReqCode.isEmpty()) preReqCode = null;

            saveCourseAndMapping(programCode, courseCode, courseTitle, units, preReqCode, year, sem, out);
        }
    }

    private static int parseUnits(List<XWPFTableCell> cells) {
        for (int i = 2; i < Math.min(5, cells.size()); i++) {
            try { return Integer.parseInt(cells.get(i).getText().trim().split(" ")[0]); }
            catch (Exception ignored) {}
        }
        return 3;
    }

    private static void saveCourseAndMapping(String programCode, String courseCode, String courseTitle, int units, String preReqCode, int yearLevel, int semester, PrintWriter out) {
        out.printf("INSERT IGNORE INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, active_status) VALUES ('%s', '%s', %d, %d, 0, 1, 1);%n",
                courseCode.replace("'", "''"), courseTitle, units, units);

        out.printf("INSERT IGNORE INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) " +
                "SELECT ct.curriculum_id, c.course_id, %d, %d, 1 FROM courses c CROSS JOIN curriculum_templates ct " +
                "JOIN programs p ON p.program_id = ct.program_id " +
                "WHERE c.course_code = '%s' AND p.program_code = '%s';%n",
                yearLevel, semester, courseCode.replace("'", "''"), programCode);

        if (preReqCode != null && !preReqCode.isEmpty() && !preReqCode.equalsIgnoreCase("NONE")) {
            String[] preReqs = preReqCode.split(",");
            for (String pr : preReqs) {
                pr = pr.trim();
                out.printf("INSERT IGNORE INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, description, active_status) VALUES ('%s', '%s', 3, 3, 0, 1, 'Prerequisite placeholder', 1);%n",
                        pr.replace("'", "''"), pr.replace("'", "''"));
                
                out.printf("INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id) " +
                        "SELECT c1.course_id, c2.course_id FROM courses c1, courses c2 " +
                        "WHERE c1.course_code = '%s' AND c2.course_code = '%s';%n",
                        courseCode.replace("'", "''"), pr.replace("'", "''"));
            }
        }

        // AutoCreateSection
        String sectionCode = programCode.toUpperCase() + "-" + yearLevel + "-" + semester;
        out.printf("INSERT IGNORE INTO class_sections (course_id, term_id, section_code, max_capacity, semester_number) " +
                "SELECT c.course_id, 1, '%s', 40, %d FROM courses c WHERE c.course_code = '%s';%n",
                sectionCode, semester, courseCode.replace("'", "''"));

        String slotKey = programCode + "-" + yearLevel + "-" + semester;
        int slotIdx = scheduleIndexMap.getOrDefault(slotKey, 0);
        int day = SLOT_DAYS[slotIdx % 20];
        String start = SLOT_STARTS[(slotIdx / 5) % 4];
        String end = SLOT_ENDS[(slotIdx / 5) % 4];

        out.printf("INSERT IGNORE INTO class_schedules (section_id, day_of_week, start_time, end_time, schedule_type) " +
                "SELECT cs.section_id, %d, %s, %s, 'Lecture' FROM class_sections cs JOIN courses c ON cs.course_id = c.course_id " +
                "WHERE cs.section_code = '%s' AND c.course_code = '%s';%n",
                day, start, end, sectionCode, courseCode.replace("'", "''"));

        scheduleIndexMap.put(slotKey, slotIdx + 1);
    }

    private static String generateProgramCode(String fileName) {
        String clean = fileName.toUpperCase().replace(" ", "");
        if (clean.contains("BSIT") || clean.contains("INFORMATIONTECHNOLOGY")) return "BSIT";
        if (clean.contains("NURSING"))       return "BSN";
        if (clean.contains("ACCOUNTANCY"))   return "BSA";
        if (clean.contains("PSYCHOLOGY"))    return "BSPSYCH";
        if (clean.contains("CRIMINOLOGY"))   return "BSCRIM";
        if (clean.contains("BIOLOGY"))       return "BSBIO";
        if (clean.contains("COE") || clean.contains("COMPUTERENGINEERING")) return "BSCPE";
        if (clean.contains("MEDTECH") || clean.contains("MEDICALTECHNOLOGY")) return "BSMedTech";
        if (clean.contains("PHARMACY"))      return "BSPHARM";
        if (clean.contains("PHYSICAL"))      return "BSPT";
        if (clean.contains("RADIOLOG"))      return "BSRT";
        if (clean.contains("OCCUPATIONAL"))  return "BSOT";
        StringBuilder code = new StringBuilder();
        for (String word : fileName.split(" ")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0)))
                code.append(Character.toUpperCase(word.charAt(0)));
        }
        return code.length() > 0 ? code.toString() : "PROG-X";
    }
}



