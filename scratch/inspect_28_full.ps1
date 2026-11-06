$res = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/printers/325/diagnose"
Write-Output "=== AuthSettings ==="
Write-Output $res.AuthSettings
Write-Output "=== UI ==="
Write-Output $res.UI
Write-Output "=== Controller ==="
Write-Output $res.Controller
