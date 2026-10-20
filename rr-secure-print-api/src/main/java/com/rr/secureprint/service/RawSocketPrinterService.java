package com.rr.secureprint.service;

import com.rr.secureprint.entity.PrinterEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Handles communication with physical printers via standard RAW/JetDirect protocol (Port 9100).
 * Governed strictly by the 'app.printer.simulation-mode' safety switch to prevent premature
 * physical print transmission.
 */
@Service
public class RawSocketPrinterService {

    private static final Logger LOGGER = Logger.getLogger(RawSocketPrinterService.class.getName());

    @Value("${app.printer.simulation-mode:false}")
    private boolean simulationMode;

    @Value("${app.printer.connection-timeout-ms:5000}")
    private int connectionTimeoutMs;

    public PrintTransmissionResult transmitSpoolData(PrinterEntity printer, byte[] spoolData, String jobSummary) {
        if (simulationMode) {
            LOGGER.info("[SIMULATED PRINT TRANSMISSION] Simulation mode is ENABLED. " +
                    "Printer: " + printer.getName() + " (" + printer.getIpAddress() + ":" + printer.getRawPort() + "), " +
                    "Spool Size: " + spoolData.length + " bytes, Document: " + jobSummary);
            return new PrintTransmissionResult(true, true,
                    "SIMULATED: Spool bytes processed and verified. Physical transmission prevented by simulation guard.");
        }

        // Live physical printer transmission via Raw Socket (Port 9100)
        LOGGER.info("[LIVE TRANSMISSION] Sending " + spoolData.length + " bytes to " +
                printer.getIpAddress() + ":" + printer.getRawPort() + " (" + printer.getName() + ")...");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.getIpAddress(), printer.getRawPort()), connectionTimeoutMs);
            socket.setSoTimeout(15000);

            try (OutputStream out = socket.getOutputStream()) {
                out.write(spoolData);
                out.flush();
            }

            LOGGER.info("[LIVE TRANSMISSION SUCCESS] Bytes written to " + printer.getIpAddress());
            return new PrintTransmissionResult(true, false, "Live print data successfully delivered to printer buffer.");
        } catch (Exception e) {
            LOGGER.severe("[LIVE TRANSMISSION FAILED] Error transmitting to " + printer.getIpAddress() + ": " + e.getMessage());
            return new PrintTransmissionResult(false, false, "Printer transmission error: " + e.getMessage());
        }
    }

    public com.rr.secureprint.dto.PrinterTestResult testConnection(String ipAddress, int rawPort) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(ipAddress, rawPort), 3000);
            long elapsed = System.currentTimeMillis() - start;
            return new com.rr.secureprint.dto.PrinterTestResult(true, elapsed, "Connected to RAW port " + rawPort + " successfully in " + elapsed + "ms.");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return new com.rr.secureprint.dto.PrinterTestResult(false, elapsed, "Connection failed: " + e.getMessage());
        }
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public record PrintTransmissionResult(boolean success, boolean simulated, String message) {}
}
