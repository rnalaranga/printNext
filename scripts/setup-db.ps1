<#
.SYNOPSIS
    PostgreSQL Database Initializer for RR Secure Print
.DESCRIPTION
    Creates the 'rr_secure_print' database and applies schema migrations.
#>

[CmdletBinding()]
param(
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$Database = "rr_secure_print",
    [string]$User = "postgres"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RR Secure Print — Database Initialization" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$schemaFile = Join-Path $PSScriptRoot "..\rr-secure-print-db\schema.sql"
if (-not (Test-Path $schemaFile)) {
    Write-Error "Schema file not found at: $schemaFile"
    exit 1
}

# Check if psql is available
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($psql) {
    Write-Host "[INFO] Connecting via psql to $HostName:$Port..." -ForegroundColor Yellow
    & psql -h $HostName -p $Port -U $User -c "CREATE DATABASE $Database;" 2>$null
    & psql -h $HostName -p $Port -U $User -d $Database -f $schemaFile
    Write-Host "[SUCCESS] Database schema initialized successfully!" -ForegroundColor Green
} else {
    Write-Host "[WARN] 'psql' CLI not found in PATH." -ForegroundColor Yellow
    Write-Host "You can let Flyway automatically execute the migration on Spring Boot startup by running:" -ForegroundColor White
    Write-Host "  .\scripts\run-postgres.ps1" -ForegroundColor Cyan
}
