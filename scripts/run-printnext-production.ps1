# printNext - Production 24/7 Resilient Runner
# Restarts automatically if the server exits or is killed

param(
    [int]$Port = 8080
)

$scriptDir = $PSScriptRoot
$rootDir = (Get-Item $scriptDir).Parent.FullName
$jarPath = Join-Path $rootDir "rr-secure-print-api\target\rr-secure-print-api-1.0.0-SNAPSHOT.jar"

if (-not (Test-Path $jarPath)) {
    Write-Error "Could not find $jarPath. Please build the project first."
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "    printNext - 24/7 High-Availability Service Runner      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Port:         $Port" -ForegroundColor Cyan
Write-Host " JAR:          $jarPath" -ForegroundColor Cyan
Write-Host " Auto-Restart: ENABLED" -ForegroundColor Yellow
Write-Host " Startup Sweep: ALL TOSHIBA MFPs AUTO-LOCKED" -ForegroundColor Yellow
Write-Host "----------------------------------------------------------" -ForegroundColor Gray

while ($true) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Starting printNext server process..." -ForegroundColor White
    try {
        & java -Xms256m -Xmx1024m -jar $jarPath "--server.port=$Port"
    } catch {
        Write-Warning "printNext server terminated with error: $_"
    }
    Write-Warning "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] printNext server stopped. Restarting in 5 seconds (Press Ctrl+C to stop)..."
    Start-Sleep -Seconds 5
}
