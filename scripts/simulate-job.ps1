# CLI Spool Job Generator for RR Secure Print
param(
    [string]$Username = "jdoe",
    [string]$DocumentName = "Foundation-Structural-Drawings-Sheet4.pdf",
    [int]$Pages = 8,
    [switch]$Color,
    [switch]$SingleSided,
    [int]$Copies = 1,
    [string]$ServerUrl = "http://localhost:8080"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RR Secure Print - Submitting Simulated Spool Job" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$body = @{
    username = $Username
    documentName = $DocumentName
    pageCount = $Pages
    color = $Color.IsPresent
    duplex = (-not $SingleSided.IsPresent)
    copies = $Copies
    clientMachine = $env:COMPUTERNAME
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$ServerUrl/api/simulation/submit-job" -Method Post -Body $body -ContentType "application/json"
    Write-Host "[SUCCESS] Job Queued Successfully!" -ForegroundColor Green
    Write-Host "  Job ID:        $($response.id)" -ForegroundColor White
    Write-Host "  Job Number:    #$($response.jobNumber)" -ForegroundColor White
    Write-Host "  Document:      $($response.documentName)" -ForegroundColor White
    Write-Host "  User:          $($response.username) ($($response.userSid))" -ForegroundColor White
    Write-Host "  Status:        $($response.status)" -ForegroundColor Yellow
    Write-Host "  Pages:         $($response.pageCount) ($($response.formattedFileSize))" -ForegroundColor White
    Write-Host "`nYou can now log in at the Toshiba touchscreen ($ServerUrl) to release this job." -ForegroundColor Cyan
} catch {
    Write-Error "Failed to submit job: $_"
}
