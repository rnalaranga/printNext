. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly

$ip = '192.168.1.11'
$auth = Invoke-TopAccessLogin -ip $ip -user 'admin' -pass '123456'
Write-Host "Authenticated successfully on $ip!"

function Set-CleanTargetLock {
    param($ip, $authContext, [bool]$lock)
    $copyVal = if ($lock) { "true" } else { "false" }
    $scanVal = if ($lock) { "true" } else { "false" }

    $setXml = @"
<DeviceInformationModel>
  <SetValue overrideDelta="false">
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>$copyVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>$scanVal</value></Payload>
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
    $res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $authContext.Session -TimeoutSec 15
    $sw.Stop()
    $label = if ($lock) {'LOCK'} else {'UNLOCK'}
    Write-Host "[$label clean] Time: $($sw.ElapsedMilliseconds)ms, Result: $($res.Content -match 'STATUS_OK')"
}

Write-Host "`n--- Testing CLEAN UNLOCK (only copy/scan target) ---"
Set-CleanTargetLock -ip $ip -authContext $auth -lock $false

Start-Sleep -Seconds 2

Write-Host "`n--- Testing CLEAN LOCK (only copy/scan target) ---"
Set-CleanTargetLock -ip $ip -authContext $auth -lock $true
