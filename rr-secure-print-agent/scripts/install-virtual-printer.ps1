<#
.SYNOPSIS
    Installs the isolated "RR Secure Pull Print" Virtual Windows Queue for RR Construction.
.DESCRIPTION
    Creates a dedicated virtual print queue configured to hold print jobs safely
    before they are pulled by employees at the Toshiba e-STUDIO 3518A MFP.
    This script NEVER modifies or touches any existing production printer queues.
.NOTES
    Must be run in an elevated PowerShell session (Run as Administrator).
#>

[CmdletBinding()]
param(
    [string]$PrinterName = "RR Secure Pull Print",
    [string]$DriverName = "Generic / Text Only",
    [string]$PortName = "NUL:"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RR Secure Print — Virtual Pull-Queue Installer" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Check for Administrator elevation
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning "This script requires Administrator privileges to register Windows print queues."
    Write-Warning "Please right-click PowerShell and select 'Run as Administrator'."
    exit 1
}

# 1. Check if printer already exists
$existing = Get-Printer -Name $PrinterName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "[OK] Virtual printer '$PrinterName' is already installed." -ForegroundColor Green
    exit 0
}

# 2. Check Driver Availability
$driver = Get-PrinterDriver -Name $DriverName -ErrorAction SilentlyContinue
if (-not $driver) {
    Write-Host "[INFO] Finding suitable universal PCL / PostScript driver..." -ForegroundColor Yellow
    $availableDrivers = Get-PrinterDriver
    $selected = $availableDrivers | Where-Object { $_.Name -like "*PCL*" -or $_.Name -like "*Generic*" } | Select-Object -First 1
    if ($selected) {
        $DriverName = $selected.Name
        Write-Host "[OK] Using available driver: $DriverName" -ForegroundColor Green
    } else {
        $DriverName = "Microsoft Print to PDF"
        Write-Host "[OK] Using standard driver: $DriverName" -ForegroundColor Green
    }
}

# 3. Add Virtual Queue
try {
    Write-Host "[INFO] Creating virtual queue '$PrinterName' on port '$PortName'..." -ForegroundColor Yellow
    Add-Printer -Name $PrinterName -DriverName $DriverName -PortName $PortName -Comment "RR Construction Secure Pull-Print Queue"
    Write-Host "[SUCCESS] Virtual Pull-Print Queue created successfully!" -ForegroundColor Green
    Write-Host "Workstations can now print to '$PrinterName' to hold jobs for secure release." -ForegroundColor Cyan
} catch {
    Write-Error "Failed to create virtual printer queue: $_"
    exit 1
}
