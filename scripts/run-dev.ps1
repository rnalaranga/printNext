# Runs RR Secure Print in Development / Simulation Mode
param(
    [int]$Port = 8080
)

$scriptDir = $PSScriptRoot
$rootDir = (Get-Item $scriptDir).Parent.FullName

# Auto-detect Java & Maven if missing from current session PATH
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:JAVA_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "mvn.cmd" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:M2_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   RR SECURE PRINT - LAUNCHING DEV / SIMULATION SERVER      " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Root Directory: $rootDir" -ForegroundColor Gray
Write-Host " Server Port:    $Port" -ForegroundColor Gray
Write-Host " Active Profile: dev (Embedded DB + Safe Spool Storage)" -ForegroundColor Gray
Write-Host "------------------------------------------------------------" -ForegroundColor Cyan

$env:PORT = "$Port"
$env:ACTIVE_PROFILE = "dev"

# Ensure Windows Spool Monitor daemon is running in background
$spoolProc = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*start-spool-monitor.ps1*" }
if (-not $spoolProc) {
    Write-Host "[SPOOL MONITOR] Starting background Windows Spool Monitor..." -ForegroundColor Green
    Start-Process powershell -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File "' + $scriptDir + '\start-spool-monitor.ps1"' -WindowStyle Hidden
} else {
    Write-Host "[SPOOL MONITOR] Background Spool Monitor is active." -ForegroundColor Green
}

Set-Location $rootDir
$jarPath = Join-Path $rootDir "rr-secure-print-api\target\rr-secure-print-api-1.0.0-SNAPSHOT.jar"

if (Test-Path $jarPath) {
    Write-Host "[INFO] Starting executable JAR directly..." -ForegroundColor Green
    & java -jar $jarPath "--server.port=$Port"
} else {
    Write-Host "[INFO] Building and starting via Maven..." -ForegroundColor Yellow
    $arg = "--server.port=" + $Port
    mvn spring-boot:run -pl rr-secure-print-api "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=$arg"
}

