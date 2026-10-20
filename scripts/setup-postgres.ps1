<#
.SYNOPSIS
    PostgreSQL Installation Helper for Windows
.DESCRIPTION
    Provides automated options to install or run PostgreSQL for RR Secure Print:
    Option 1: Windows native package via winget
    Option 2: Lightweight Docker / Podman container
#>

[CmdletBinding()]
param(
    [ValidateSet("winget", "docker", "status")]
    [string]$Method = "status"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RR Secure Print — PostgreSQL Setup Helper" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if ($Method -eq "status") {
    $pg = Get-Service *postgres* -ErrorAction SilentlyContinue
    if ($pg) {
        Write-Host "[OK] PostgreSQL Service found: $($pg.Name) ($($pg.Status))" -ForegroundColor Green
    } else {
        Write-Host "[INFO] PostgreSQL is not yet running on this machine." -ForegroundColor Yellow
        Write-Host "`nTo install native PostgreSQL via winget, run:" -ForegroundColor White
        Write-Host "  .\scripts\setup-postgres.ps1 -Method winget" -ForegroundColor Cyan
        Write-Host "`nOr to run via Docker container, run:" -ForegroundColor White
        Write-Host "  .\scripts\setup-postgres.ps1 -Method docker" -ForegroundColor Cyan
        Write-Host "`nOr use the built-in embedded H2 database (Zero setup required!):" -ForegroundColor White
        Write-Host "  .\scripts\run-dev.ps1" -ForegroundColor Green
    }
} elseif ($Method -eq "winget") {
    Write-Host "[INFO] Installing PostgreSQL 16 via winget..." -ForegroundColor Yellow
    winget install PostgreSQL.PostgreSQL --silent --accept-package-agreements --accept-source-agreements
} elseif ($Method -eq "docker") {
    Write-Host "[INFO] Launching PostgreSQL container..." -ForegroundColor Yellow
    docker run -d --name rr-secure-print-db `
        -p 5432:5432 `
        -e POSTGRES_DB=rr_secure_print `
        -e POSTGRES_USER=postgres `
        -e POSTGRES_PASSWORD=postgres `
        postgres:16-alpine
    Write-Host "[OK] Container started on port 5432." -ForegroundColor Green
}
