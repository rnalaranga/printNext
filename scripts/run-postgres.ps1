# Runs RR Secure Print with PostgreSQL Database
param(
    [int]$Port = 8080,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "rr_secure_print",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "postgres"
)

$scriptDir = $PSScriptRoot
$rootDir = (Get-Item $scriptDir).Parent.FullName

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   RR SECURE PRINT - LAUNCHING POSTGRESQL PRODUCTION PROFILE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Database: jdbc:postgresql://$DbHost:$DbPort/$DbName" -ForegroundColor Gray
Write-Host " User:     $DbUser" -ForegroundColor Gray
Write-Host " Port:     $Port" -ForegroundColor Gray
Write-Host "------------------------------------------------------------" -ForegroundColor Cyan

$env:PORT = "$Port"
$env:ACTIVE_PROFILE = "postgres"
$env:DB_HOST = "$DbHost"
$env:DB_PORT = "$DbPort"
$env:DB_NAME = "$DbName"
$env:DB_USER = "$DbUser"
$env:DB_PASSWORD = "$DbPassword"

Set-Location $rootDir
mvn spring-boot:run -pl rr-secure-print-api "-Dspring-boot.run.profiles=postgres"
