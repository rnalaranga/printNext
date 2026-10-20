package com.rr.secureprint.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Service to manage Windows Spooler queue hold and release operations.
 * Holds real print jobs in the Windows print queue and flushes them to the physical
 * Toshiba MFP upon authenticated release at the MFP touchscreen.
 */
@Service
public class WindowsQueueService {

    private static final Logger LOGGER = Logger.getLogger(WindowsQueueService.class.getName());

    private final VirtualQueueConfigService virtualQueueConfigService;

    public WindowsQueueService() {
        this.virtualQueueConfigService = null;
    }

    public WindowsQueueService(VirtualQueueConfigService virtualQueueConfigService) {
        this.virtualQueueConfigService = virtualQueueConfigService;
    }

    private String getQueueName() {
        return (virtualQueueConfigService != null && virtualQueueConfigService.getSelectedPrinterName() != null)
                ? virtualQueueConfigService.getSelectedPrinterName()
                : "QOA_Printer";
    }

    /**
     * Releases a held Windows print job by resuming the printer queue and allowing
     * Windows Spooler to transmit the authentic Toshiba Universal Driver data to port 9100.
     */
    public boolean releaseWindowsJob(Integer windowsJobId) {
        if (windowsJobId == null) {
            LOGGER.warning("[WINDOWS SPOOL RELEASE] No Windows Job ID provided.");
            return false;
        }

        String queue = getQueueName();
        LOGGER.info("[WINDOWS SPOOL RELEASE] Releasing Job #" + windowsJobId + " on queue '" + queue + "'...");

        try {
            File scriptFile = new File("scripts/release-job.ps1");
            ProcessBuilder pb;
            if (scriptFile.exists()) {
                pb = new ProcessBuilder(
                        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", scriptFile.getAbsolutePath(),
                        "-JobId", String.valueOf(windowsJobId),
                        "-PrinterName", queue
                );
            } else {
                // Fallback to inline command if script file is not found
                String cmd = String.format(
                        "Resume-PrintJob -PrinterName '%s' -ID %d -ErrorAction SilentlyContinue; " +
                        "$p = Get-CimInstance -ClassName Win32_Printer -Filter \"Name='%s'\"; " +
                        "Invoke-CimMethod -InputObject $p -MethodName Resume | Out-Null; " +
                        "Start-Sleep -Seconds 3; " +
                        "Invoke-CimMethod -InputObject $p -MethodName Pause | Out-Null; " +
                        "Remove-PrintJob -PrinterName '%s' -ID %d -ErrorAction SilentlyContinue",
                        queue, windowsJobId, queue, queue, windowsJobId
                );
                pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[PS-RELEASE] " + line);
                }
            }

            int exitCode = process.waitFor();
            LOGGER.info("[WINDOWS SPOOL RELEASE] Process finished with exit code: " + exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.severe("[WINDOWS SPOOL RELEASE FAILED] Error releasing job #" + windowsJobId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Cancels and purges a held job from the Windows print spooler.
     */
    public boolean cancelWindowsJob(Integer windowsJobId) {
        if (windowsJobId == null) return true;
        String queue = getQueueName();
        LOGGER.info("[WINDOWS SPOOL CANCEL] Purging Job #" + windowsJobId + " from queue '" + queue + "'...");

        try {
            File scriptFile = new File("scripts/cancel-job.ps1");
            ProcessBuilder pb;
            if (scriptFile.exists()) {
                pb = new ProcessBuilder(
                        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", scriptFile.getAbsolutePath(),
                        "-JobId", String.valueOf(windowsJobId),
                        "-PrinterName", queue
                );
            } else {
                String cmd = String.format(
                        "Remove-PrintJob -PrinterName '%s' -ID %d -ErrorAction SilentlyContinue",
                        queue, windowsJobId
                );
                pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[PS-CANCEL] " + line);
                }
            }

            return process.waitFor() == 0;
        } catch (Exception e) {
            LOGGER.severe("[WINDOWS SPOOL CANCEL FAILED] Error cancelling job #" + windowsJobId + ": " + e.getMessage());
            return false;
        }
    }
}
