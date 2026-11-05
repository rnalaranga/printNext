$j = Get-Content 'diagnose_2528.json' -Raw | ConvertFrom-Json
$adminSys = $j.AdminSystemSettings
[xml]$doc = $adminSys
$doc.DeviceInformationModel.GetValue.Controller.Settings.AdminSystemSettings.ChildNodes | ForEach-Object { $_.Name }
