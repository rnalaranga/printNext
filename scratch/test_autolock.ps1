try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/station/auto-lock' -Method Post -ContentType 'application/json' -Body '{"printerCode":"PCT0002","reason":"TEST"}' -UseBasicParsing
    Write-Output $r.Content
} catch {
    Write-Host "Caught error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "RESPONSE BODY:"
        Write-Host $reader.ReadToEnd()
    }
}
