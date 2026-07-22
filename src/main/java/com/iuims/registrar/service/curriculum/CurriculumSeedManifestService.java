package com.iuims.registrar.service.curriculum;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CurriculumSeedManifestService {

    private static final String MANIFEST_PATH = "curriculum/curriculum-seed-manifest.csv";

    public List<CurriculumSeedManifestEntry> loadManifest() {
        Resource resource = new ClassPathResource(MANIFEST_PATH);
        if (!resource.exists()) {
            return List.of();
        }

        List<CurriculumSeedManifestEntry> entries = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (lineNumber == 1 && trimmed.toLowerCase(Locale.ROOT).startsWith("program_code,")) {
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length < 4) {
                    throw new IllegalStateException("Invalid curriculum manifest row at line " + lineNumber);
                }

                String programCode = parts[0].trim();
                String seedModeRaw = parts[1].trim();
                String sourceFile = parts[2].trim();
                String notes = parts[3].trim();

                if (programCode.isBlank()) {
                    throw new IllegalStateException("Blank program_code in curriculum manifest line " + lineNumber);
                }
                if (seen.putIfAbsent(programCode.toUpperCase(Locale.ROOT), lineNumber) != null) {
                    throw new IllegalStateException("Duplicate program_code in curriculum manifest: " + programCode);
                }

                entries.add(new CurriculumSeedManifestEntry(
                    programCode,
                    SeedMode.fromText(seedModeRaw),
                    sourceFile,
                    notes));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load curriculum seed manifest: " + e.getMessage(), e);
        }

        return entries;
    }

    public CurriculumSeedManifestEntry findByProgramCode(String programCode) {
        if (programCode == null || programCode.isBlank()) {
            return null;
        }
        String normalized = programCode.trim().toUpperCase(Locale.ROOT);
        for (CurriculumSeedManifestEntry entry : loadManifest()) {
            if (entry.programCode().equalsIgnoreCase(normalized)) {
                return entry;
            }
        }
        return null;
    }

    public record CurriculumSeedManifestEntry(
        String programCode,
        SeedMode seedMode,
        String sourceFile,
        String notes
    ) {
        public boolean requiresSourceFile() {
            return seedMode == SeedMode.DIRECT || seedMode == SeedMode.SHARED;
        }
    }

    public enum SeedMode {
        DIRECT,
        SHARED,
        BLOCKED;

        public static SeedMode fromText(String raw) {
            if (raw == null || raw.isBlank()) {
                return BLOCKED;
            }
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "DIRECT" -> DIRECT;
                case "SHARED" -> SHARED;
                case "BLOCKED" -> BLOCKED;
                default -> throw new IllegalStateException("Unknown curriculum seed mode: " + raw);
            };
        }
    }
}
