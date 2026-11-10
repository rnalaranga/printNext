[xml]$x11 = Get-Content "scratch/auth_11_settings.xml"
[xml]$x15 = Get-Content "scratch/auth_15_settings.xml"

function Get-Leaves($node, $path="") {
    $leaves = @{}
    foreach ($child in $node.ChildNodes) {
        if ($child.NodeType -eq "Element") {
            $currentPath = if ($path) { "$path/$($child.Name)" } else { $child.Name }
            if ($child.HasChildNodes -and $child.FirstChild.NodeType -eq "Element") {
                $sub = Get-Leaves $child $currentPath
                foreach ($k in $sub.Keys) { $leaves[$k] = $sub[$k] }
            } else {
                $leaves[$currentPath] = $child.InnerText
            }
        }
    }
    return $leaves
}

$l11 = Get-Leaves $x11.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
$l15 = Get-Leaves $x15.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings

Write-Host "=== DIFFERENCES BETWEEN 15 (18-series) and 11 (28-series) ==="
foreach ($k in ($l11.Keys | Sort-Object)) {
    if ($l15.ContainsKey($k)) {
        if ($l11[$k] -ne $l15[$k]) {
            Write-Host "$k -> [15: $($l15[$k])] vs [11: $($l11[$k])]"
        }
    } else {
        Write-Host "ONLY IN 11: $k = $($l11[$k])"
    }
}
foreach ($k in ($l15.Keys | Sort-Object)) {
    if (-not $l11.ContainsKey($k)) {
        Write-Host "ONLY IN 15: $k = $($l15[$k])"
    }
}
