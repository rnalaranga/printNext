package com.rr.secureprint.service;

import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.ScanJobEntity;
import com.rr.secureprint.entity.SystemSettingsEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.ScanJobRepository;
import com.rr.secureprint.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embedded Local SMTP Server (Port 25 by default).
 * Receives native scan jobs directly from Toshiba MFPs over LAN,
 * stores the scanned PDF document on the printNext server (data/scans/),
 * records the activity in the system database and audit log,
 * and securely relays the email to the recipient via upstream SMTP (qoa.lk:465 SSL).
 */
@Service
public class SmtpInboundServerService {

    private static final Logger log = LoggerFactory.getLogger(SmtpInboundServerService.class);
    private static final DateTimeFormatter FILE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final int port;
    private final boolean enabled;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final ScanJobRepository scanJobRepository;
    private final SystemSettingsService systemSettingsService;
    private final SmtpEmailService smtpEmailService;
    private final AuditService auditService;
    private final CopyTrackingService copyTrackingService;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    public SmtpInboundServerService(
            @Value("${scan.smtp.inbound.port:25}") int port,
            @Value("${scan.smtp.inbound.enabled:true}") boolean enabled,
            PrinterRepository printerRepository,
            UserRepository userRepository,
            ScanJobRepository scanJobRepository,
            SystemSettingsService systemSettingsService,
            SmtpEmailService smtpEmailService,
            AuditService auditService,
            CopyTrackingService copyTrackingService) {
        this.port = port;
        this.enabled = enabled;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.scanJobRepository = scanJobRepository;
        this.systemSettingsService = systemSettingsService;
        this.smtpEmailService = smtpEmailService;
        this.auditService = auditService;
        this.copyTrackingService = copyTrackingService;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("[SMTP INBOUND] Inbound SMTP server disabled by configuration.");
            return;
        }

        threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "smtp-inbound-worker");
            t.setDaemon(true);
            return t;
        });

        Thread serverThread = new Thread(this::runServer, "smtp-inbound-listener");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        log.info("[SMTP INBOUND] Inbound SMTP server stopped.");
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            log.info("============================================================================");
            log.info(" [SMTP INBOUND] Scan-to-Email Gateway listening on port {}", port);
            log.info(" Toshiba MFPs can route scans to this server (192.168.1.100:{})", port);
            log.info(" Documents will be captured, archived in data/scans/, and relayed via qoa.lk");
            log.info("============================================================================");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(30000);
                    threadPool.submit(() -> handleSmtpSession(clientSocket));
                } catch (IOException e) {
                    if (!running) break;
                    log.warn("[SMTP INBOUND] Socket accept error: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[SMTP INBOUND] Could not bind SMTP server to port {}: {}", port, e.getMessage());
        }
    }

    private void handleSmtpSession(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        log.info("[SMTP INBOUND] Incoming scan connection from {}", clientIp);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {

            writer.write("220 192.168.1.100 printNext Enterprise Scan Gateway Ready\r\n");
            writer.flush();

            String line;
            String sender = null;
            List<String> recipients = new ArrayList<>();
            ByteArrayOutputStream dataBuffer = null;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                String upper = trimmed.toUpperCase(Locale.ROOT);

                if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                    writer.write("250-192.168.1.100\r\n");
                    writer.write("250-8BITMIME\r\n");
                    writer.write("250-SIZE 52428800\r\n");
                    writer.write("250 OK\r\n");
                    writer.flush();
                } else if (upper.startsWith("MAIL FROM:")) {
                    sender = extractEmailAddress(trimmed.substring(10));
                    writer.write("250 2.1.0 Sender OK\r\n");
                    writer.flush();
                } else if (upper.startsWith("RCPT TO:")) {
                    String rcpt = extractEmailAddress(trimmed.substring(8));
                    if (rcpt != null && !rcpt.isBlank()) {
                        recipients.add(rcpt);
                    }
                    writer.write("250 2.1.5 Recipient OK\r\n");
                    writer.flush();
                } else if (upper.equals("DATA")) {
                    writer.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                    writer.flush();

                    dataBuffer = readDataBlock(reader);
                    String jobId = "SCAN-" + System.currentTimeMillis();
                    writer.write("250 2.0.0 OK: queued as " + jobId + "\r\n");
                    writer.flush();

                    // Process received scan asynchronously
                    if (dataBuffer != null && dataBuffer.size() > 0) {
                        byte[] emailRawBytes = dataBuffer.toByteArray();
                        List<String> finalRecipients = new ArrayList<>(recipients);
                        String finalSender = sender;
                        threadPool.submit(() -> processReceivedScan(clientIp, finalSender, finalRecipients, emailRawBytes));
                    }
                } else if (upper.startsWith("RSET")) {
                    sender = null;
                    recipients.clear();
                    writer.write("250 2.0.0 Reset OK\r\n");
                    writer.flush();
                } else if (upper.startsWith("NOOP")) {
                    writer.write("250 2.0.0 OK\r\n");
                    writer.flush();
                } else if (upper.startsWith("QUIT")) {
                    writer.write("221 2.0.0 Bye\r\n");
                    writer.flush();
                    break;
                } else {
                    writer.write("500 5.5.1 Unrecognized command\r\n");
                    writer.flush();
                }
            }
        } catch (Exception ex) {
            log.warn("[SMTP INBOUND] Connection error with {}: {}", clientIp, ex.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private ByteArrayOutputStream readDataBlock(BufferedReader reader) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) {
                break;
            }
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            out.write(line.getBytes(StandardCharsets.ISO_8859_1));
            out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        return out;
    }

    private String extractEmailAddress(String str) {
        if (str == null) return null;
        int start = str.indexOf('<');
        int end = str.lastIndexOf('>');
        if (start != -1 && end != -1 && end > start) {
            return str.substring(start + 1, end).trim();
        }
        return str.replaceAll("[<>]", "").trim();
    }

    /**
     * Parses the MIME message received from the MFP, archives the PDF in data/scans/,
     * updates user quota and scan activity database, and relays the mail via upstream SMTP.
     */
    private void processReceivedScan(String printerIp, String sender, List<String> recipients, byte[] rawMimeBytes) {
        log.info("[SMTP INBOUND] Processing received scan from IP {} ({} bytes)", printerIp, rawMimeBytes.length);

        try {
            Session mailSession = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(mailSession, new ByteArrayInputStream(rawMimeBytes));

            String subject = message.getSubject();
            if (subject == null || subject.isBlank()) {
                subject = "Scanned Document";
            }

            // Extract attachment(s)
            List<ScannedAttachment> attachments = extractAttachments(message);
            if (attachments.isEmpty()) {
                log.warn("[SMTP INBOUND] No attachments found in email from {}. Saving raw payload as PDF.", printerIp);
                attachments.add(new ScannedAttachment("Scan_" + System.currentTimeMillis() + ".pdf", rawMimeBytes, "application/pdf"));
            }

            // Determine recipient: from envelope or message header
            String primaryRecipient = !recipients.isEmpty() ? recipients.get(0) : null;
            if (primaryRecipient == null || primaryRecipient.isBlank()) {
                if (message.getAllRecipients() != null && message.getAllRecipients().length > 0) {
                    primaryRecipient = message.getAllRecipients()[0].toString();
                }
            }
            if (primaryRecipient == null || primaryRecipient.isBlank()) {
                primaryRecipient = "nalaranga@gmail.com";
            }
            primaryRecipient = extractEmailAddress(primaryRecipient);

            // Determine associated printer
            PrinterEntity printer = printerRepository.findByIpAddress(printerIp).orElse(null);
            String printerName = printer != null ? printer.getName() : "Toshiba MFP (" + printerIp + ")";

            // Attribute job to user
            String username = copyTrackingService.resolveTerminalUserForJob(printerIp, Instant.now());
            if (username == null || username.isBlank() || "unknown".equalsIgnoreCase(username)) {
                // Try resolving user by destination email
                String cleanRecip = primaryRecipient;
                username = userRepository.findByEmailIgnoreCase(cleanRecip)
                        .map(UserEntity::getUsername)
                        .orElse(null);
            }
            if (username == null || username.isBlank()) {
                // Try sender address
                if (sender != null && !sender.isBlank()) {
                    username = userRepository.findByEmailIgnoreCase(sender)
                            .map(UserEntity::getUsername)
                            .orElse(null);
                }
            }
            if (username == null || username.isBlank()) {
                username = "admin";
            }
            final String finalUser = username;

            UserEntity user = userRepository.findByUsernameIgnoreCase(finalUser).orElse(null);

            // Process each attachment
            for (ScannedAttachment att : attachments) {
                String timeStamp = LocalDateTime.now().format(FILE_TS_FMT);
                String safeName = att.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!safeName.toLowerCase().endsWith(".pdf") && !safeName.toLowerCase().endsWith(".tif") && !safeName.toLowerCase().endsWith(".jpg")) {
                    safeName += ".pdf";
                }

                // 1. SAVE DOCUMENT ON SERVER DISK (data/scans/<user>/)
                Path userScanDir = Paths.get("data", "scans", finalUser);
                Files.createDirectories(userScanDir);
                Path localSavedFile = userScanDir.resolve(timeStamp + "_" + safeName);
                Files.write(localSavedFile, att.data());
                String absoluteStoredPath = localSavedFile.toAbsolutePath().toString();
                log.info("[SCAN STORED] Captured document saved on server: {}", absoluteStoredPath);

                // Count pages in PDF
                int pages = countPagesInPdf(att.data());

                // 2. CREATE DATABASE SCAN RECORD
                ScanJobEntity scanJob = new ScanJobEntity(
                        user,
                        printer,
                        finalUser,
                        printerName,
                        printerIp,
                        primaryRecipient,
                        subject,
                        safeName,
                        pages,
                        "COLOR",
                        "300DPI",
                        safeName.toUpperCase().endsWith(".TIF") ? "TIFF" : "PDF",
                        "SIMPLEX",
                        "PROCESSING",
                        "Captured by printNext server at " + localSavedFile.getFileName()
                );
                ScanJobEntity savedJob = scanJobRepository.save(scanJob);

                // 3. RELAY VIA UPSTREAM SMTP (qoa.lk:465 SSL)
                SystemSettingsEntity settings = systemSettingsService.getOrCreateSettings();
                SmtpEmailService.EmailResult emailResult = smtpEmailService.sendScanEmail(
                        primaryRecipient,
                        subject,
                        safeName,
                        att.data(),
                        finalUser,
                        printerName,
                        settings
                );

                // 4. UPDATE RECORD STATUS & AUDIT
                String finalStatus = emailResult.success() ? "SENT" : "FAILED";
                savedJob.setStatus(finalStatus);
                savedJob.setDeliveryMessage(emailResult.message() + " (Stored at " + localSavedFile.getFileName() + ")");
                scanJobRepository.save(savedJob);

                if (user != null) {
                    user.setScanCount(user.getScanCount() + pages);
                    userRepository.save(user);
                }

                auditService.logEvent(
                        "SCAN_TO_EMAIL_RELAY",
                        user != null ? user.getUserSid() : "SYSTEM",
                        finalUser,
                        null,
                        printer != null ? printer.getPrinterCode() : "MFP",
                        printerIp,
                        "Relayed scan (" + pages + " pgs) to " + primaryRecipient + " [" + finalStatus + "]",
                        emailResult.success() ? "SUCCESS" : "FAILURE"
                );

                log.info("[SCAN COMPLETED] Job #{} processed. Stored locally: {} -> Relayed to: {} [{}]",
                        savedJob.getId(), absoluteStoredPath, primaryRecipient, finalStatus);
            }

        } catch (Exception ex) {
            log.error("[SMTP INBOUND] Failed to process scan job from {}: {}", printerIp, ex.getMessage(), ex);
        }
    }

    private List<ScannedAttachment> extractAttachments(Part message) throws Exception {
        List<ScannedAttachment> list = new ArrayList<>();
        if (message.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) message.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                if (part.isMimeType("multipart/*")) {
                    list.addAll(extractAttachments(part));
                } else {
                    String disposition = part.getDisposition();
                    String fileName = part.getFileName();
                    String ct = part.getContentType().toLowerCase();

                    boolean isAtt = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                            || fileName != null
                            || ct.contains("application/pdf")
                            || ct.contains("image/tiff")
                            || ct.contains("image/jpeg");

                    if (isAtt) {
                        try (InputStream is = part.getInputStream()) {
                            byte[] bytes = is.readAllBytes();
                            String safeFileName = (fileName != null && !fileName.isBlank())
                                    ? fileName
                                    : "Scan_" + System.currentTimeMillis() + (ct.contains("tiff") ? ".tif" : ".pdf");
                            list.add(new ScannedAttachment(safeFileName, bytes, part.getContentType()));
                        }
                    }
                }
            }
        } else if (message.getContent() instanceof InputStream is) {
            byte[] bytes = is.readAllBytes();
            list.add(new ScannedAttachment("Scan_" + System.currentTimeMillis() + ".pdf", bytes, message.getContentType()));
        }
        return list;
    }

    private int countPagesInPdf(byte[] data) {
        if (data == null || data.length < 50) return 1;
        String content = new String(data, 0, Math.min(data.length, 50000), StandardCharsets.ISO_8859_1);
        Pattern p = Pattern.compile("/Type\\s*/Page\\b");
        Matcher m = p.matcher(content);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return Math.max(count, 1);
    }

    public record ScannedAttachment(String filename, byte[] data, String contentType) {}
}
