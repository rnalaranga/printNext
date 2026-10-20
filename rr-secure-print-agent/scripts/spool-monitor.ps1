<#
.SYNOPSIS
    RR Secure Print Windows Agent Daemon Stub
.DESCRIPTION
    Monitors the Windows Print Spooler for incoming jobs sent to "RR Secure Pull Print".
    Extracts the user SID and document metadata, submits to the RR Secure Print REST API,
    and clears the spooler queue.
#>

[CmdletBinding()]
param(
    [string]$PrinterName = "RR Secure Pull Print",
    [string]$ApiUrl = "http://localhost:8080/api/simulation/submit-job",
    [int]$PollIntervalMs = 2000
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RR Secure Print — Windows Spooler Monitor Daemon" -ForegroundColor Cyan
Write-Host "  Watching Queue: $PrinterName" -ForegroundColor Cyan
Write-Host "  API Endpoint:   $ApiUrl" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$running = $true
while ($running) {
    try {
        $jobs = Get-PrintJob -PrinterName $PrinterName -ErrorAction SilentlyContinue
        foreach ($job in $jobs) {
            Write-Host "[SPOOL DETECTED] Job ID: $($job.Id), Document: '$($job.DocumentName)', User: '$($job.UserName)'" -ForegroundColor Yellow

            # Resolve Windows User SID
            $userSid = "S-1-5-21-3623811015-3361044348-30300820-1001"
            try {
                $account = New-Object System.Security.Principal.NTAccount($job.UserName)
                $sidObj = $account.Translate([System.Security.Principal.SecurityIdentifier])
                $userSid = $sidObj.Value
            } catch {
                Write-Host "[WARN] Could not resolve live AD SID for $($job.UserName), using domain fallback." -ForegroundColor Gray
            }

            # Prepare API Payload
            $payload = @{
                username = $job.UserName
                documentName = $job.DocumentName
                pageCount = [Math]::Max(1, $job.TotalPages)
                color = $false
                duplex = $true
                copies = 1
                clientMachine = $env:COMPUTERNAME
            } | ConvertTo-Json

            # Submit to RR Secure Print API
            try {
                $response = Invoke-RestMethod -Uri $ApiUrl -Method Post -Body $payload -ContentType "application/json"
                Write-Host "[INGESTED] Job quarantined and held in RR Secure Print: $($response.id)" -ForegroundColor Green

                # Remove job from Windows Spooler queue so it does not remain pending
                Remove-PrintJob -PrinterName $PrinterName -ID $job.Id -ErrorAction SilentlyContinue
                Write-Host "[PURGED] Cleared from Windows Spooler queue." -ForegroundColor Gray
            } catch {
                Write-Warning "Could not submit job to RR Secure Print API: $_"
            }
        }
    } catch {
        # Quiet error catching for spooler poll
    }

    Start-Sleep -Milliseconds $PollIntervalMs
}
