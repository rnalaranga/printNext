$res = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/printers/324/diagnose'
Write-Output '=== 324 AuthSettings ==='
Write-Output $res.AuthSettings
Write-Output '=== 324 UI ==='
Write-Output $res.UI
