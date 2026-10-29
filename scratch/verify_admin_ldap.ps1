Start-Sleep -Seconds 4
try {
    $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Server status: $($h.status)"
} catch {
    Write-Host "Waiting..."
    Start-Sleep -Seconds 4
    $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Server status: $($h.status)"
}

$adminHtml = (Invoke-WebRequest -Uri "http://localhost:8080/admin.html" -UseBasicParsing).Content
Write-Host "Has modalLdapLoading in admin.html: $($adminHtml.Contains('modalLdapLoading'))"
Write-Host "Has syncLdapUsersWithLoading in admin.html: $($adminHtml.Contains('syncLdapUsersWithLoading'))"

$adminJs = (Invoke-WebRequest -Uri "http://localhost:8080/js/admin-app.js" -UseBasicParsing).Content
Write-Host "Has syncLdapUsersWithLoading in admin-app.js: $($adminJs.Contains('syncLdapUsersWithLoading'))"

$adminCss = (Invoke-WebRequest -Uri "http://localhost:8080/css/admin.css" -UseBasicParsing).Content
Write-Host "Has ldap-sync-spinner in admin.css: $($adminCss.Contains('ldap-sync-spinner'))"
