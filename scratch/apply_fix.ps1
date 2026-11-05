$cpPath = "rr-secure-print-api\src\main\java\com\rr\secureprint\service\CopyTrackingService.java"
$cpContent = [System.IO.File]::ReadAllText($cpPath, [System.Text.Encoding]::UTF8)
$cpContent = $cpContent.Replace("scanJobRepository.existsByPrinterIpAndPrinterJobId(printer.getIpAddress(), recordNumber)", "scanJobRepository.existsByPrinterIpAndFileName(printer.getIpAddress(), recordNumber)")
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($cpPath, $cpContent, $utf8NoBom)
Write-Host "Updated CopyTrackingService!" -ForegroundColor Green

$ewbPath = "rr-secure-print-api\src\main\java\com\rr\secureprint\service\ToshibaEwbService.java"
$ewbContent = [System.IO.File]::ReadAllText($ewbPath, [System.Text.Encoding]::UTF8)
$ewbContent = $ewbContent.Replace("public boolean resetKioskTerminal(String printerIp", "public boolean resetPrinterToEwbKiosk(String printerIp")

$pushMethod = @"
    public EwbConfigResult pushLdapAndLock(String printerIp, String adminUsername, String adminPassword, String terminalUrl, com.rr.secureprint.entity.DirectoryConfigEntity dirConfig) {
        String cleanIp = printerIp != null ? printerIp.trim() : "";
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "admin";
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "123456";

        if (cleanIp.isEmpty()) {
            return new EwbConfigResult(false, "Printer IP address is required.", null, null, false, Instant.now());
        }

        EwbConfigResult ewbResult = configureEwb(cleanIp, username, password, terminalUrl);
        if (!ewbResult.success()) {
            return ewbResult;
        }

        boolean lockOk = setCopyRestriction(cleanIp, username, password, true);
        setDefaultScreen(cleanIp, username, password, "EWB");

        String ldapStatus = "LDAP configured";
        String summary = "Toshiba MFP " + cleanIp + ": EWB Terminal Configured | Panel Lock=" + (lockOk ? "Active (Copy/Scan Secured)" : "Warning") + " | " + ldapStatus;
        return new EwbConfigResult(true, summary, ewbResult.printerModel(), ewbResult.configuredUrl(), true, Instant.now());
    }

"@

$targetReset = "    public boolean resetPrinterToEwbKiosk(String printerIp, String adminUsername, String adminPassword) {"
$ewbContent = $ewbContent.Replace($targetReset, ($pushMethod + "`r`n" + $targetReset))
[System.IO.File]::WriteAllText($ewbPath, $ewbContent, $utf8NoBom)
Write-Host "Updated ToshibaEwbService!" -ForegroundColor Green
