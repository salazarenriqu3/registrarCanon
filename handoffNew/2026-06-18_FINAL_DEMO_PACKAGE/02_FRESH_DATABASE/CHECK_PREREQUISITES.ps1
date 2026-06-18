$ErrorActionPreference = "Stop"
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\..\.."))
$failures = [System.Collections.Generic.List[string]]::new()

function Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $status = if ($Passed) { "PASS" } else { "FAIL" }
    $color = if ($Passed) { "Green" } else { "Red" }
    Write-Host "$status $Name - $Detail" -ForegroundColor $color
    if (-not $Passed) { $failures.Add($Name) }
}

Write-Host "EAC demo prerequisite check" -ForegroundColor Cyan
Write-Host "Workspace: $workspace"

Check "Registrar project" (Test-Path (Join-Path $workspace "registrar\pom.xml")) "registrar/pom.xml"
Check "Enrollment project" (Test-Path (Join-Path $workspace "enrollment3\pom.xml")) "enrollment3/pom.xml"
Check "Java" ($null -ne (Get-Command java -ErrorAction SilentlyContinue)) "Java 17+ required"
$hasMaven = $null -ne (Get-Command mvn -ErrorAction SilentlyContinue)
$hasWrapper = Test-Path (Join-Path $workspace "enrollment3\mvnw.cmd")
Check "Maven" ($hasMaven -or $hasWrapper) "mvn or enrollment wrapper"

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
    $mysql = Get-ChildItem "C:\Program Files\MariaDB*", "C:\Program Files\MySQL\MySQL Server*" -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\mysql.exe" } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}
Check "MySQL client" ($null -ne $mysql) "mysql.exe"

if ($mysql) {
    $dbHost = if ($env:EAC_DB_HOST) { $env:EAC_DB_HOST } else { "127.0.0.1" }
    $dbPort = if ($env:EAC_DB_PORT) { $env:EAC_DB_PORT } else { "3306" }
    $dbUser = if ($env:EAC_DB_USER) { $env:EAC_DB_USER } else { "root" }
    if ($env:EAC_DB_PASSWORD) { $env:MYSQL_PWD = $env:EAC_DB_PASSWORD }
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $mysql -h $dbHost -P $dbPort -u $dbUser -e "SELECT 1" 2>$null | Out-Null
    $databaseExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
    Check "Database server" ($databaseExitCode -eq 0) "$dbUser@$dbHost`:$dbPort"
}

$registrarInUse = $null -ne (Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue)
$enrollmentInUse = $null -ne (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue)
Check "Registrar port" $true $(if ($registrarInUse) { "8083 already in use; stop the existing app before starting another" } else { "8083 available" })
Check "Enrollment port" $true $(if ($enrollmentInUse) { "8082 already in use; stop the existing app before starting another" } else { "8082 available" })

if ($failures.Count -gt 0) {
    Write-Host "Failed checks: $($failures -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host "All required checks passed." -ForegroundColor Green
exit 0
