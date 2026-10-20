# Prerequisite Checker for RR Secure Print
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   RR SECURE PRINT - PREREQUISITE AND ENVIRONMENT CHECKER   " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Check Java
Write-Host ""
Write-Host "[1/5] Checking Java Version..." -NoNewline
try {
    $javaVerOutput = & java -version 2>&1 | Out-String
    if ($javaVerOutput -match 'version "([0-9]+)') {
        $majorVer = [int]$matches[1]
        if ($majorVer -ge 21) {
            Write-Host " [PASS] Found Java $majorVer" -ForegroundColor Green
        } else {
            Write-Host " [WARN] Java version is $majorVer. Java 21+ is recommended." -ForegroundColor Yellow
        }
    } else {
        Write-Host " [PASS] Java is installed." -ForegroundColor Green
    }
} catch {
    Write-Host " [FAIL] Java is NOT installed or not in PATH." -ForegroundColor Red
}

# 2. Check Maven
Write-Host "[2/5] Checking Apache Maven..." -NoNewline
try {
    $mvnVer = & mvn -v 2>&1 | Select-Object -First 1
    Write-Host " [PASS] $mvnVer" -ForegroundColor Green
} catch {
    Write-Host " [FAIL] Maven is NOT installed or not in PATH." -ForegroundColor Red
}

# 3. Check PostgreSQL
Write-Host "[3/5] Checking PostgreSQL Database..." -NoNewline
$pgRunning = $false
try {
    $pgService = Get-Service *postgres* -ErrorAction SilentlyContinue
    if ($pgService -and $pgService.Status -eq 'Running') {
        Write-Host " [PASS] PostgreSQL Windows Service is Running." -ForegroundColor Green
        $pgRunning = $true
    }
} catch {}

if (-not $pgRunning) {
    $tcpPg = Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
    if ($tcpPg) {
        Write-Host " [PASS] PostgreSQL is listening on port 5432." -ForegroundColor Green
        $pgRunning = $true
    } else {
        Write-Host " [INFO] PostgreSQL not detected locally." -ForegroundColor Yellow
        Write-Host "        (Embedded H2 PostgreSQL-mode fallback is ready for instant zero-dependency execution)" -ForegroundColor Gray
    }
}

# 4. Check LAN IP for Toshiba e-STUDIO MFP
Write-Host "[4/5] Checking Local LAN IP Address..." -NoNewline
$lanIps = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "*Loopback*" -and $_.IPAddress -notlike "169.254*" }
$targetIp = $lanIps | Where-Object { $_.IPAddress -eq "192.168.1.6" } | Select-Object -First 1
if ($targetIp) {
    Write-Host " [PASS] Found designated test IP: 192.168.1.6 ($($targetIp.InterfaceAlias))" -ForegroundColor Green
} elseif ($lanIps) {
    $firstIp = ($lanIps | Select-Object -First 1).IPAddress
    Write-Host " [PASS] Available LAN IP: $firstIp" -ForegroundColor Green
} else {
    Write-Host " [WARN] No active LAN IPv4 address detected." -ForegroundColor Yellow
}

# 5. Check Ports
Write-Host "[5/5] Checking Web Ports (8080 and 8000)..."
$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
$port8000 = Get-NetTCPConnection -LocalPort 8000 -ErrorAction SilentlyContinue

if ($port8080) {
    Write-Host "      - Port 8080: In Use (Process ID: $($port8080.OwningProcess))" -ForegroundColor Yellow
} else {
    Write-Host "      - Port 8080: Available for RR Secure Print Server [OK]" -ForegroundColor Green
}

if ($port8000) {
    Write-Host "      - Port 8000: In Use (Existing Python test server)" -ForegroundColor Yellow
} else {
    Write-Host "      - Port 8000: Available" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  SUMMARY AND NEXT STEPS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "1. To launch the server in Dev/Simulation mode:" -ForegroundColor White
Write-Host "   .\scripts\run-dev.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "2. Open the Toshiba EWB interface in your browser or MFP:" -ForegroundColor White
Write-Host "   http://localhost:8080/  or  http://192.168.1.6:8080/" -ForegroundColor Cyan
Write-Host ""
Write-Host "3. Open the Administrator Portal:" -ForegroundColor White
Write-Host "   http://localhost:8080/admin.html" -ForegroundColor Cyan
