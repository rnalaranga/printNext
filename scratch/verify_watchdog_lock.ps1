Start-Sleep -Seconds 12
. "$PSScriptRoot\..\scripts\configure-printer-kiosk.ps1" -InspectOnly
$auth = Invoke-TopAccessLogin -ip '192.168.1.11' -user 'admin' -pass '123456'
$status = Get-ToshibaAuthStatus -ip '192.168.1.11' -authContext $auth
Write-Host "`nStatus on 192.168.1.11 after watchdog timeout:"
$status | Format-List
