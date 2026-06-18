$ErrorActionPreference = "Stop"
$package = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$required = @(
    "00_START_HERE.md",
    "01_DOCUMENTATION\FINAL_SYSTEM_DOCUMENTATION_20260618.md",
    "01_DOCUMENTATION\FINAL_DEMO_AND_TEST_MANUAL_20260618.md",
    "01_DOCUMENTATION\FINAL_HANDOVER_20260618.md",
    "02_FRESH_DATABASE\RUN_FRESH_DATABASE.cmd",
    "02_FRESH_DATABASE\sql\01_SCHEMA\01_base_schema_and_seed.sql",
    "02_FRESH_DATABASE\sql\02_CONTRACTS\02_enlistment_status_contract.sql",
    "02_FRESH_DATABASE\sql\02_CONTRACTS\06_exact_fee_schema.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\03_full_curriculum.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\04_academic_term_calendar.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\07_retire_empty_programs.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\08_block_sections.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\09_block_offerings.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\10_irregular_open_sections.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\11_faculty_and_grading.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\12_class_schedules.sql",
    "02_FRESH_DATABASE\sql\03_ACADEMIC_MASTER\16_assign_demo_faculty.sql",
    "02_FRESH_DATABASE\sql\04_TERM_AND_FEES\05_demo_fee_templates.sql",
    "02_FRESH_DATABASE\sql\04_TERM_AND_FEES\13_activate_demo_term.sql",
    "02_FRESH_DATABASE\sql\04_TERM_AND_FEES\14_materialize_demo_term_fees.sql",
    "02_FRESH_DATABASE\sql\04_TERM_AND_FEES\15_materialize_calendar_term_fees.sql",
    "02_FRESH_DATABASE\sql\05_VERIFICATION\17_verify_readiness.sql",
    "03_TEST_DATA\01_read_only_demo_smoke.sql",
    "03_TEST_DATA\02_scholarship_demo_seed.sql",
    "03_TEST_DATA\03_scholarship_demo_cleanup.sql",
    "03_TEST_DATA\04_registrar_student_dataset_verify.sql",
    "03_TEST_DATA\REGISTRAR_STUDENT_TEST_MATRIX.md",
    "04_RUNNERS\07_LOAD_SCHOLARSHIP_TEST_DATA.cmd",
    "04_RUNNERS\08_CLEAN_SCHOLARSHIP_TEST_DATA.cmd",
    "05_MANIFEST\SOURCE_FILE_MAP.md",
    "05_MANIFEST\SHA256SUMS.txt"
)

$missing = $required | Where-Object { -not (Test-Path (Join-Path $package $_)) }
if ($missing) {
    Write-Host "PACKAGE INVALID - missing files:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" }
    exit 1
}

$checksumFile = Join-Path $package "05_MANIFEST\SHA256SUMS.txt"
$checksumErrors = [System.Collections.Generic.List[string]]::new()
foreach ($line in Get-Content $checksumFile) {
    if ($line -notmatch '^([0-9a-f]{64})  (.+)$') {
        $checksumErrors.Add("Invalid checksum line: $line")
        continue
    }
    $expectedHash = $Matches[1]
    $relativePath = $Matches[2].Replace('/', '\')
    $target = Join-Path $package $relativePath
    if (-not (Test-Path $target)) {
        $checksumErrors.Add("Missing checksummed file: $relativePath")
        continue
    }
    $actualHash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        $checksumErrors.Add("Checksum mismatch: $relativePath")
    }
}

if ($checksumErrors.Count -gt 0) {
    Write-Host "PACKAGE INVALID - checksum errors:" -ForegroundColor Red
    $checksumErrors | ForEach-Object { Write-Host "  $_" }
    exit 1
}

$files = Get-ChildItem $package -Recurse -File
Write-Host "PACKAGE VALID" -ForegroundColor Green
Write-Host "Root: $package"
Write-Host "Files: $($files.Count)"
Write-Host "Size: $([math]::Round(($files | Measure-Object Length -Sum).Sum / 1MB, 2)) MB"
exit 0
