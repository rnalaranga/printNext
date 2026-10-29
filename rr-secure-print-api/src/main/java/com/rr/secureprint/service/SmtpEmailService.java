package com.rr.secureprint.service;

import com.rr.secureprint.entity.SystemSettingsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Robust zero-dependency SMTP client supporting Plain, STARTTLS (port 587), and SSL (port 465).
 * Sends standard MIME multipart/mixed messages with PDF attachments.
 * Includes local storage simulation mode when no external SMTP server is configured.
 */
@Service
public class SmtpEmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
    private static final int TIMEOUT_MS = 10000;

    public record EmailResult(boolean success, String message, String savedFilePath) {}

    /**
     * Dispatches scanned document to user's email address.
     */
    public EmailResult sendScanEmail(String recipientEmail, String subject, String fileName,
                                     byte[] attachmentBytes, String username, String printerName,
                                     SystemSettingsEntity settings) {

        if (recipientEmail == null || recipientEmail.isBlank()) {
            return new EmailResult(false, "Recipient email address is required.", null);
        }

        String safeFileName = (fileName != null && !fileName.isBlank()) ? fileName.trim() : "Scanned_Document.pdf";
        if (!safeFileName.toLowerCase().endsWith(".pdf")) {
            safeFileName += ".pdf";
        }

        String safeSubject = (subject != null && !subject.isBlank()) ? subject.trim() : "Scanned Document from " + printerName;

        String smtpHost = settings != null ? settings.getSmtpHost() : null;
        int smtpPort = (settings != null && settings.getSmtpPort() != null && settings.getSmtpPort() > 0)
                ? settings.getSmtpPort() : 587;
        String fromAddr = (settings != null && settings.getSmtpFromAddress() != null && !settings.getSmtpFromAddress().isBlank())
                ? settings.getSmtpFromAddress().trim() : "scan@rrconstruction.lk";
        String fromName = (settings != null && settings.getSmtpFromName() != null && !settings.getSmtpFromName().isBlank())
                ? settings.getSmtpFromName().trim() : "RR MFP Scanner";
        boolean useTls = settings == null || settings.getSmtpUseTls() == null || settings.getSmtpUseTls();
        boolean useSsl = settings != null && Boolean.TRUE.equals(settings.getSmtpUseSsl());
        String smtpUser = settings != null ? settings.getSmtpUsername() : null;
        String smtpPass = settings != null ? settings.getSmtpPassword() : null;

        // Save local copy in data/scans for archiving & audit
        String savedLocalPath = null;
        try {
            Path scanDir = Paths.get("data", "scans");
            Files.createDirectories(scanDir);
            Path targetFile = scanDir.resolve(safeFileName);
            Files.write(targetFile, attachmentBytes);
            savedLocalPath = targetFile.toAbsolutePath().toString();
            log.info("[SCAN ARCHIVE] Saved copy to {}", savedLocalPath);
        } catch (Exception ex) {
            log.warn("[SCAN ARCHIVE] Could not save local copy: {}", ex.getMessage());
        }

        // Check if an external SMTP relay host is configured
        if (smtpHost == null || smtpHost.isBlank() || "localhost".equalsIgnoreCase(smtpHost) || "127.0.0.1".equals(smtpHost)) {
            log.info("[SCAN SIMULATION] External SMTP not configured. Simulating delivery to {} (Saved: {})", recipientEmail, savedLocalPath);
            return new EmailResult(true, "Document successfully scanned and delivered to " + recipientEmail + " (Simulated Mode)", savedLocalPath);
        }

        // Live SMTP delivery
        try {
            deliverViaSmtp(smtpHost, smtpPort, smtpUser, smtpPass, fromAddr, fromName,
                    recipientEmail.trim(), safeSubject, safeFileName, attachmentBytes, useTls, useSsl);

            log.info("[SMTP SUCCESS] Dispatched scan '{}' to {} via {}:{}", safeFileName, recipientEmail, smtpHost, smtpPort);
            return new EmailResult(true, "Document successfully sent to " + recipientEmail + " via SMTP relay (" + smtpHost + ").", savedLocalPath);
        } catch (Exception ex) {
            log.error("[SMTP ERROR] Failed to send email to {} via {}:{}: {}", recipientEmail, smtpHost, smtpPort, ex.getMessage(), ex);
            return new EmailResult(false, "SMTP Error: " + ex.getMessage() + (savedLocalPath != null ? " (Scanned file preserved locally)" : ""), savedLocalPath);
        }
    }

    /**
     * Direct test for SMTP credentials from Admin portal.
     */
    public EmailResult testSmtpConnection(String host, int port, String user, String pass,
                                          String fromAddr, String testRecipient, boolean useTls, boolean useSsl) {
        try {
            byte[] testPdf = PdfDocumentGenerator.generateScannedPdf("SMTP Diagnostic Test Document", "Administrator",
                    "Toshiba e-STUDIO Test", "Color", "300 DPI", 1);
            deliverViaSmtp(host, port, user, pass, fromAddr, "RR MFP Test Scanner",
                    testRecipient, "nexPrint SMTP Test - " + host, "SMTP_Test_Scan.pdf", testPdf, useTls, useSsl);
            return new EmailResult(true, "Test email sent successfully to " + testRecipient + " via " + host + ":" + port, null);
        } catch (Exception ex) {
            return new EmailResult(false, "SMTP Test Failed: " + ex.getMessage(), null);
        }
    }

    private void deliverViaSmtp(String host, int port, String username, String password,
                                String fromAddr, String fromName, String toAddr,
                                String subject, String fileName, byte[] attachmentBytes,
                                boolean useTls, boolean useSsl) throws Exception {

        Socket socket;
        if (useSsl) {
            SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) ssf.createSocket();
            sslSocket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        }
        socket.setSoTimeout(TIMEOUT_MS);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

        // Read initial server banner
        expectReply(reader, 220);

        // Send EHLO
        sendLine(writer, "EHLO " + getLocalHost());
        String ehloResp = readMultilineReply(reader, 250);

        // Upgrade via STARTTLS if requested and supported
        if (useTls && !useSsl) {
            if (ehloResp.contains("STARTTLS")) {
                sendLine(writer, "STARTTLS");
                expectReply(reader, 220);

                SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) ssf.createSocket(socket, host, port, true);
                sslSocket.startHandshake();

                socket = sslSocket;
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

                sendLine(writer, "EHLO " + getLocalHost());
                readMultilineReply(reader, 250);
            }
        }

        // Authenticate if credentials are provided
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            sendLine(writer, "AUTH LOGIN");
            expectReply(reader, 334);
            sendLine(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
            expectReply(reader, 334);
            sendLine(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
            expectReply(reader, 235);
        }

        // MAIL FROM
        sendLine(writer, "MAIL FROM:<" + fromAddr + ">");
        expectReply(reader, 250);

        // RCPT TO
        sendLine(writer, "RCPT TO:<" + toAddr + ">");
        expectReply(reader, 250);

        // DATA
        sendLine(writer, "DATA");
        expectReply(reader, 354);

        // Construct MIME multipart message
        String boundary = "----=_Part_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String dateHeader = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);

        StringBuilder headers = new StringBuilder();
        headers.append("Date: ").append(dateHeader).append("\r\n");
        headers.append("From: ").append(fromName != null ? "\"" + fromName + "\" <" + fromAddr + ">" : fromAddr).append("\r\n");
        headers.append("To: ").append(toAddr).append("\r\n");
        headers.append("Subject: ").append(subject).append("\r\n");
        headers.append("MIME-Version: 1.0\r\n");
        headers.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n");
        headers.append("\r\n");
        writer.write(headers.toString());

        // Body part (HTML & Text notification)
        StringBuilder bodyPart = new StringBuilder();
        bodyPart.append("--").append(boundary).append("\r\n");
        bodyPart.append("Content-Type: text/html; charset=UTF-8\r\n");
        bodyPart.append("Content-Transfer-Encoding: 7bit\r\n");
        bodyPart.append("\r\n");
        bodyPart.append("<html><body style='font-family: Arial, sans-serif; color: #333;'>");
        bodyPart.append("<h2 style='color: #1D4ED8;'>Scanned Document Received</h2>");
        bodyPart.append("<p>Please find attached your document <strong>").append(fileName).append("</strong> scanned from the Toshiba e-STUDIO Multifunction Printer.</p>");
        bodyPart.append("<hr style='border: 0; border-top: 1px solid #ddd; margin: 16px 0;'/>");
        bodyPart.append("<p style='font-size: 12px; color: #777;'>Generated by <strong>nexPrint Secure Pull-Print &amp; Scan System</strong> for RR Construction.</p>");
        bodyPart.append("</body></html>\r\n");
        bodyPart.append("\r\n");
        writer.write(bodyPart.toString());

        // Attachment part
        StringBuilder attachHeader = new StringBuilder();
        attachHeader.append("--").append(boundary).append("\r\n");
        attachHeader.append("Content-Type: application/pdf; name=\"").append(fileName).append("\"\r\n");
        attachHeader.append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n");
        attachHeader.append("Content-Transfer-Encoding: base64\r\n");
        attachHeader.append("\r\n");
        writer.write(attachHeader.toString());

        // Base64 encode and write with 76 char line wraps
        String base64Data = Base64.getEncoder().encodeToString(attachmentBytes);
        int offset = 0;
        int len = base64Data.length();
        while (offset < len) {
            int chunk = Math.min(76, len - offset);
            writer.write(base64Data, offset, chunk);
            writer.write("\r\n");
            offset += chunk;
        }

        // Close multipart boundary and end DATA stream
        writer.write("\r\n--" + boundary + "--\r\n");
        writer.write(".\r\n");
        writer.flush();

        expectReply(reader, 250);

        // QUIT
        try {
            sendLine(writer, "QUIT");
            expectReply(reader, 221);
        } catch (Exception ignored) {}

        socket.close();
    }

    private static void sendLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line + "\r\n");
        writer.flush();
    }

    private static void expectReply(BufferedReader reader, int expectedCode) throws IOException {
        readMultilineReply(reader, expectedCode);
    }

    private static String readMultilineReply(BufferedReader reader, int expectedCode) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
        if (!sb.toString().startsWith(String.valueOf(expectedCode))) {
            throw new IOException("SMTP command failed: " + sb);
        }
        return sb.toString();
    }

    private static String getLocalHost() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
