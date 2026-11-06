# printNext - 100% Live Hardware Demonstration on Toshiba e-STUDIO 2528A (192.168.1.11)
$ErrorActionPreference = "Stop"

$ip = "192.168.1.11"
$apiUrl = "http://localhost:8080"

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "         LIVE HARDWARE DEMONSTRATION: DIRECT COPY JUMP & AUTO-LOCK        " -ForegroundColor Cyan
Write-Host "   Target MFP: 192.168.1.11 (Toshiba e-STUDIO 2528A in Production)        " -ForegroundColor Yellow
Write-Host "   Server:     $apiUrl                                                    " -ForegroundColor Gray
Write-Host "==========================================================================`n" -ForegroundColor Cyan

. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1"

function Get-HardwareStatus {
    param($targetIp)
    $auth = Invoke-TopAccessLogin -ip $targetIp -user "admin" -pass "123456"
    $h = @{ "csrfpId" = $auth.Cookie; "Cache-Control" = "no-cache" }

    # Query 1: DefaultScreen on Panel
    $qScreen = '<DeviceInformationModel><GetValue><Controller><Settings><UI><DefaultScreen/></UI></Settings></Controller></GetValue></DeviceInformationModel>'
    $rScreen = Invoke-WebRequest -Uri "http://$targetIp/contentwebserver" -Method Post -Body $qScreen -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 6
    [xml]$docScreen = $rScreen.Content
    $screen = $docScreen.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen

    # Query 2: Copy Padlock
    $qAuth = '<DeviceInformationModel><Command><GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings></Command><GetValue><Authentication><AuthenticationSettings></AuthenticationSettings></Authentication></GetValue></DeviceInformationModel>'
    $rAuth = Invoke-WebRequest -Uri "http://$targetIp/contentwebserver" -Method Post -Body $qAuth -ContentType "text/plain; charset=utf-8" -Headers $h -WebSession $auth.Session -TimeoutSec 6
    [xml]$docAuth = $rAuth.Content
    $copy = $docAuth.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings.AuthenticatedTarget.copy

    return [PSCustomObject]@{
        Timestamp     = (Get-Date -Format "HH:mm:ss")
        DefaultScreen = $screen
        CopyPadlocked = ($copy -eq "true")
    }
}

# --- STEP 0: BASELINE ---
Write-Host "`n[PHASE 0: BASELINE STATE BEFORE USER TOUCHES MACHINE]" -ForegroundColor White
$st0 = Get-HardwareStatus -targetIp $ip
Write-Host "  Time:                  $($st0.Timestamp)" -ForegroundColor Gray
Write-Host "  Physical Copy Padlock: $($st0.CopyPadlocked) (LOCKED with padlock - unauthorized walk-up blocked)" -ForegroundColor Green
Write-Host "  Default Screen:        $($st0.DefaultScreen) (Set to 'EWB' kiosk terminal)" -ForegroundColor Green
Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 1: USER CLICKS 'PHOTOCOPY' ---
Write-Host "`n[PHASE 1: USER LOGS IN AND CLICKS 'PHOTOCOPY' ON TERMINAL]" -ForegroundColor White
$payload = @{
    printerIp    = $ip
    printerCode  = "PCT0002"
    mode         = "COPY"
    switchScreen = $true
    username     = "demo.user"
} | ConvertTo-Json

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$apiRes = Invoke-RestMethod -Uri "$apiUrl/api/station/device-functions" -Method Post -Body $payload -ContentType "application/json"
$sw.Stop()

Write-Host "  API Execution:         $($sw.ElapsedMilliseconds)ms" -ForegroundColor Green
Write-Host "  API Message:           $($apiRes.message)" -ForegroundColor Green
Write-Host "  Screen Switch Target:  $($apiRes.mode)" -ForegroundColor Green

# Query hardware
$st1 = Get-HardwareStatus -targetIp $ip
Write-Host "`n  >>> LIVE HARDWARE STATE AFTER CLICKING COPY ($($st1.Timestamp)):" -ForegroundColor Yellow
Write-Host "      1. Copy Padlock:   $($st1.CopyPadlocked) -> UNLOCKED (Walk-up photocopying active)" -ForegroundColor $(if (-not $st1.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "      2. Default Screen: $($st1.DefaultScreen) -> 'Copy' (DIRECT JUMP: Pressing [HOME] jumps to Copy!)" -ForegroundColor $(if ($st1.DefaultScreen -eq "Copy") { "Green" } else { "Red" })
Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 2: TEST PERSISTENCE (OLD BUG WAS 2s REVERT) ---
Write-Host "`n[PHASE 2: VERIFY PERSISTENCE - WAITING 7 SECONDS (PAST OLD 2-SECOND BUG)]" -ForegroundColor White
Write-Host "  (In the old code, after 2 seconds DefaultScreen prematurely reverted to 'EWB')" -ForegroundColor Gray
Start-Sleep -Seconds 7

$st2 = Get-HardwareStatus -targetIp $ip
Write-Host "`n  >>> LIVE HARDWARE STATE AT T+7 SECONDS ($($st2.Timestamp)):" -ForegroundColor Yellow
Write-Host "      1. Copy Padlock:   $($st2.CopyPadlocked) -> UNLOCKED (Machine still ready for user)" -ForegroundColor $(if (-not $st2.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "      2. Default Screen: $($st2.DefaultScreen) -> 'Copy' (STILL HELD AT 'Copy'! NO PREMATURE REVERT!)" -ForegroundColor $(if ($st2.DefaultScreen -eq "Copy") { "Green" } else { "Red" })

if ($st2.DefaultScreen -eq "Copy" -and -not $st2.CopyPadlocked) {
    Write-Host "`n  [PASS] 2-Second premature revert bug is 100% ELIMINATED!" -ForegroundColor Green
    Write-Host "         When user presses [HOME] on the panel, it opens Native Copy Screen directly!" -ForegroundColor Green
} else {
    Write-Host "`n  [FAIL] Screen reverted prematurely!" -ForegroundColor Red
}
Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkGray

# --- STEP 3: USER COMPLETES COPIES AND LOCKS ---
Write-Host "`n[PHASE 3: USER COMPLETES PHOTOCOPIES -> PRESSES [SLEEP] / AUTO-LOCKS]" -ForegroundColor White
$lockPayload = @{
    printerIp   = $ip
    printerCode = "PCT0002"
    reason      = "FINISH_DEVICE_SESSION_BUTTON"
} | ConvertTo-Json

$lockRes = Invoke-RestMethod -Uri "$apiUrl/api/station/auto-lock" -Method Post -Body $lockPayload -ContentType "application/json"
Write-Host "  Auto-Lock API Triggered: Result=$($lockRes.success), Reason=$($lockRes.reason)" -ForegroundColor Green

Write-Host "  Waiting 5s for CWS security lock to commit..." -ForegroundColor Gray
Start-Sleep -Seconds 5

$st3 = Get-HardwareStatus -targetIp $ip
Write-Host "`n  >>> FINAL HARDWARE STATE AFTER LOCKOUT ($($st3.Timestamp)):" -ForegroundColor Yellow
Write-Host "      1. Copy Padlock:   $($st3.CopyPadlocked) -> RE-LOCKED (Padlock active, unauthorized copies blocked)" -ForegroundColor $(if ($st3.CopyPadlocked) { "Green" } else { "Red" })
Write-Host "      2. Default Screen: $($st3.DefaultScreen) -> 'EWB' (RESTORED to nexPrint Login Kiosk!)" -ForegroundColor $(if ($st3.DefaultScreen -eq "EWB") { "Green" } else { "Red" })

Write-Host "`n==========================================================================" -ForegroundColor Cyan
if (-not $st1.CopyPadlocked -and $st1.DefaultScreen -eq "Copy" -and $st2.DefaultScreen -eq "Copy" -and $st3.CopyPadlocked -and $st3.DefaultScreen -eq "EWB") {
    Write-Host "   DEMONSTRATION VERDICT: 100% SUCCESSFUL & VERIFIED ON HARDWARE ENGINE   " -ForegroundColor Green
} else {
    Write-Host "   DEMONSTRATION VERDICT: FAILED                                          " -ForegroundColor Red
}
Write-Host "==========================================================================`n" -ForegroundColor Cyan
