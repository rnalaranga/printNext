param([string]$FilePath)
$text = [System.IO.File]::ReadAllText($FilePath)
$normalized = $text -replace "\r\n", "`n"
[System.IO.File]::WriteAllText($FilePath, $normalized, [System.Text.Encoding]::UTF8)
Write-Output "Normalized $FilePath to LF"
