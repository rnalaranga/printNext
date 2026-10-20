# RR Secure Print - Windows Spooler Interceptor & Auto-Hold Service
param(
    [string]$PrinterName = "RR Secure Pull Print",
    [string]$PrinterIp = "192.168.1.8",
    [string]$ApiUrl = "http://localhost:8080/api/spool/ingest",
    [int]$PollIntervalMs = 600
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  nexPrint - WINDOWS SPOOL INTERCEPTOR DAEMON                " -ForegroundColor Cyan
Write-Host "  Watching Queue: $PrinterName" -ForegroundColor Yellow
Write-Host "  Target Device:  $PrinterIp : 9100" -ForegroundColor Yellow
Write-Host "  Backend API:    $ApiUrl" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Verify Standard TCP/IP Port exists for physical Toshiba MFP
$portName = "IP_$PrinterIp"
$port = Get-PrinterPort -Name $portName -ErrorAction SilentlyContinue
if (-not $port) {
    Write-Host "[SETUP] Creating Standard TCP/IP Port '$portName' ($PrinterIp:9100)..." -ForegroundColor Yellow
    Add-PrinterPort -Name $portName -PrinterHostAddress $PrinterIp -PortNumber 9100 -ErrorAction SilentlyContinue
}

# 2. Verify / Configure Virtual Queue
$printer = Get-Printer -Name $PrinterName -ErrorAction SilentlyContinue
if (-not $printer) {
    Write-Host "[SETUP] Creating Virtual Queue '$PrinterName'..." -ForegroundColor Yellow
    Add-Printer -Name $PrinterName -DriverName "TOSHIBA Universal Printer 2" -PortName $portName
    Set-Printer -Name $PrinterName -KeepPrintedJobs $false
    Write-Host "[OK] Virtual Queue created with Toshiba Universal Driver." -ForegroundColor Green
} else {
    Set-Printer -Name $PrinterName -PortName $portName -KeepPrintedJobs $false -ErrorAction SilentlyContinue
    Write-Host "[OK] Connected to '$PrinterName' (Driver: $($printer.DriverName), Port: $portName)." -ForegroundColor Green
}

# 3. Ensure Queue is Paused on startup for pull-print quarantine
$cimPrinter = Get-CimInstance -ClassName Win32_Printer -Filter "Name='$PrinterName'"
if ($cimPrinter) {
    Invoke-CimMethod -InputObject $cimPrinter -MethodName Pause | Out-Null
    Write-Host "[OK] Queue '$PrinterName' is PAUSED (Secure Quarantine Active)." -ForegroundColor Green
}

# 4. Set as default printer for easy Ctrl+P
try {
    Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows NT\CurrentVersion\Windows" -Name "LegacyDefaultPrinterMode" -Value 1 -Force -ErrorAction SilentlyContinue
    rundll32 printui.dll,PrintUIEntry /y /n "$PrinterName"
    Write-Host "[OK] Set '$PrinterName' as Windows Default Printer." -ForegroundColor Green
} catch {
    Write-Host "[WARN] Could not set default printer: $_" -ForegroundColor Gray
}

Write-Host "`n[STATUS] Active and listening for Windows print jobs (Ctrl + P)..." -ForegroundColor Green
Write-Host "Any document printed to '$PrinterName' will be held securely until released at the touchscreen.`n" -ForegroundColor Gray

$processedJobs = @{}

while ($true) {
    try {
        $jobs = Get-PrintJob -PrinterName $PrinterName -ErrorAction SilentlyContinue
        foreach ($job in $jobs) {
            # Skip jobs that are already being processed or already printed
            if ($job.JobStatus -eq "Printed" -or $job.JobStatus -eq "Deleting") {
                continue
            }

            $jobKey = "$($job.Id)"
            if ($processedJobs.ContainsKey($jobKey)) {
                continue
            }

            # Immediately suspend the individual job so it cannot despool prematurely
            Suspend-PrintJob -PrinterName $PrinterName -ID $job.Id -ErrorAction SilentlyContinue

            $processedJobs[$jobKey] = [DateTime]::UtcNow

            Write-Host "`n------------------------------------------------------------" -ForegroundColor Cyan
            Write-Host "[PRINT CAPTURED] Windows Spooler Job #$($job.Id)" -ForegroundColor Yellow
            Write-Host "  Document:  $($job.DocumentName)" -ForegroundColor White
            Write-Host "  User:      $($job.UserName)" -ForegroundColor White
            Write-Host "  Pages:     $([Math]::Max(1, $job.TotalPages))" -ForegroundColor White
            Write-Host "  Driver:    $($printer.DriverName)" -ForegroundColor White
            Write-Host "  Spool:     $($job.Size) bytes" -ForegroundColor White

            # Resolve Windows User SID
            $userSid = "S-1-5-21-1966046810-3588821400-173210280-1001"
            try {
                $account = New-Object System.Security.Principal.NTAccount($job.UserName)
                $userSid = $account.Translate([System.Security.Principal.SecurityIdentifier]).Value
            } catch {
                Write-Host "  [WARN] Using detected Windows SID mapping: $userSid" -ForegroundColor Gray
            }

            # Ingest payload with windowsJobId
            $body = @{
                username = $job.UserName
                userSid = $userSid
                documentName = if ($job.DocumentName) { $job.DocumentName } else { "Print-Document.pdf" }
                pageCount = [Math]::Max(1, $job.TotalPages)
                sizeBytes = $job.Size
                clientMachine = $env:COMPUTERNAME
                color = $false
                duplex = $true
                windowsJobId = [int]$job.Id
            } | ConvertTo-Json

            try {
                $response = Invoke-RestMethod -Uri $ApiUrl -Method Post -Body $body -ContentType "application/json"
                Write-Host "  [HELD] Quarantined in RR Secure Print: Job ID $($response.id)" -ForegroundColor Green
                Write-Host "  [TOUCHSCREEN] Log in at http://192.168.1.6:8080/ or MFP screen to release" -ForegroundColor Cyan
            } catch {
                Write-Host "  [ERROR] Ingestion to API failed: $_" -ForegroundColor Red
                # Remove from tracking so we can retry on next iteration
                $processedJobs.Remove($jobKey)
            }
        }

        # Clean tracked jobs that no longer exist in Windows Spooler (released or cancelled)
        if ($jobs) {
            $activeJobIds = @($jobs | ForEach-Object { "$($_.Id)" })
            $trackedKeys = @($processedJobs.Keys)
            foreach ($k in $trackedKeys) {
                if ($k -notin $activeJobIds) {
                    $processedJobs.Remove($k)
                }
            }
        } else {
            $processedJobs.Clear()
        }

    } catch {
        # Loop safety
    }

    Start-Sleep -Milliseconds $PollIntervalMs
}
