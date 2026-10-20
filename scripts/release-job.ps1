param(
    [Parameter(Mandatory=$true)]
    [int]$JobId,
    [string]$PrinterName = "RR Secure Pull Print"
)

Write-Host "[RELEASE] Releasing Windows Job #$JobId on '$PrinterName'..."

# 1. Ensure job is resumed
Resume-PrintJob -PrinterName $PrinterName -ID $JobId -ErrorAction SilentlyContinue

# 2. Resume the printer queue so the Windows Spooler begins transmitting driver data to port 9100
$p = Get-CimInstance -ClassName Win32_Printer -Filter "Name='$PrinterName'"
if (-not $p) {
    Write-Error "Printer '$PrinterName' not found."
    exit 1
}

Invoke-CimMethod -InputObject $p -MethodName Resume | Out-Null
Write-Host "[RELEASE] Printer resumed. Spooler despooling to 192.168.1.8:9100..."

# 3. Wait up to 6 seconds for the spooler to finish transmitting to the physical printer
$completed = $false
for ($i = 0; $i -lt 12; $i++) {
    Start-Sleep -Milliseconds 500
    $job = Get-PrintJob -PrinterName $PrinterName -ID $JobId -ErrorAction SilentlyContinue
    if (-not $job) {
        $completed = $true
        break
    }
    if ($job.JobStatus -eq "Printed") {
        $completed = $true
        break
    }
}

# 4. Re-pause the printer queue immediately to ensure future jobs remain held
Invoke-CimMethod -InputObject $p -MethodName Pause | Out-Null
Write-Host "[RELEASE] Printer re-paused for quarantine."

# 5. Clean up any remaining printed job artifact
Remove-PrintJob -PrinterName $PrinterName -ID $JobId -ErrorAction SilentlyContinue

Write-Host "[RELEASE] Completed successfully. (Despooled: $completed)"
exit 0
