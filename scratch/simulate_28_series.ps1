Write-Output "=========================================================="
Write-Output "  SIMULATING TOSHIBA 28-SERIES WORKFLOW (PCT0002 / 192.168.1.11)"
Write-Output "=========================================================="

$base = "http://localhost:8080"
$printerIp = "192.168.1.11"
$printerCode = "PCT0002"

# Step 1: Diagnose Initial Hardware Lock State
Write-Output "`n[Step 1] Querying 28-Series Hardware CWS Initial Lock State..."
$diag = Invoke-RestMethod -Uri "$base/api/admin/printers/325/diagnose"
if ($diag.AuthSettings -match '<copy>(.*?)</copy>') {
    Write-Output "Initial Copy Restricted: $($Matches[1])"
}
if ($diag.UI -match '<DefaultScreen>(.*?)</DefaultScreen>') {
    Write-Output "Initial DefaultScreen: $($Matches[1])"
}

# Step 2: User Login via PIN
Write-Output "`n[Step 2] User 'chiran' Logging in via PIN (1234)..."
$loginBody = @{ pin = '1234'; printerCode = $printerCode } | ConvertTo-Json
$webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginRes = Invoke-RestMethod -Uri "$base/api/auth/login-pin" -Method Post -Body $loginBody -ContentType 'application/json' -WebSession $webSession
Write-Output "Login Result: Authenticated=$($loginRes.authenticated), User=$($loginRes.user.username)"

# Step 3: Trigger Device Function Unlock (User taps 'Copy')
Write-Output "`n[Step 3] User Taps 'Copy' (Switch to Native Copy Functions)..."
$unlockBody = @{
    printerCode = $printerCode
    printerIp = $printerIp
    mode = 'COPY'
    username = 'chiran'
    switchScreen = 'false'
} | ConvertTo-Json

$t0 = [System.Diagnostics.Stopwatch]::StartNew()
$unlockRes = Invoke-RestMethod -Uri "$base/api/station/device-functions" -Method Post -Body $unlockBody -ContentType 'application/json' -WebSession $webSession
$t0.Stop()
Write-Output "Unlock Request Time: $($t0.ElapsedMilliseconds)ms"
Write-Output "Unlock Result: Success=$($unlockRes.success), ScreenSwitched=$($unlockRes.screenSwitched)"
Write-Output "Message: $($unlockRes.message)"

# Step 4: Verify Physical Hardware State on 28-Series
Write-Output "`n[Step 4] Checking Physical Hardware CWS State on 192.168.1.11..."
$diagAfterUnlock = Invoke-RestMethod -Uri "$base/api/admin/printers/325/diagnose"
if ($diagAfterUnlock.AuthSettings -match '<AuthenticatedTarget>(.*?)</AuthenticatedTarget>') {
    Write-Output "Hardware AuthenticatedTarget: $($Matches[1])"
}
if ($diagAfterUnlock.UI -match '<DefaultScreen>(.*?)</DefaultScreen>') {
    Write-Output "Hardware DefaultScreen: $($Matches[1])"
}

# Step 5: Simulate Session Auto-Lock (Sleep / Inactivity / Finish)
Write-Output "`n[Step 5] User Finished Copying -> Triggering Auto-Lock..."
$lockBody = @{
    printerCode = $printerCode
    printerIp = $printerIp
    reason = 'FINISH_DEVICE_SESSION'
} | ConvertTo-Json

$t1 = [System.Diagnostics.Stopwatch]::StartNew()
$lockRes = Invoke-RestMethod -Uri "$base/api/station/auto-lock" -Method Post -Body $lockBody -ContentType 'application/json' -WebSession $webSession
$t1.Stop()
Write-Output "Lock Request Time: $($t1.ElapsedMilliseconds)ms"
Write-Output "Lock Result: Success=$($lockRes.success), Locked=$($lockRes.locked)"

# Step 6: Verify Physical Hardware Re-locked on 28-Series
Write-Output "`n[Step 6] Checking Physical Hardware CWS State after Lock on 192.168.1.11..."
$diagAfterLock = Invoke-RestMethod -Uri "$base/api/admin/printers/325/diagnose"
if ($diagAfterLock.AuthSettings -match '<AuthenticatedTarget>(.*?)</AuthenticatedTarget>') {
    Write-Output "Hardware AuthenticatedTarget: $($Matches[1])"
}
if ($diagAfterLock.UI -match '<DefaultScreen>(.*?)</DefaultScreen>') {
    Write-Output "Hardware DefaultScreen: $($Matches[1])"
}

Write-Output "`n=========================================================="
Write-Output "  28-SERIES SIMULATION COMPLETED SUCCESSFULLY!"
Write-Output "=========================================================="
