try {
    $r = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/station/auto-lock' -Method Post -Body '{"printerCode":"PCT0003","reason":"RESET_CLEAR_KEY"}' -ContentType 'application/json'
    Write-Output $r
} catch {
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Output $reader.ReadToEnd()
}
