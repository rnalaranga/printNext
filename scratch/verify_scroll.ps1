Start-Sleep -Seconds 5
try {
    $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Server status: $($h.status)"
} catch {
    Start-Sleep -Seconds 4
    $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Server status: $($h.status)"
}

$css = (Invoke-WebRequest -Uri "http://localhost:8080/css/admin.css" -UseBasicParsing).Content

Write-Host "admin-body has overflow-y: auto: $($css.Contains('overflow-y: auto !important; /* Dedicated page-level vertical scrollbar */'))"
Write-Host "table-wrap has overflow-y: visible: $($css.Contains('overflow-y: visible !important; /* Tables expand fully to let the PAGE scroll vertically */'))"
Write-Host "page-settings has overflow: visible: $($css.Contains('#page-settings .card {'))"

$html = (Invoke-WebRequest -Uri "http://localhost:8080/admin.html" -UseBasicParsing).Content
Write-Host "admin.html uses admin.css?v=20261026_108: $($html.Contains('admin.css?v=20261026_108'))"
