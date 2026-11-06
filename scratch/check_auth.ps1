foreach ($id in @(324, 325, 356, 388)) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/printers/$id/diagnose"
        $d = $res.AuthSettings
        $ui = $res.UI
        Write-Output "=== Printer ID $id ==="
        if ($d -match '<AuthenticatedTarget>(.*?)</AuthenticatedTarget>') {
            Write-Output "AuthenticatedTarget: $($Matches[1])"
        }
        if ($d -match '<openAccess>(.*?)</openAccess>') {
            Write-Output "openAccess: $($Matches[1])"
        }
        if ($ui -match '<DefaultScreen>(.*?)</DefaultScreen>') {
            Write-Output "DefaultScreen: $($Matches[1])"
        }
        if ($ui -match '<autoClear>(.*?)</autoClear>') {
            Write-Output "autoClear: $($Matches[1])"
        }
    } catch {
        Write-Output "Error querying ${id}"
    }
}
