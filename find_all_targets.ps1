$text = [System.IO.File]::ReadAllText("$PSScriptRoot\diag_388.json")
$idx = 0
while (($idx = $text.IndexOf("AuthenticatedTarget", $idx)) -ge 0) {
    Write-Host "Match at ${idx}:"
    Write-Host ($text.Substring($idx, 400))
    $idx += 20
}
