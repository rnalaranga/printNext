<#
.SYNOPSIS
    Configures Toshiba e-BRIDGE Next MFPs for nexPrint Kiosk Mode and Function-wise Panel Lockdown.
.DESCRIPTION
    Automates TopAccess configuration across individual devices or entire fleets (100+ MFPs).
    Enforces PaperCut-style function-wise authentication so unauthenticated walk-up users
    cannot access native Copy or Scan by pressing the physical [Home] button.
.EXAMPLE
    .\configure-printer-kiosk.ps1 -PrinterIp "192.168.1.10" -Action Lock
    .\configure-printer-kiosk.ps1 -PrinterIp "192.168.1.10" -Action Status
    .\configure-printer-kiosk.ps1 -PrinterList "192.168.1.10,192.168.1.11" -Action Lock
#>
param(
    [string]$PrinterIp = "192.168.1.10",
    [string]$PrinterList = "",
    [string]$AdminUser = "Admin",
    [string]$AdminPass = "123456",
    [ValidateSet("Status", "Lock", "Unlock")]
    [string]$Action = "Status"
)

function Invoke-TopAccessLogin {
    param($ip, $user, $pass)

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $initRes = Invoke-WebRequest -Uri "http://$ip/" -WebSession $session -TimeoutSec 5
    $cookie = ($session.Cookies.GetCookies("http://$ip/") | Where-Object { $_.Name -eq "Session" }).Value

    if (-not $cookie) {
        throw "Could not acquire initial Session cookie from $ip"
    }

    $clientIp = $cookie.Substring(0, $cookie.LastIndexOf('.'))
    $loginXml = @"
<DeviceInformationModel>
  <SetValue>
    <Authentication>
      <UserCredential>
        <userName>$user</userName>
        <passwd>$pass</passwd>
        <ipaddress>$clientIp</ipaddress>
        <DepartmentManagement isEnable='false'>
          <requireDepartment>false</requireDepartment>
        </DepartmentManagement>
        <domainName></domainName>
        <applicationType>TOP_ACCESS</applicationType>
      </UserCredential>
    </Authentication>
  </SetValue>
  <Command>
    <Login>
      <commandNode>Authentication/UserCredential</commandNode>
      <Params><appName>TOPACCESS</appName></Params>
    </Login>
  </Command>
  <SaveSessionInformation>
    <SessionInfo>
      <Information><type>LoginPassword</type><data>$pass</data></Information>
      <Information><type>LoginUser</type><data>$user</data></Information>
    </SessionInfo>
  </SaveSessionInformation>
  <GetValue>
    <Authentication><UserCredential></UserCredential></Authentication>
  </GetValue>
</DeviceInformationModel>
"@

    $headers = @{
        "csrfpId" = $cookie
        "Cache-Control" = "no-cache"
        "Pragma" = "no-cache"
    }

    $loginRes = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $loginXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $session -TimeoutSec 10

    if ($loginRes.Content -match "STATUS_OK") {
        $activeCookie = ($session.Cookies.GetCookies("http://$ip/") | Where-Object { $_.Name -eq "Session" }).Value
        return @{
            Session = $session
            Cookie = $activeCookie
        }
    } else {
        throw "Login failed on $ip"
    }
}

function Get-ToshibaAuthStatus {
    param($ip, $authContext)

    $headers = @{
        "csrfpId" = $authContext.Cookie
        "Cache-Control" = "no-cache"
        "Pragma" = "no-cache"
    }

    $queryXml = @"
<DeviceInformationModel>
  <Command>
    <GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings>
  </Command>
  <GetValue>
    <Authentication><AuthenticationSettings></AuthenticationSettings></Authentication>
    <Controller><Settings><UI><DefaultScreen></DefaultScreen></UI></Settings></Controller>
  </GetValue>
</DeviceInformationModel>
"@

    $res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $queryXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $authContext.Session -TimeoutSec 10
    [xml]$doc = $res.Content
    $auth = $doc.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
    $defScreen = $doc.DeviceInformationModel.GetValue.Controller.Settings.UI.DefaultScreen

    [PSCustomObject]@{
        IP = $ip
        UserAuthEnabled = ($auth.openAccess -eq "1")
        FunctionWiseAuth = ($auth.functionwiseAuthentication -eq "true")
        CopyRestricted = ($auth.AuthenticatedTarget.copy -eq "true")
        ScanRestricted = ($auth.AuthenticatedTarget.scan -eq "true")
        PrintRestricted = ($auth.AuthenticatedTarget.print -eq "true")
        EWBRestricted = ($auth.AuthenticatedTarget.EWB -eq "true")
        DefaultScreen = $defScreen
    }
}

function Set-ToshibaKioskLock {
    param($ip, $authContext, [bool]$lock)

    $openAccessVal = if ($lock) { "1" } else { "0" }
    $copyVal = if ($lock) { "true" } else { "false" }
    $scanVal = if ($lock) { "true" } else { "false" }
    $printVal = "false"
    $ewbVal = "false" # Must remain false so nexPrint terminal screen can open freely

    $headers = @{
        "csrfpId" = $authContext.Cookie
        "Cache-Control" = "no-cache"
        "Pragma" = "no-cache"
    }

    $setXml = @"
<DeviceInformationModel>
  <SetValue overrideDelta="false">
    <Payload><path>Authentication/AuthenticationSettings/openAccess</path><value>$openAccessVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/functionwiseAuthentication</path><value>true</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/copy</path><value>$copyVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/scan</path><value>$scanVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/print</path><value>$printVal</value></Payload>
    <Payload><path>Authentication/AuthenticationSettings/AuthenticatedTarget/EWB</path><value>$ewbVal</value></Payload>
  </SetValue>
  <Command>
    <AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>
  </Command>
</DeviceInformationModel>
"@

    $res = Invoke-WebRequest -Uri "http://$ip/contentwebserver" -Method Post -Body $setXml -ContentType "text/plain; charset=utf-8" -Headers $headers -WebSession $authContext.Session -TimeoutSec 10
    if ($res.Content -match "STATUS_OK") {
        return $true
    } else {
        throw "Failed to update authentication settings on ${ip} : $($res.Content)"
    }
}

# --- Execution Entry ---
$targets = @()
if ($PrinterList) {
    $targets = $PrinterList.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
} else {
    $targets = @($PrinterIp)
}

foreach ($target in $targets) {
    Write-Host "`n=======================================================" -ForegroundColor Cyan
    Write-Host "Target MFP: $target (Action: $Action)" -ForegroundColor Cyan
    Write-Host "=======================================================" -ForegroundColor Cyan

    try {
        $auth = Invoke-TopAccessLogin -ip $target -user $AdminUser -pass $AdminPass
        Write-Host "Authenticated as '$AdminUser' successfully." -ForegroundColor Green

        if ($Action -eq "Lock") {
            Write-Host "Applying PaperCut-style function-wise panel lock..." -ForegroundColor Yellow
            $null = Set-ToshibaKioskLock -ip $target -authContext $auth -lock $true
            Write-Host "Lock command applied successfully." -ForegroundColor Green
        } elseif ($Action -eq "Unlock") {
            Write-Host "Removing panel lock (restoring open access)..." -ForegroundColor Yellow
            $null = Set-ToshibaKioskLock -ip $target -authContext $auth -lock $false
            Write-Host "Unlock command applied successfully." -ForegroundColor Green
        }

        $status = Get-ToshibaAuthStatus -ip $target -authContext $auth
        Write-Host "`nCurrent Configuration on ${target}:" -ForegroundColor White
        $status | Format-List
    } catch {
        Write-Host "Error configuring ${target} : $($_.Exception.Message)" -ForegroundColor Red
    }
}
