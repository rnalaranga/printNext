param(
    [Parameter(Mandatory=$true)]
    [int]$JobId,
    [string]$PrinterName = "RR Secure Pull Print"
)

Write-Host "[CANCEL] Purging Windows Job #$JobId from '$PrinterName'..."
Remove-PrintJob -PrinterName $PrinterName -ID $JobId -ErrorAction SilentlyContinue
Write-Host "[CANCEL] Job purged."
exit 0
