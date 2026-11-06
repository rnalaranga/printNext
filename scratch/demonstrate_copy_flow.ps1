# printNext - Live Demonstration of Direct Copy Screen Jump & Auto-Lock
$ErrorActionPreference = "Stop"

$printerIp = "192.168.1.11"
$printerCode = "PCT0002"
$apiUrl = "http://localhost:8080"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   printNext LIVE DEMONSTRATION: DIRECT COPY JUMP & AUTO-LOCK FLOW   " -ForegroundColor Cyan
Write-Host "   Target MFP: $printerCode ($printerIp) - Toshiba e-STUDIO 2528A" -ForegroundColor Yellow
Write-Host "   Server:     $apiUrl" -ForegroundColor Gray
Write-Host "======================================================================`n" -ForegroundColor Cyan

. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

function Get-LiveStatus {
    $freshAuth = Invoke-TopAccessLogin -ip $printerIp -user "admin" -pass "123456"
    $headers = @{ "csrfpId" = $freshAuth.Cookie; "Cache-Control" = "no-cache" }

    $q1 = '<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>'
    $r1 = Invoke-WebRequest -Uri "http://$printerIp/contentwebserver" -Method Post -Body $q1 -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $freshAuth.Session -TimeoutSec 6
    [xml]$d1 = $r1.Content
    $copyLock = $d1.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget.copy

    $q2 = '<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>'
    $r2 = Invoke-WebRequest -Uri "http://$printerIp/contentwebserver" -Method Post -Body $q2 -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $freshAuth.Session -TimeoutSec 6
    [xml]$d2 = $r2.Content
    $defScreen = $d2.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen

    return [PSCustomObject]@{
        Timestamp     = (Get-Date -Format "HH:mm:ss")
        CopyPadlocked = ($copyLock -eq "true")
        DefaultScreen = $defScreen
    }
}

# --- STEP 0: BASELINE ---
Write-Host "[STEP 0] Checking Baseline State on Toshiba MFP..." -ForegroundColor White
$st0 = Get-LiveStatus
Write-Host "  Time:           $($st0.Timestamp)" -ForegroundColor Gray
Write-Host "  Copy Padlocked: $($st0.CopyPadlocked) (Expected: True - Walk-up blocked)" -ForegroundColor $(if ($st0.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "  Default Screen: $($st0.DefaultScreen) (Expected: EWB - Login Kiosk)" -ForegroundColor $(if ($st0.DefaultScreen -eq "EWB") { "Green" } else { "Red" })
Write-Host "----------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 1: USER CLICKS 'PHOTOCOPY' ---
Write-Host "`n[STEP 1] User clicks [PHOTOCOPY] on nexPrint Touchscreen Terminal..." -ForegroundColor White
$body = @{
    printerIp    = $printerIp
    printerCode  = $printerCode
    mode         = "COPY"
    switchScreen = $true
    username     = "demo.engineer"
} | ConvertTo-Json

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$res = Invoke-RestMethod -Uri "$apiUrl/api/station/device-functions" -Method Post -Body $body -ContentType "application/json"
$sw.Stop()

Write-Host "  API Execution:     $($sw.ElapsedMilliseconds)ms" -ForegroundColor Green
Write-Host "  API Result:        $($res.message)" -ForegroundColor Green

# Query hardware immediately
$st1 = Get-LiveStatus
Write-Host "  Live Hardware Status ($($st1.Timestamp)):" -ForegroundColor White
Write-Host "    -> Copy Padlocked: $($st1.CopyPadlocked) (UNLOCKED: Hardware buttons active!)" -ForegroundColor $(if (-not $st1.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "    -> Default Screen: $($st1.DefaultScreen) (DIRECT COPY JUMP: Set to 'Copy'!)" -ForegroundColor $(if ($st1.DefaultScreen -eq "Copy") { "Green" } else { "Red" })
Write-Host "----------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 2: VERIFY PERSISTENCE (PAST 2-SECOND OLD BUG WINDOW) ---
Write-Host "`n[STEP 2] Waiting 5 seconds to test that DefaultScreen is HELD at 'Copy'..." -ForegroundColor White
Start-Sleep -Seconds 5

$st2 = Get-LiveStatus
Write-Host "  Live Hardware Status at T+5s ($($st2.Timestamp)):" -ForegroundColor White
Write-Host "    -> Copy Padlocked: $($st2.CopyPadlocked) (STILL UNLOCKED: Ready for photocopying)" -ForegroundColor $(if (-not $st2.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "    -> Default Screen: $($st2.DefaultScreen) (STILL 'Copy': Pressing [HOME] jumps to Copy directly!)" -ForegroundColor $(if ($st2.DefaultScreen -eq "Copy") { "Green" } else { "Red" })

if ($st2.DefaultScreen -eq "Copy" -and -not $st2.CopyPadlocked) {
    Write-Host "  >>> VERIFIED: DefaultScreen is held at 'Copy'. The 2-second premature revert bug is gone!" -ForegroundColor Green
} else {
    Write-Host "  >>> FAILED: Premature revert detected!" -ForegroundColor Red
}
Write-Host "----------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 3: USER COMPLETES COPIES & TAPS FINISH / SLEEP ---
Write-Host "`n[STEP 3] User completes copies -> Taps 'Finish & Lock' or presses [SLEEP]..." -ForegroundColor White
$lockBody = @{
    printerIp   = $printerIp
    printerCode = $printerCode
    reason      = "FINISH_DEVICE_SESSION_BUTTON"
} | ConvertTo-Json

$lockRes = Invoke-RestMethod -Uri "$apiUrl/api/station/auto-lock" -Method Post -Body $lockBody -ContentType "application/json"
Write-Host "  Auto-Lock API: Result=$($lockRes.success), Reason=$($lockRes.reason)" -ForegroundColor Green

Write-Host "  Waiting 3s for TopAccess CWS security lock to commit..." -ForegroundColor Gray
Start-Sleep -Milliseconds 3000

$st3 = Get-LiveStatus
Write-Host "  Final Hardware Status ($($st3.Timestamp)):" -ForegroundColor White
Write-Host "    -> Copy Padlocked: $($st3.CopyPadlocked) (RE-LOCKED: Secured with padlock!)" -ForegroundColor $(if ($st3.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "    -> Default Screen: $($st3.DefaultScreen) (RESTORED: Reset to 'EWB' kiosk for next user!)" -ForegroundColor $(if ($st3.DefaultScreen -eq "EWB") { "Green" } else { "Red" })

Write-Host "`n======================================================================" -ForegroundColor Cyan
if ($st3.CopyPadlocked -and $st3.DefaultScreen -eq "EWB" -and $st2.DefaultScreen -eq "Copy") {
    Write-Host "   DEMONSTRATION RESULT: 100% SUCCESSFUL AND VERIFIED LIVE            " -ForegroundColor Green
} else {
    Write-Host "   DEMONSTRATION RESULT: FAILED                                       " -ForegroundColor Red
}
Write-Host "======================================================================`n" -ForegroundColor Cyan
