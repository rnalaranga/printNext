$base = "http://localhost:8080"
$sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Step 1: Login
$loginBody = '{"pin":"1234","printerCode":"PCT0001"}'
$login = Invoke-RestMethod -Uri "$base/api/auth/login-pin" -Method Post -Body $loginBody -ContentType "application/json" -WebSession $sess
Write-Output "Login: Authenticated=$($login.authenticated), User=$($login.user.username)"

# Step 2: Unlock
$unlockBody = '{"printerCode":"PCT0001","printerIp":"192.168.1.10","mode":"COPY","username":"chiran","switchScreen":"false"}'
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$unlock = Invoke-RestMethod -Uri "$base/api/station/device-functions" -Method Post -Body $unlockBody -ContentType "application/json" -WebSession $sess
$sw.Stop()
Write-Output "Unlock result: $($unlock.success), took $($sw.ElapsedMilliseconds)ms"

# Step 3: Duplicate Unlock (Should be instant cache hit!)
$sw.Restart()
$unlock2 = Invoke-RestMethod -Uri "$base/api/station/device-functions" -Method Post -Body $unlockBody -ContentType "application/json" -WebSession $sess
$sw.Stop()
Write-Output "Duplicate unlock result: $($unlock2.success), took $($sw.ElapsedMilliseconds)ms (Cache Hit!)"

# Step 4: Lock Device
$lockBody = '{"printerCode":"PCT0001","printerIp":"192.168.1.10","reason":"USER_COMPLETED"}'
$sw.Restart()
$lock = Invoke-RestMethod -Uri "$base/api/station/auto-lock" -Method Post -Body $lockBody -ContentType "application/json" -WebSession $sess
$sw.Stop()
Write-Output "Lock result: $($lock.success), took $($sw.ElapsedMilliseconds)ms"

# Step 5: Duplicate Lock (Should be instant cache hit!)
$sw.Restart()
$lock2 = Invoke-RestMethod -Uri "$base/api/station/auto-lock" -Method Post -Body $lockBody -ContentType "application/json" -WebSession $sess
$sw.Stop()
Write-Output "Duplicate lock result: $($lock2.success), took $($sw.ElapsedMilliseconds)ms (Cache Hit!)"
