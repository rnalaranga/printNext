$c = (Invoke-WebRequest -Uri "http://localhost:8080/index.html" -UseBasicParsing).Content

Write-Host "Print Release in page: $($c.Contains('Print Release'))"
Write-Host "Secure Queue in page (should be False): $($c.Contains('Secure Queue'))"
Write-Host "Start Copying in page (should be False): $($c.Contains('Start Copying'))"
Write-Host "Start Scanning in page (should be False): $($c.Contains('Start Scanning'))"
Write-Host "Photocopier Ready in page: $($c.Contains('Photocopier Ready'))"
Write-Host "Scanner Ready in page: $($c.Contains('Scanner Ready'))"
Write-Host "RFID / NFC Contactless in page (should be False): $($c.Contains('RFID / NFC Contactless'))"
Write-Host "Ready for Card Tap in page (should be False): $($c.Contains('Ready for Card Tap'))"
