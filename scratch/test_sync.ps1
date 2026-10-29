try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/directory/sync-ad" -Method Post
    Write-Host "Success: $($res.success)"
    Write-Host "Message: $($res.message)"
    Write-Host "Imported: $($res.importedCount)"
    Write-Host "Updated: $($res.updatedCount)"
} catch {
    Write-Host "Exception: $($_.Exception.Message)"
}
