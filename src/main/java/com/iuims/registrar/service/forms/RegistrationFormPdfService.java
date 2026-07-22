package com.iuims.registrar.service.forms;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.service.finance.FinancePolicyService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RegistrationFormPdfService {

    private static final Pattern AY_PATTERN = Pattern.compile("A\\.Y\\.\\s*\\d{4}-\\d{4}", Pattern.CASE_INSENSITIVE);

    private static final float PAGE_W = PDRectangle.LETTER.getWidth();
    private static final float PAGE_H = PDRectangle.LETTER.getHeight();
    private static final float ML = 36f;
    private static final float CW = PAGE_W - 2 * ML;
    private static final float FOOTER_Y = 26f;
    private static final float TOP_Y = PAGE_H - 42f;

    private static final float FEE_W = CW * 0.575f;
    private static final float GAP = 8f;
    private static final float PAY_X = ML + FEE_W + GAP;
    private static final float PAY_W = CW - FEE_W - GAP;

    private static final String COLLEGE = "Emilio Aguinaldo College";
    private static final String ADDRESS =
        "1113-1117 San Marcelino cor. Gonzales St. Brgy 674, Zone 73 Dist. V. Paco, Manila";

    private static final String[] RULES = {
        "1. When a student registers in the College, it is understood that he/she is enrolling for the entire semester.",
        "2. A student who transfer or otherwise withdraws, within two (2) weeks after the beginning of classes and who has already paid the pertinent tuition and other school fees in full or any length of time longer than one (1) month may be charged twenty five percent (25%) of amount due for the term of he/she withdraws within the first week of classes or fifty percent (50%) of amount due for the term if within the second week of classes, regardless of whether or not he/she has actually attended the classes. The student may be charged all the school fees in full if he/she withdraws anytime after the second week of classes.",
        "3. If the transfer or withdrawal is due to a justifiable reason, the student shall be charged the pertinent fees only up to and including the last month of attendance.",
        "4. Registration and other pertinent fees are not refundable."
    };

    private final JdbcTemplate db;
    private final TermFeeAdminService termFeeAdminService;
    private final FinancePolicyService financePolicyService;

    public RegistrationFormPdfService(
        JdbcTemplate db,
        TermFeeAdminService termFeeAdminService,
        FinancePolicyService financePolicyService
    ) {
        this.db = db;
        this.termFeeAdminService = termFeeAdminService;
        this.financePolicyService = financePolicyService;
    }

    public byte[] render(
        Map<String, Object> student,
        List<Map<String, Object>> studentLoad,
        Map<String, Object> finance,
        String currentTermLabel,
        String printedBy
    ) {
        RegistrationFormData data = buildData(student, studentLoad, finance, currentTermLabel, printedBy);
        return renderData(data);
    }

    public Map<String, Object> buildSnapshot(
        Map<String, Object> student,
        List<Map<String, Object>> studentLoad,
        Map<String, Object> finance,
        String currentTermLabel,
        String printedBy
    ) {
        return snapshotMap(buildData(student, studentLoad, finance, currentTermLabel, printedBy));
    }

    public byte[] renderSnapshot(Map<String, Object> snapshot) {
        return renderData(snapshotData(snapshot));
    }

    private byte[] renderData(RegistrationFormData data) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = new Fonts();

            PageCursor p1 = new PageCursor(doc, fonts, data, 1, 2);
            drawPageHeader(p1);
            drawStudentBox(p1);
            drawSubjectsTable(p1, data.subjects());
            drawFeesAndPayment(p1, data);
            drawRulesTitle(p1);
            p1.y = wrappedText(p1.cs, fonts.regular, 7.5f, ML, p1.y - 3, CW, RULES[0]) - 2;
            drawPageFooter(p1);

            PageCursor p2 = new PageCursor(doc, fonts, data, 2, 2);
            drawPageHeader(p2);
            drawStudentBox(p2);
            p2.y -= 10;
            for (int i = 1; i < RULES.length; i++) {
                p2.y = wrappedText(p2.cs, fonts.regular, 7.5f, ML, p2.y, CW, RULES[i]) - 7;
            }
            drawPageFooter(p2);

            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate registration form PDF: " + ex.getMessage(), ex);
        }
    }

    private RegistrationFormData buildData(
        Map<String, Object> student,
        List<Map<String, Object>> studentLoad,
        Map<String, Object> finance,
        String currentTermLabel,
        String printedBy
    ) {
        String studentNumber = text(student.get("username"));
        Map<String, Object> profile = loadStudentProfile(studentNumber);
        Integer termId = termFeeAdminService.getActiveTermId();
        Map<String, Object> termRow = loadActiveTerm(termId);
        int yearLevel = intValue(student.get("year_level"), 1);
        int semester = intValue(student.get("semester"), intValue(termRow.get("semester_number"), 1));
        String programCode = text(student.get("program_code"));
        String programName = loadProgramName(programCode);
        String semesterLabel = resolveSemesterLabel(termRow, semester, currentTermLabel);
        String schoolYear = text(termRow.get("academic_year"));
        String semesterSchoolYearLine = buildSemesterSchoolYearLine(termRow, semesterLabel, schoolYear, currentTermLabel);
        String fullName = coalesce(text(profile.get("real_name")), text(student.get("real_name")));

        Map<String, Double> miscFees = new LinkedHashMap<>();
        Map<String, Double> otherFees = new LinkedHashMap<>();
        populateFeeBreakdown(programCode, termId, yearLevel, semester, miscFees, otherFees);

        double forwardedBalance = doubleValue(finance.get("balance_forwarded"));
        if (forwardedBalance > 0.009d) {
            otherFees.put("Forwarded Balance", forwardedBalance);
        }
        double withdrawalCharges = doubleValue(finance.get("withdrawal_charges"));
        if (withdrawalCharges > 0.009d) {
            otherFees.put("Withdrawal Charges", withdrawalCharges);
        }

        double totalAssessment = doubleValue(finance.get("total_assessment"));
        double totalPaid = doubleValue(finance.get("total_paid"));
        double balance = doubleValue(finance.get("balance"));
        double downpaymentRequired = requiredDownpayment(totalAssessment);
        List<InstallmentLine> installments = buildInstallments(studentNumber, termId, totalAssessment, downpaymentRequired);

        List<SubjectLine> subjects = new ArrayList<>();
        double totalUnits = 0.0;
        for (Map<String, Object> loadRow : studentLoad) {
            double units = doubleValue(loadRow.get("units"));
            totalUnits += units;
            subjects.add(new SubjectLine(
                text(loadRow.get("course_code")),
                text(loadRow.get("description")),
                coalesce(text(loadRow.get("pretty_schedule")), "TBA"),
                text(loadRow.get("section")),
                units,
                printedBy
            ));
        }

        return new RegistrationFormData(
            studentNumber,
            resolveStatusLabel(student, finance),
            text(profile.get("last_name")),
            text(profile.get("first_name")),
            text(profile.get("middle_name")),
            fullName,
            parseAge(profile.get("dob")),
            normalizeSex(profile.get("sex")),
            programCode,
            programName,
            buildCourseYearLine(programCode, yearLevel),
            semesterSchoolYearLine,
            schoolYear,
            semesterLabel,
            subjects,
            totalUnits,
            doubleValue(finance.get("tuition_fee")),
            miscFees,
            otherFees,
            totalAssessment,
            downpaymentRequired,
            totalPaid,
            balance,
            installments,
            hasText(printedBy) ? printedBy : "registrar",
            LocalDateTime.now()
        );
    }

    private Map<String, Object> snapshotMap(RegistrationFormData data) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("referenceNumber", data.referenceNumber());
        snapshot.put("statusLabel", data.statusLabel());
        snapshot.put("lastName", data.lastName());
        snapshot.put("firstName", data.firstName());
        snapshot.put("middleName", data.middleName());
        snapshot.put("fullName", data.fullName());
        snapshot.put("age", data.age());
        snapshot.put("gender", data.gender());
        snapshot.put("programCode", data.programCode());
        snapshot.put("programName", data.programName());
        snapshot.put("courseYearLine", data.courseYearLine());
        snapshot.put("semesterSchoolYearLine", data.semesterSchoolYearLine());
        snapshot.put("schoolYear", data.schoolYear());
        snapshot.put("semesterLabel", data.semesterLabel());
        snapshot.put("totalUnits", data.totalUnits());
        snapshot.put("tuitionAmount", data.tuitionAmount());
        snapshot.put("miscFees", new LinkedHashMap<>(data.miscFees()));
        snapshot.put("otherFees", new LinkedHashMap<>(data.otherFees()));
        snapshot.put("totalAssessment", data.totalAssessment());
        snapshot.put("downpaymentRequired", data.downpaymentRequired());
        snapshot.put("totalPaid", data.totalPaid());
        snapshot.put("balance", data.balance());
        snapshot.put("printedBy", data.printedBy());
        snapshot.put("printedAt", data.printedAt().toString());
        List<Map<String, Object>> subjects = new ArrayList<>();
        for (SubjectLine subject : data.subjects()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseCode", subject.courseCode());
            row.put("description", subject.description());
            row.put("schedule", subject.schedule());
            row.put("section", subject.section());
            row.put("units", subject.units());
            row.put("registeredBy", subject.registeredBy());
            subjects.add(row);
        }
        snapshot.put("subjects", subjects);
        List<Map<String, Object>> installments = new ArrayList<>();
        for (InstallmentLine line : data.installments()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", line.label());
            row.put("amountDue", line.amountDue());
            installments.add(row);
        }
        snapshot.put("installments", installments);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private RegistrationFormData snapshotData(Map<String, Object> snapshot) {
        Map<String, Double> miscFees = new LinkedHashMap<>();
        Object miscRaw = snapshot.get("miscFees");
        if (miscRaw instanceof Map<?, ?> miscMap) {
            miscMap.forEach((key, value) -> miscFees.put(String.valueOf(key), doubleValue(value)));
        }

        Map<String, Double> otherFees = new LinkedHashMap<>();
        Object otherRaw = snapshot.get("otherFees");
        if (otherRaw instanceof Map<?, ?> otherMap) {
            otherMap.forEach((key, value) -> otherFees.put(String.valueOf(key), doubleValue(value)));
        }

        List<SubjectLine> subjects = new ArrayList<>();
        Object subjectsRaw = snapshot.get("subjects");
        if (subjectsRaw instanceof List<?> subjectRows) {
            for (Object row : subjectRows) {
                if (!(row instanceof Map<?, ?> subjectMap)) {
                    continue;
                }
                subjects.add(new SubjectLine(
                    text(subjectMap.get("courseCode")),
                    text(subjectMap.get("description")),
                    text(subjectMap.get("schedule")),
                    text(subjectMap.get("section")),
                    doubleValue(subjectMap.get("units")),
                    text(subjectMap.get("registeredBy"))
                ));
            }
        }

        List<InstallmentLine> installments = new ArrayList<>();
        Object installmentsRaw = snapshot.get("installments");
        if (installmentsRaw instanceof List<?> installmentRows) {
            for (Object row : installmentRows) {
                if (!(row instanceof Map<?, ?> installmentMap)) {
                    continue;
                }
                installments.add(new InstallmentLine(
                    text(installmentMap.get("label")),
                    doubleValue(installmentMap.get("amountDue"))
                ));
            }
        }

        LocalDateTime printedAt;
        try {
            printedAt = LocalDateTime.parse(text(snapshot.get("printedAt")));
        } catch (Exception ignored) {
            printedAt = LocalDateTime.now();
        }

        return new RegistrationFormData(
            text(snapshot.get("referenceNumber")),
            text(snapshot.get("statusLabel")),
            text(snapshot.get("lastName")),
            text(snapshot.get("firstName")),
            text(snapshot.get("middleName")),
            text(snapshot.get("fullName")),
            snapshot.get("age") instanceof Number age ? age.intValue() : null,
            text(snapshot.get("gender")),
            text(snapshot.get("programCode")),
            text(snapshot.get("programName")),
            text(snapshot.get("courseYearLine")),
            text(snapshot.get("semesterSchoolYearLine")),
            text(snapshot.get("schoolYear")),
            text(snapshot.get("semesterLabel")),
            subjects,
            doubleValue(snapshot.get("totalUnits")),
            doubleValue(snapshot.get("tuitionAmount")),
            miscFees,
            otherFees,
            doubleValue(snapshot.get("totalAssessment")),
            doubleValue(snapshot.get("downpaymentRequired")),
            doubleValue(snapshot.get("totalPaid")),
            doubleValue(snapshot.get("balance")),
            installments,
            text(snapshot.get("printedBy")),
            printedAt
        );
    }

    private void populateFeeBreakdown(
        String programCode,
        Integer termId,
        int yearLevel,
        int semester,
        Map<String, Double> miscFees,
        Map<String, Double> otherFees
    ) {
        Integer programId = termFeeAdminService.resolveProgramId(programCode);
        if (programId == null) {
            return;
        }
        Map<String, Double> rates = termFeeAdminService.getFeeRatesForScope(programId, termId, yearLevel, semester);
        if (rates.isEmpty()) {
            return;
        }
        for (Map<String, Object> feeType : termFeeAdminService.listFeeTypesForAdmin(programId, termId, yearLevel, semester)) {
            String code = text(feeType.get("fee_code"));
            Double amount = rates.get(code);
            if (amount == null || amount <= 0.0) {
                continue;
            }
            String label = text(feeType.get("fee_name"));
            if (code.startsWith("MISC_")) {
                miscFees.put(label, amount);
            } else if (code.startsWith("OTHER_")) {
                otherFees.put(label, amount);
            }
        }
    }

    private List<InstallmentLine> buildInstallments(
        String studentNumber,
        Integer termId,
        double totalAssessment,
        double downpaymentRequired
    ) {
        Map<String, Object> installmentView = financePolicyService.buildStudentInstallmentView(studentNumber, termId);
        Object rawPlan = installmentView.get("studentInstallmentPlan");
        if (!(rawPlan instanceof List<?> rows) || rows.isEmpty()) {
            return List.of();
        }

        double remaining = Math.max(0.0, round2(totalAssessment - downpaymentRequired));
        int count = rows.size();
        double perInstallment = count > 0 ? round2(remaining / count) : 0.0;
        List<InstallmentLine> out = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String label = map.get("installment_label") != null
                ? map.get("installment_label").toString()
                : "Installment " + (i + 1);
            double amount = i == rows.size() - 1
                ? round2(remaining - perInstallment * (rows.size() - 1))
                : perInstallment;
            out.add(new InstallmentLine(label, amount));
        }

        return out;
    }

    private double requiredDownpayment(double totalAssessment) {
        if (totalAssessment <= 0.0) {
            return 0.0;
        }
        double pct = PolicySettings.downpaymentPercent(db);
        if (pct > 0.0) {
            return Math.min(totalAssessment, round2(totalAssessment * pct / 100.0));
        }
        return Math.min(totalAssessment, PolicySettings.downpaymentThreshold(db));
    }

    private Map<String, Object> loadStudentProfile(String studentNumber) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.putAll(db.queryForMap(
                "SELECT student_number, reference_number, first_name, last_name, middle_name, real_name FROM students WHERE student_number = ? LIMIT 1",
                studentNumber
            ));
        } catch (Exception ignored) {
        }
        try {
            out.put("sex", db.queryForObject(
                "SELECT sex FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber
            ));
        } catch (Exception ignored) {
        }
        try {
            out.put("dob", db.queryForObject(
                "SELECT dob FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber
            ));
        } catch (Exception ignored) {
        }
        return out;
    }

    private Map<String, Object> loadActiveTerm(Integer termId) {
        if (termId == null) {
            return Map.of();
        }
        try {
            return db.queryForMap(
                "SELECT term_id, term_name, academic_year, semester_number FROM academic_terms WHERE term_id = ? LIMIT 1",
                termId
            );
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String loadProgramName(String programCode) {
        if (!hasText(programCode)) {
            return "";
        }
        try {
            return db.queryForObject(
                "SELECT program_name FROM programs WHERE program_code = ? LIMIT 1",
                String.class,
                programCode.trim()
            );
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveSemesterLabel(Map<String, Object> termRow, int semester, String currentTermLabel) {
        String termName = text(termRow.get("term_name"));
        int sem = intValue(termRow.get("semester_number"), semester);
        if (hasText(termName)) {
            if (termName.toLowerCase(Locale.ROOT).contains("1st semester")) return "First Semester";
            if (termName.toLowerCase(Locale.ROOT).contains("first semester")) return "First Semester";
            if (termName.toLowerCase(Locale.ROOT).contains("2nd semester")) return "Second Semester";
            if (termName.toLowerCase(Locale.ROOT).contains("second semester")) return "Second Semester";
            if (termName.toLowerCase(Locale.ROOT).contains("summer")) return "Summer";
            if (termName.toLowerCase(Locale.ROOT).contains("tutorial")) return "Tutorial";
        }
        if (hasText(currentTermLabel)) {
            String lower = currentTermLabel.toLowerCase(Locale.ROOT);
            if (lower.contains("1st semester") || lower.contains("first semester")) return "First Semester";
            if (lower.contains("2nd semester") || lower.contains("second semester")) return "Second Semester";
            if (lower.contains("summer")) return "Summer";
            if (lower.contains("tutorial")) return "Tutorial";
        }
        return switch (sem) {
            case 2 -> "Second Semester";
            case 3 -> "Summer";
            case 4 -> "Tutorial";
            default -> "First Semester";
        };
    }

    private String buildSemesterSchoolYearLine(
        Map<String, Object> termRow,
        String semesterLabel,
        String schoolYear,
        String currentTermLabel
    ) {
        String termName = text(termRow.get("term_name"));
        if (hasText(termName) && termName.toLowerCase(Locale.ROOT).contains("a.y.")) {
            return normalizeSemesterSchoolYearLine(termName);
        }
        if (hasText(currentTermLabel) && currentTermLabel.toLowerCase(Locale.ROOT).contains("a.y.")) {
            return normalizeSemesterSchoolYearLine(currentTermLabel);
        }
        if (schoolYear.isBlank()) {
            return semesterLabel;
        }
        return semesterLabel + ", A.Y. " + schoolYear;
    }

    private String normalizeSemesterSchoolYearLine(String raw) {
        String line = raw.trim();
        Matcher ayMatcher = AY_PATTERN.matcher(line);
        String ay = ayMatcher.find() ? ayMatcher.group().replaceAll("\\s+", " ").trim() : "";
        String semester = detectSemesterDisplay(line);
        if (hasText(ay) && hasText(semester)) {
            return semester + ", " + ay;
        }
        if (line.contains(" - ")) {
            String[] parts = line.split("\\s+-\\s+", 2);
            if (parts.length == 2 && parts[0].contains("A.Y.")) {
                return normalizeSemesterDisplay(parts[1]) + ", " + parts[0].trim();
            }
        }
        return normalizeSemesterDisplay(line);
    }

    private String normalizeSemesterDisplay(String raw) {
        return raw.trim()
            .replace("1st Semester", "First Semester")
            .replace("2nd Semester", "Second Semester");
    }

    private String detectSemesterDisplay(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("1st semester") || lower.contains("first semester")) return "First Semester";
        if (lower.contains("2nd semester") || lower.contains("second semester")) return "Second Semester";
        if (lower.contains("summer")) return "Summer";
        if (lower.contains("tutorial")) return "Tutorial";
        return "";
    }

    private String resolveStatusLabel(Map<String, Object> student, Map<String, Object> finance) {
        String studentType = text(student.get("student_type")).toUpperCase(Locale.ROOT);
        if (studentType.contains("NEW")) return "NEW";
        if (studentType.contains("OLD")) return "OLD";
        if (studentType.contains("TRANS")) return "TRANSFEREE";
        if (studentType.contains("RETURN")) return "RETURNEE";

        String enrollmentType = text(student.get("enrollment_status_type")).toUpperCase(Locale.ROOT);
        if (hasText(enrollmentType)) {
            return enrollmentType;
        }
        String admissionStatus = text(student.get("admission_status")).toUpperCase(Locale.ROOT);
        if (hasText(admissionStatus)) {
            return admissionStatus;
        }
        String financeStatus = text(finance.get("enrollment_status")).toUpperCase(Locale.ROOT);
        return hasText(financeStatus) ? financeStatus : "ENROLLED";
    }

    private String buildCourseYearLine(String programCode, int yearLevel) {
        return programCode + " - " + switch (yearLevel) {
            case 2 -> "Second Year";
            case 3 -> "Third Year";
            case 4 -> "Fourth Year";
            case 5 -> "Fifth Year";
            default -> "First Year";
        };
    }

    private Integer parseAge(Object dobValue) {
        String raw = dobValue != null ? dobValue.toString().trim() : "";
        if (raw.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-M-d")
        )) {
            try {
                LocalDate dob = LocalDate.parse(raw, formatter);
                if (dob.isAfter(LocalDate.now())) {
                    return null;
                }
                return Period.between(dob, LocalDate.now()).getYears();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String normalizeSex(Object sexValue) {
        String raw = sexValue != null ? sexValue.toString().trim() : "";
        if (raw.equalsIgnoreCase("M") || raw.equalsIgnoreCase("Male")) {
            return "Male";
        }
        if (raw.equalsIgnoreCase("F") || raw.equalsIgnoreCase("Female")) {
            return "Female";
        }
        return raw;
    }

    private static void drawPageHeader(PageCursor c) throws IOException {
        String pg = "Page " + c.pageNum + " of " + c.pageTotal;
        txt(c.cs, c.f.regular, 7f, PAGE_W - ML - tw(c.f.regular, 7f, pg), c.y, pg);
        txtCentered(c.cs, c.f.bold, 11f, c.y, COLLEGE);
        c.y -= 15;
        txtCentered(c.cs, c.f.regular, 7.5f, c.y, ADDRESS);
        c.y -= 7;
        hline(c.cs, ML, c.y, CW, 0.7f);
        c.y -= 12;
        txtCentered(c.cs, c.f.bold, 10.5f, c.y, "REGISTRATION FORM");
        c.y -= 5;
        hline(c.cs, ML, c.y, CW, 0.7f);
        c.y -= 12;
    }

    private void drawStudentBox(PageCursor c) throws IOException {
        float[] rh = {14f, 14f, 10f, 14f};
        float totalHeight = 0f;
        for (float h : rh) totalHeight += h;
        float top = c.y;
        float bottom = top - totalHeight;
        float mid = ML + CW * 0.5f;

        float nameLabelEnd = ML + 4 + tw(c.f.regular, 7.5f, "Name :") + 3;
        float lastColW = 102f;
        float firstColW = 110f;
        float lastNameX = nameLabelEnd;
        float firstNameX = lastNameX + lastColW;
        float middleNameX = firstNameX + firstColW;

        strokeRect(c.cs, ML, bottom, CW, totalHeight);

        float rowTop = top;
        for (int i = 0; i < rh.length; i++) {
            float rowBottom = rowTop - rh[i];
            if (i > 0) {
                hline(c.cs, ML, rowBottom, CW, 0.4f);
            }
            float ty = rowBottom + 3.5f;
            switch (i) {
                case 0 -> {
                    vline(c.cs, mid, rowBottom, rh[i]);
                    labelVal(c, 7.5f, ML + 4, ty, "Student Number :", c.data.referenceNumber());
                    labelVal(c, 7.5f, mid + 4, ty, "Status :", c.data.statusLabel());
                }
                case 1 -> {
                    txt(c.cs, c.f.regular, 7.5f, ML + 4, ty, "Name :");
                    txt(c.cs, c.f.bold, 7.5f, lastNameX, ty, ns(c.data.lastName()));
                    txt(c.cs, c.f.bold, 7.5f, firstNameX, ty, ns(c.data.firstName()));
                    txt(c.cs, c.f.bold, 7.5f, middleNameX, ty, ns(c.data.middleName()));
                    float ageX = ML + CW * 0.68f;
                    String ageVal = c.data.age() != null ? String.valueOf(c.data.age()) : "";
                    labelVal(c, 7.5f, ageX, ty, "Age :", ageVal);
                    float genX = ageX + tw(c.f.regular, 7.5f, "Age :") + 2
                        + tw(c.f.bold, 7.5f, ageVal) + 8;
                    labelVal(c, 7.5f, genX, ty, "Gender :", c.data.gender());
                }
                case 2 -> {
                    txt(c.cs, c.f.regular, 6f, lastNameX, ty, "(Last)");
                    txt(c.cs, c.f.regular, 6f, firstNameX, ty, "(First)");
                    txt(c.cs, c.f.regular, 6f, middleNameX, ty, "(Middle)");
                }
                case 3 -> {
                    vline(c.cs, mid, rowBottom, rh[i]);
                    labelVal(c, 7.5f, ML + 4, ty, "Course & Year :", c.data.courseYearLine());
                    labelVal(c, 7.5f, mid + 4, ty, "Semester/School Year :", c.data.semesterSchoolYearLine());
                }
                default -> { }
            }
            rowTop = rowBottom;
        }
        c.y = bottom - 8;
    }

    private void drawSubjectsTable(PageCursor c, List<SubjectLine> subjects) throws IOException {
        float[] cols = {68f, 162f, 128f, 60f, 54f, 68f};
        String[] hdrs = {"SUBJECT CODE", "DESCRIPTION", "SCHEDULE", "SECTION", "TOTAL UNITS", "REGISTERED BY"};
        float headerH = 15f;
        float rowH = 14f;
        int rows = Math.max(1, subjects.size());
        float tableH = headerH + rows * rowH;
        float topY = c.y;
        float botY = topY - tableH;

        float[] cx = new float[cols.length];
        cx[0] = ML;
        for (int i = 1; i < cols.length; i++) {
            cx[i] = cx[i - 1] + cols[i - 1];
        }

        fillGray(c.cs, ML, topY - headerH, CW, headerH);
        strokeRect(c.cs, ML, botY, CW, tableH);
        hline(c.cs, ML, topY - headerH, CW, 0.5f);
        for (int i = 1; i < cols.length; i++) {
            vline(c.cs, cx[i], botY, tableH);
        }
        for (int ri = 1; ri < rows; ri++) {
            hline(c.cs, ML, topY - headerH - ri * rowH, CW, 0.4f);
        }

        float hty = topY - headerH + 4.5f;
        for (int i = 0; i < hdrs.length; i++) {
            txt(c.cs, c.f.bold, 6.5f, cx[i] + 3, hty, hdrs[i]);
        }

        for (int ri = 0; ri < subjects.size(); ri++) {
            SubjectLine subject = subjects.get(ri);
            float rowBottom = topY - headerH - (ri + 1) * rowH;
            float ty = rowBottom + 4f;

            txt(c.cs, c.f.regular, 7f, cx[0] + 3, ty, ns(subject.courseCode()));
            txt(c.cs, c.f.regular, 6.5f, cx[1] + 3, ty, fitWidth(c.f.regular, 6.5f, subject.description(), cols[1] - 6));
            txt(c.cs, c.f.regular, 6.5f, cx[2] + 3, ty, fitWidth(c.f.regular, 6.5f, subject.schedule(), cols[2] - 6));
            String section = ns(subject.section());
            txt(c.cs, c.f.bold, 7f, cx[3] + (cols[3] - tw(c.f.bold, 7f, section)) / 2f, ty, section);
            String units = fmtUnits(subject.units());
            txt(c.cs, c.f.regular, 7f, cx[4] + (cols[4] - tw(c.f.regular, 7f, units)) / 2f, ty, units);
            txt(c.cs, c.f.regular, 6.5f, cx[5] + 3, ty, fitWidth(c.f.regular, 6.5f, subject.registeredBy(), cols[5] - 6));
        }

        c.y = botY - 8;
    }

    private void drawFeesAndPayment(PageCursor c, RegistrationFormData data) throws IOException {
        float startY = c.y;
        float feeEndY = renderFeeColumn(c.cs, c.f, data, ML, startY, FEE_W);
        float payEndY = renderPaymentColumn(c.cs, c.f, data, PAY_X, startY, PAY_W);
        c.y = Math.min(feeEndY, payEndY) - 8;
    }

    private float renderFeeColumn(
        PDPageContentStream cs,
        Fonts fonts,
        RegistrationFormData data,
        float x,
        float y,
        float w
    ) throws IOException {
        float amtX = x + w;
        float rowHeight = 10f;
        float subRowHeight = 9f;

        txt(cs, fonts.bold, 7.5f, x + (w - tw(fonts.bold, 7.5f, ":: FEE DETAILS ::")) / 2f, y, ":: FEE DETAILS ::");
        y -= 12;

        y = feeRow(cs, fonts.regular, x, amtX, y, rowHeight, "TUITION FEE", data.tuitionAmount());
        y = feeRow(cs, fonts.regular, x, amtX, y, rowHeight, "TUTORIAL FEES", 0.0);

        y = feeRow(cs, fonts.bold, x, amtX, y, rowHeight, "MISCELLANEOUS FEES", data.miscTotal());
        for (Map.Entry<String, Double> entry : data.miscFees().entrySet()) {
            y = feeRow(cs, fonts.regular, x + 12, amtX, y, subRowHeight, entry.getKey(), entry.getValue());
        }

        y = feeRow(cs, fonts.bold, x, amtX, y, rowHeight, "LABORATORY FEES", 0.0);

        y = feeRow(cs, fonts.bold, x, amtX, y, rowHeight, "OTHER FEES", data.otherTotal());
        for (Map.Entry<String, Double> entry : data.otherFees().entrySet()) {
            y = feeRow(cs, fonts.regular, x + 12, amtX, y, subRowHeight, entry.getKey(), entry.getValue());
        }

        y -= 3;
        hline(cs, x, y, w, 0.5f);
        hline(cs, x, y - 2, w, 0.5f);
        y -= 12;

        txt(cs, fonts.bold, 8f, x, y, "TOTAL ASSESSMENT");
        String totalAssessment = "Php " + fmtMoney(data.totalAssessment());
        txt(cs, fonts.bold, 8f, amtX - tw(fonts.bold, 8f, totalAssessment), y, totalAssessment);
        y -= 11;

        txt(cs, fonts.bold, 7.5f, x, y, "Parent Share");
        String parentShare = "Php " + fmtMoney(data.totalAssessment());
        txt(cs, fonts.bold, 7.5f, amtX - tw(fonts.bold, 7.5f, parentShare), y, parentShare);
        y -= 10;

        return y;
    }

    private float feeRow(
        PDPageContentStream cs,
        PDType1Font font,
        float x,
        float amtX,
        float y,
        float rowHeight,
        String label,
        double amount
    ) throws IOException {
        txt(cs, font, 7.5f, x, y, label);
        String value = fmtMoney(amount);
        txt(cs, font, 7.5f, amtX - tw(font, 7.5f, value), y, value);
        return y - rowHeight;
    }

    private float renderPaymentColumn(
        PDPageContentStream cs,
        Fonts fonts,
        RegistrationFormData data,
        float x,
        float y,
        float w
    ) throws IOException {
        txt(cs, fonts.bold, 7.5f, x + (w - tw(fonts.bold, 7.5f, ":: PAYMENT DETAILS ::")) / 2f, y, ":: PAYMENT DETAILS ::");
        y -= 13;

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Downpayment", fmtPayment(data.downpaymentRequired())});
        for (InstallmentLine line : data.installments()) {
            rows.add(new String[]{line.label(), fmtPayment(line.amountDue())});
        }

        float headerH = 13f;
        float rowH = 11f;
        float tableH = headerH + rows.size() * rowH;
        float botY = y - tableH;
        float splitX = x + w * 0.55f;

        fillGray(cs, x, y - headerH, w, headerH);
        strokeRect(cs, x, botY, w, tableH);
        vline(cs, splitX, botY, tableH);
        hline(cs, x, y - headerH, w, 0.5f);

        txt(cs, fonts.bold, 7f, x + 3, y - headerH + 4f, "PAYMENT MODE");
        txt(cs, fonts.bold, 7f, splitX + 3, y - headerH + 4f, "INSTALLMENT");

        float rowTop = y - headerH;
        for (String[] row : rows) {
            float rowBottom = rowTop - rowH;
            hline(cs, x, rowBottom, w, 0.4f);
            txt(cs, fonts.regular, 7f, x + 3, rowBottom + 3f, row[0]);
            txt(cs, fonts.regular, 7f, x + w - tw(fonts.regular, 7f, row[1]) - 3, rowBottom + 3f, row[1]);
            rowTop = rowBottom;
        }

        return rowTop - 4;
    }

    private static void drawRulesTitle(PageCursor c) throws IOException {
        txt(c.cs, c.f.bold, 8f, ML, c.y, "RULES GOVERNING RECORD / ADJUSTMENT OF FEES");
        hline(c.cs, ML, c.y - 2, CW, 0.5f);
        c.y -= 10;
    }

    private static void drawPageFooter(PageCursor c) throws IOException {
        hline(c.cs, ML, FOOTER_Y + 10, CW, 0.5f);
        txt(c.cs, c.f.regular, 7f, ML, FOOTER_Y, "Printed by : " + ns(c.data.printedBy()));
        String printedAt = "Date and Time Printed : "
            + c.data.printedAt().format(DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a"));
        txt(c.cs, c.f.regular, 7f, PAGE_W - ML - tw(c.f.regular, 7f, printedAt), FOOTER_Y, printedAt);
        c.close();
    }

    private void labelVal(PageCursor c, float size, float x, float y, String label, String value) throws IOException {
        txt(c.cs, c.f.regular, size, x, y, label);
        txt(c.cs, c.f.bold, size, x + tw(c.f.regular, size, label) + 2, y, ns(value));
    }

    private static void txtCentered(PDPageContentStream cs, PDType1Font font, float size, float y, String text)
        throws IOException {
        String value = san(text);
        txt(cs, font, size, (PAGE_W - tw(font, size, value)) / 2f, y, value);
    }

    private static void txt(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
        throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(san(text));
        cs.endText();
    }

    private static float wrappedText(
        PDPageContentStream cs,
        PDType1Font font,
        float size,
        float x,
        float y,
        float maxWidth,
        String text
    ) throws IOException {
        String[] words = san(text).split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (tw(font, size, candidate) > maxWidth && !line.isEmpty()) {
                txt(cs, font, size, x, y, line.toString());
                y -= 11;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            txt(cs, font, size, x, y, line.toString());
            y -= 11;
        }
        return y;
    }

    private static float tw(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(san(text)) / 1000f * size;
    }

    private void strokeRect(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void fillGray(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(0.82f);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setNonStrokingColor(0f);
    }

    private static void hline(PDPageContentStream cs, float x, float y, float w, float lw) throws IOException {
        cs.setLineWidth(lw);
        cs.moveTo(x, y);
        cs.lineTo(x + w, y);
        cs.stroke();
    }

    private static void vline(PDPageContentStream cs, float x, float botY, float h) throws IOException {
        cs.setLineWidth(0.4f);
        cs.moveTo(x, botY);
        cs.lineTo(x, botY + h);
        cs.stroke();
    }

    private static String fitWidth(PDType1Font font, float size, String value, float maxWidth) throws IOException {
        if (value == null || value.isBlank() || maxWidth <= 0) {
            return "";
        }
        String text = value.trim();
        if (tw(font, size, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        int end = text.length();
        while (end > 0 && tw(font, size, text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    private static String fmtMoney(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static String fmtPayment(double value) {
        return String.format(Locale.US, "%,.3f", value);
    }

    private static String fmtUnits(double value) {
        return value == Math.rint(value)
            ? String.valueOf((int) value)
            : String.format(Locale.US, "%.1f", value);
    }

    private static String ns(String value) {
        return value != null ? value : "";
    }

    private static String san(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text
            .replace("\u20B1", "Php ")
            .replace('\u2014', '-')
            .replace('\u2013', '-')
            .replace("\u2026", "...")
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u00D7', 'x');
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            sb.append(c < 128 ? c : '?');
        }
        return sb.toString();
    }

    private static String coalesce(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private static double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String text(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RegistrationFormData(
        String referenceNumber,
        String statusLabel,
        String lastName,
        String firstName,
        String middleName,
        String fullName,
        Integer age,
        String gender,
        String programCode,
        String programName,
        String courseYearLine,
        String semesterSchoolYearLine,
        String schoolYear,
        String semesterLabel,
        List<SubjectLine> subjects,
        double totalUnits,
        double tuitionAmount,
        Map<String, Double> miscFees,
        Map<String, Double> otherFees,
        double totalAssessment,
        double downpaymentRequired,
        double totalPaid,
        double balance,
        List<InstallmentLine> installments,
        String printedBy,
        LocalDateTime printedAt
    ) {
        double miscTotal() {
            return miscFees.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        double otherTotal() {
            return otherFees.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private record SubjectLine(
        String courseCode,
        String description,
        String schedule,
        String section,
        double units,
        String registeredBy
    ) {
    }

    private record InstallmentLine(String label, double amountDue) {
    }

    private static final class Fonts {
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private static final class PageCursor {
        private PDPageContentStream cs;
        private final Fonts f;
        private final RegistrationFormData data;
        private final int pageNum;
        private final int pageTotal;
        private float y;

        private PageCursor(PDDocument doc, Fonts f, RegistrationFormData data, int pageNum, int pageTotal)
            throws IOException {
            this.f = f;
            this.data = data;
            this.pageNum = pageNum;
            this.pageTotal = pageTotal;
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            this.cs = new PDPageContentStream(doc, page);
            this.y = TOP_Y;
        }

        private void close() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }
    }
}
