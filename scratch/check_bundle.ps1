$url = "http://192.168.1.11/topaccess.e49ee3eefb40b1c28871.bundle.js"
Write-Host "Downloading bundle..."
$content = (Invoke-WebRequest -Uri $url -UseBasicParsing).Content
Write-Host "Bundle size: $($content.Length)"

$patterns = @(
    '["''](/[^"'']*login[^"'']*)["'']',
    '["''](/[^"'']*auth[^"'']*)["'']',
    '["''](/contentwebserver[^"'']*)["'']',
    'login_user_name',
    'login_password',
    'csrfpId',
    'Session'
)

foreach ($p in $patterns) {
    $m = [regex]::Matches($content, $p)
    Write-Host "Pattern: $p -> Matches: $($m.Count)"
    $m | Select-Object -First 5 | ForEach-Object { Write-Host "   Found: $($_.Value)" }
}
