. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
Write-Host "Authenticated successfully on $ip!"

function Set-KioskTargetLock {
    param($ip, $authContext, [bool]$lock)
    $copyVal = if ($lock) { "true" } else { "false" }
    $scanVal = if ($lock) { "true" } else { "false" }

    $setXml = @"
<DeviceInformationModel>
  <SetValue overrideDelta="false">
    <Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>1</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>$copyVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>$scanVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>false</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>false</value></Payload>
  </SetValue>
  <Command>
    <AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
  </Command>
</DeviceInformationModel>
"@

    $headers = @{
        "csrfpId" = $authContext.Cookie
        "Cache-Control" = "no-cache"
        "Pragma" = "no-cache"
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $authContext.Session -TimeoutSec 10
    $sw.Stop()
    $label = if ($lock) {'LOCK'} else {'UNLOCK'}
    Write-Host "[$label] Time: $($sw.ElapsedMilliseconds)ms, Result: $($res.Content -match 'STATUS_OK')"
}

# 1. Test Lock
Write-Host "`n--- Testing LOCK with openAccess=1 ---"
Set-KioskTargetLock -ip $ip -authContext $auth -lock $true

# 2. Test Unlock
Write-Host "`n--- Testing UNLOCK with openAccess=1 ---"
Set-KioskTargetLock -ip $ip -authContext $auth -lock $false

# 3. Test Lock again
Write-Host "`n--- Testing LOCK with openAccess=1 ---"
Set-KioskTargetLock -ip $ip -authContext $auth -lock $true
