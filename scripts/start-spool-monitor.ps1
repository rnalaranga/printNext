# RR Secure Print - PaperCut-Style Find-Me Virtual Queue Interceptor Daemon
param(
    [string]$PrinterName = "",
    [string]$ApiUrl = "http://localhost:8080/api/spool/ingest",
    [string]$VirtualQueueApiUrl = "http://localhost:8080/api/admin/virtual-queue",
    [int]$PollIntervalMs = 500
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  nexPrint - SERVER VIRTUAL QUEUE INTERCEPTOR DAEMON         " -ForegroundColor Cyan
Write-Host "  Backend API: $ApiUrl" -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Resolve which printer on the server acts as the Virtual Queue
if (-not $PrinterName) {
    try {
        $vq = Invoke-RestMethod -Uri $VirtualQueueApiUrl -Method Get -ErrorAction Stop
        if ($vq.selectedPrinterName) {
            $PrinterName = $vq.selectedPrinterName
            Write-Host "[API CONFIG] Loaded designated Virtual Queue: '$PrinterName'" -ForegroundColor Green
        }
    } catch {
        Write-Host "[INFO] API not reachable yet. Checking installed Windows printers..." -ForegroundColor Gray
    }
}

if (-not $PrinterName) {
    # Check if QOA_Printer is installed on this Windows server
    if (Get-Printer -Name "QOA_Printer" -ErrorAction SilentlyContinue) {
        $PrinterName = "QOA_Printer"
    } else {
        $PrinterName = "RR Secure Print"
    }
}

Write-Host "  Target Virtual Queue: $PrinterName" -ForegroundColor Yellow

# 2. Check and connect to the Virtual Printer on Windows
$printer = Get-Printer -Name $PrinterName -ErrorAction SilentlyContinue
if (-not $printer) {
    Write-Host "[SETUP] Server printer '$PrinterName' not found! Creating Virtual Queue..." -ForegroundColor Yellow
    $driver = Get-PrinterDriver -Name "TOSHIBA Universal Printer 2" -ErrorAction SilentlyContinue
    $driverName = if ($driver) { "TOSHIBA Universal Printer 2" } else { "Generic / Text Only" }
    Add-Printer -Name $PrinterName -DriverName $driverName -PortName "PORTPROMPT:"
    Set-Printer -Name $PrinterName -KeepPrintedJobs $false
    $printer = Get-Printer -Name $PrinterName
    Write-Host "[OK] Created '$PrinterName' (Driver: $driverName)." -ForegroundColor Green
} else {
    Write-Host "[OK] Connected to '$PrinterName' (Driver: $($printer.DriverName), Port: $($printer.PortName), Shared: $($printer.Shared))." -ForegroundColor Green
}

# 3. Ensure Queue is Paused for secure pull-print quarantine
$cimPrinter = Get-CimInstance -ClassName Win32_Printer -Filter "Name='$PrinterName'"
if ($cimPrinter) {
    Invoke-CimMethod -InputObject $cimPrinter -MethodName Pause | Out-Null
    Write-Host "[OK] Queue '$PrinterName' is PAUSED (Secure Quarantine Active)." -ForegroundColor Green
}

# 4. Display Network Sharing Path
$serverHost = $env:COMPUTERNAME
if ($printer.Shared -and $printer.ShareName) {
    Write-Host "[OK] Network Share: \\$serverHost\$($printer.ShareName)" -ForegroundColor Green
} else {
    Write-Host "[INFO] Local Queue (Enable sharing in Windows or Admin Console to share across LAN)." -ForegroundColor Gray
}

Write-Host "`n[STATUS] Active and listening for print jobs sent to '$PrinterName'..." -ForegroundColor Green
Write-Host "All jobs printed to '$PrinterName' will be quarantined into the Virtual Pool." -ForegroundColor White
Write-Host "Users can walk up to ANY physical Toshiba MFP, enter PIN, and release!`n" -ForegroundColor Yellow

$processedJobs = @{}

while ($true) {
    try {
        $jobs = Get-PrintJob -PrinterName $PrinterName -ErrorAction SilentlyContinue
        foreach ($job in $jobs) {
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
            Write-Host "[VIRTUAL PRINT CAPTURED] Job #$($job.Id) on '$PrinterName'" -ForegroundColor Yellow
            Write-Host "  Document:  $($job.DocumentName)" -ForegroundColor White
            Write-Host "  User:      $($job.UserName)" -ForegroundColor White
            Write-Host "  Pages:     $([Math]::Max(1, $job.TotalPages))" -ForegroundColor White
            Write-Host "  Size:      $($job.Size) bytes" -ForegroundColor White

            # Resolve Windows User SID
            $userSid = "S-1-5-21-1966046810-3588821400-173210280-1001"
            try {
                $account = New-Object System.Security.Principal.NTAccount($job.UserName)
                $userSid = $account.Translate([System.Security.Principal.SecurityIdentifier]).Value
            } catch {
                Write-Host "  [WARN] Using default Windows SID: $userSid" -ForegroundColor Gray
            }

            # Capture authentic .SPL raw spool data from Windows Spool directory
            $spoolBase64 = $null
            $splPatterns = @(
                "C:\Windows\System32\spool\PRINTERS\{0:D5}.SPL" -f [int]$job.Id,
                "C:\Windows\System32\spool\PRINTERS\FP{0:D5}.SPL" -f [int]$job.Id,
                "C:\Windows\System32\spool\PRINTERS\*$($job.Id)*.SPL"
            )
            foreach ($pattern in $splPatterns) {
                $candidates = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue
                if ($candidates) {
                    try {
                        $bytes = [System.IO.File]::ReadAllBytes($candidates[0].FullName)
                        if ($bytes.Length -gt 0) {
                            $spoolBase64 = [Convert]::ToBase64String($bytes)
                            Write-Host "  [AUTHENTIC DRIVER DATA] Captured $([Math]::Round($bytes.Length / 1024, 1)) KB raw driver spool." -ForegroundColor Green
                            break
                        }
                    } catch {
                        # Retry
                    }
                }
            }

            # Ingest payload into backend
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
                spoolBase64 = $spoolBase64
            } | ConvertTo-Json

            try {
                $response = Invoke-RestMethod -Uri $ApiUrl -Method Post -Body $body -ContentType "application/json"
                Write-Host "  [HELD IN VIRTUAL POOL] Job ID $($response.id) is now waiting in user's queue." -ForegroundColor Green
                Write-Host "  [ACTION REQUIRED] Walk up to ANY physical Toshiba MFP and enter PIN to release." -ForegroundColor Yellow

                # Purge from Windows spooler queue so Windows stays clean
                Remove-PrintJob -PrinterName $PrinterName -ID $job.Id -ErrorAction SilentlyContinue
            } catch {
                Write-Host "  [ERROR] Ingestion to API failed: $_" -ForegroundColor Red
                $processedJobs.Remove($jobKey)
            }
        }

        # Clean tracked jobs that no longer exist
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
