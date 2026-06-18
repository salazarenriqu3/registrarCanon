# EAC Registrar + Enrollment — prerequisite checker (Windows)
# Usage (from project root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File registrar\setup\CHECK_PREREQUISITES.ps1
#
# Exit 0 = all required checks passed. Exit 1 = one or more failures.
# Agents: read AGENT_FRESH_SETUP.md before fixing failures.

param(
    [string]$ProjectRoot = "",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "",
    [int]$RegistrarPort = 8083
)

$ErrorActionPreference = "Continue"
$script:FailCount = 0
$script:Results = @()

function Write-Check {
    param([string]$Id, [bool]$Ok, [string]$Detail)
    $status = if ($Ok) { "PASS" } else { "FAIL" }
    if (-not $Ok) { $script:FailCount++ }
    $script:Results += [pscustomobject]@{ id = $Id; ok = $Ok; detail = $Detail }
    $color = if ($Ok) { "Green" } else { "Red" }
    Write-Host "$status`: $Id" -ForegroundColor $color -NoNewline
    if ($Detail) { Write-Host " - $Detail" }
    else { Write-Host "" }
}

function Find-MySqlExe {
    if (Get-Command mysql -ErrorAction SilentlyContinue) { return "mysql" }
    foreach ($base in @("C:\Program Files\MariaDB*", "C:\Program Files\MySQL\MySQL Server*")) {
        $hit = Get-ChildItem -Path $base -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\mysql.exe") } |
            Select-Object -First 1
        if ($hit) { return (Join-Path $hit.FullName "bin\mysql.exe") }
    }
    return $null
}

function Test-PortFree {
    param([int]$Port)
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return (-not $c)
    } catch {
        $out = netstat -ano | Select-String ":$Port\s" | Select-String "LISTENING"
        return (-not $out)
    }
}

function Get-JavaMajor {
    try {
        $lines = & java -version 2>&1 | ForEach-Object { "$_" }
        foreach ($line in $lines) {
            if ($line -match 'version "(\d+)') { return [int]$Matches[1] }
            if ($line -match 'version "1\.(\d+)') { return [int]$Matches[1] }
        }
    } catch { }
    return 0
}

# Resolve project root
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path $PSScriptRoot -Parent
}

Write-Host ""
Write-Host "=== EAC PREREQUISITE CHECK ===" -ForegroundColor Cyan
Write-Host "Project root: $ProjectRoot"
Write-Host ""

# ── Project layout ───────────────────────────────────────────────────────────
Write-Check "PRJ-ROOT" (Test-Path $ProjectRoot) $ProjectRoot
Write-Check "PRJ-REGISTRAR" (Test-Path (Join-Path $ProjectRoot "pom.xml")) "pom.xml"
Write-Check "PRJ-SETUP-CMD" (Test-Path (Join-Path $ProjectRoot "setup\RUN_FRESH_SETUP.cmd")) "bootstrap script"
Write-Check "PRJ-FRESH-SQL" (Test-Path (Join-Path $ProjectRoot "handoffNew\2026-06-18_FINAL_DEMO_PACKAGE\02_FRESH_DATABASE\sql\01_SCHEMA\01_base_schema_and_seed.sql")) "self-contained schema seed"

# ── JDK ──────────────────────────────────────────────────────────────────────
$javaOk = $false
$javaDetail = "java not found"
if (Get-Command java -ErrorAction SilentlyContinue) {
    $major = Get-JavaMajor
    $javaOk = $major -ge 17
    $javaDetail = if ($javaOk) { "Java $major OK (need 17+)" } else { "Java $major - need 17 or 21" }
}
Write-Check "JDK" $javaOk $javaDetail

# ── Maven ────────────────────────────────────────────────────────────────────
$mvnOk = $false
$mvnDetail = "mvn not found - install Maven or use IDE embedded Maven"
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    try {
        $ver = (& mvn -version 2>&1 | Select-Object -First 1) -join " "
        $mvnOk = $true
        $mvnDetail = $ver
    } catch { }
}
Write-Check "MAVEN" $mvnOk $mvnDetail

# ── MariaDB / MySQL client ───────────────────────────────────────────────────
$mysqlExe = Find-MySqlExe
Write-Check "MYSQL-CLIENT" ([bool]$mysqlExe) $(if ($mysqlExe) { $mysqlExe } else { "Install MariaDB/MySQL; add bin to PATH" })

# ── DB server reachable ───────────────────────────────────────────────────────
$dbOk = $false
$dbDetail = "skipped - no mysql client"
if ($mysqlExe) {
    $args = @("-h", $DbHost, "-P", "$DbPort", "-u", $DbUser, "-e", "SELECT 1 AS ok")
    if ($DbPassword) { $args = @("-h", $DbHost, "-P", "$DbPort", "-u", $DbUser, "-p$DbPassword", "-e", "SELECT 1 AS ok") }
    try {
        $out = & $mysqlExe @args 2>&1
        $dbOk = ($LASTEXITCODE -eq 0)
        $dbDetail = if ($dbOk) { "${DbHost}:${DbPort} reachable as $DbUser" } else { ($out | Out-String).Trim() }
    } catch {
        $dbDetail = $_.Exception.Message
    }
}
Write-Check "MARIADB-SERVICE" $dbOk $dbDetail

# ── Optional: eacdb already exists ───────────────────────────────────────────
if ($mysqlExe -and $dbOk) {
    $args = @("-h", $DbHost, "-P", "$DbPort", "-u", $DbUser, "-N", "-e", "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='eacdb'")
    if ($DbPassword) { $args = @("-h", $DbHost, "-P", "$DbPort", "-u", $DbUser, "-p$DbPassword", "-N", "-e", "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='eacdb'") }
    $exists = 0
    try { $exists = [int](& $mysqlExe @args 2>$null) } catch { }
    $note = if ($exists -gt 0) { "eacdb exists - RUN_FRESH_SETUP will DROP and recreate" } else { "eacdb not found - bootstrap will create it" }
    Write-Check "DB-EACDB" $true $note
}

# ── Ports (warn if apps already running — not a hard fail) ───────────────────
$regFree = Test-PortFree -Port $RegistrarPort
Write-Check "PORT-REGISTRAR" $true $(if ($regFree) { ":$RegistrarPort free" } else { ":$RegistrarPort IN USE (stop Registrar or continue)" })

# ── Optional Python (preflight only) ─────────────────────────────────────────
$pyOk = Get-Command python -ErrorAction SilentlyContinue
Write-Check "PYTHON-OPTIONAL" ([bool]$pyOk) $(if ($pyOk) { "optional preflight available" } else { "not required for bootstrap/UAT" })

Write-Host ""
Write-Host "Summary: $($script:Results.Count - $script:FailCount)/$($script:Results.Count) passed" -ForegroundColor $(if ($script:FailCount -eq 0) { "Green" } else { "Yellow" })
if ($script:FailCount -gt 0) {
    Write-Host ""
    Write-Host "Required failures must be fixed before bootstrap. See setup\AGENT_FRESH_SETUP.md" -ForegroundColor Yellow
    exit 1
}
Write-Host ""
Write-Host "Next: setup\RUN_FRESH_SETUP.cmd" -ForegroundColor Cyan
exit 0
