package com.rr.secureprint.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rr.secureprint.dto.ServerPrinterDto;
import com.rr.secureprint.dto.VirtualQueueConfigDto;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class VirtualQueueConfigService {

    private static final Logger LOGGER = Logger.getLogger(VirtualQueueConfigService.class.getName());
    private static final Path CONFIG_FILE = Paths.get("data", "virtual_queue_config.json");

    private final AuditService auditService;
    private final PrinterRepository printerRepository;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String selectedPrinterName = "QOA_Printer";

    private volatile List<ServerPrinterDto> cachedPrinters = null;
    private volatile long printersCacheTime = 0;
    private static final long PRINTERS_CACHE_TTL_MS = 45_000; // 45 seconds TTL

    private volatile Boolean cachedIsPaused = null;
    private volatile long isPausedCacheTime = 0;
    private static final long PAUSED_CACHE_TTL_MS = 15_000;   // 15 seconds TTL

    public VirtualQueueConfigService(AuditService auditService,
                                     PrinterRepository printerRepository,
                                     RawSocketPrinterService rawSocketPrinterService) {
        this.auditService = auditService;
        this.printerRepository = printerRepository;
        this.rawSocketPrinterService = rawSocketPrinterService;
    }

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
                Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
                if (map.containsKey("selectedPrinterName") && map.get("selectedPrinterName") != null) {
                    this.selectedPrinterName = map.get("selectedPrinterName").toString().trim();
                    LOGGER.info("[VIRTUAL QUEUE CONFIG] Loaded saved virtual queue: " + this.selectedPrinterName);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[VIRTUAL QUEUE CONFIG] Could not read existing config: " + e.getMessage());
        }

        // Asynchronously pre-warm printer cache so application startup is instant
        Thread.ofVirtual().start(() -> {
            try {
                List<ServerPrinterDto> installed = getInstalledServerPrinters(true);
                if (selectedPrinterName == null || selectedPrinterName.isBlank() || "QOA_Printer".equalsIgnoreCase(selectedPrinterName)) {
                    for (ServerPrinterDto p : installed) {
                        if ("QOA_Printer".equalsIgnoreCase(p.name())) {
                            this.selectedPrinterName = p.name();
                            saveConfigFile(this.selectedPrinterName);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("[VIRTUAL QUEUE CONFIG] Background printer pre-warm: " + e.getMessage());
            }
        });
    }

    public synchronized String getSelectedPrinterName() {
        return selectedPrinterName;
    }

    public synchronized VirtualQueueConfigDto getConfig() {
        List<ServerPrinterDto> printers = getInstalledServerPrinters(false);
        ServerPrinterDto current = printers.stream()
                .filter(p -> p.name() != null && p.name().equalsIgnoreCase(selectedPrinterName))
                .findFirst()
                .orElse(null);

        String host = "127.0.0.1";
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}

        String shareName = current != null && current.shareName() != null && !current.shareName().isBlank()
                ? current.shareName()
                : (current != null ? current.name() : selectedPrinterName);

        boolean isShared = current != null && Boolean.TRUE.equals(current.shared());
        String driver = current != null && current.driverName() != null ? current.driverName() : "TOSHIBA Universal Printer 2";
        int jobCount = current != null && current.jobCount() != null ? current.jobCount() : 0;
        boolean isPaused = checkIsQueuePaused(selectedPrinterName);

        String networkSharePath = "\\\\" + host + "\\" + shareName;

        List<PrinterEntity> physicalPrinters = printerRepository.findAll();
        boolean liveMode = !rawSocketPrinterService.isSimulationMode();

        return new VirtualQueueConfigDto(
                selectedPrinterName,
                shareName,
                driver,
                isShared,
                isPaused,
                jobCount,
                host,
                networkSharePath,
                printers,
                physicalPrinters,
                liveMode
        );
    }

    public synchronized VirtualQueueConfigDto selectPrinter(String printerName, String adminActor, String clientIp) {
        if (printerName == null || printerName.isBlank()) {
            throw new IllegalArgumentException("Printer name cannot be blank.");
        }
        String cleanName = printerName.trim();
        List<ServerPrinterDto> installed = getInstalledServerPrinters(false);
        boolean exists = installed.stream().anyMatch(p -> p.name() != null && p.name().equalsIgnoreCase(cleanName));
        if (!exists) {
            LOGGER.warning("[VIRTUAL QUEUE] Selected printer '" + cleanName + "' not found among cached server printers, saving anyway as custom queue name.");
        }

        this.selectedPrinterName = cleanName;
        saveConfigFile(cleanName);

        // Invalidate pause cache and pause queue in Windows
        this.cachedIsPaused = true;
        this.isPausedCacheTime = System.currentTimeMillis();
        pauseWindowsQueue(cleanName);

        auditService.logEvent("VIRTUAL_QUEUE_CHANGED", null, adminActor, null,
                "VIRTUAL-QUEUE", clientIp,
                "Designated Windows printer '" + cleanName + "' as the server Virtual Print Queue for PaperCut Follow-Me printing.",
                "SUCCESS");

        LOGGER.info("[VIRTUAL QUEUE] Designated active virtual queue: " + cleanName);
        return getConfig();
    }

    public List<ServerPrinterDto> getInstalledServerPrinters() {
        return getInstalledServerPrinters(false);
    }

    public synchronized List<ServerPrinterDto> getInstalledServerPrinters(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedPrinters != null && (now - printersCacheTime < PRINTERS_CACHE_TTL_MS)) {
            return cachedPrinters;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", "Get-Printer | Select-Object Name, DriverName, PortName, Shared, ShareName, JobCount | ConvertTo-Json -Compress"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
            }
            p.waitFor();

            String json = sb.toString().trim();
            if (!json.isEmpty()) {
                List<ServerPrinterDto> result;
                if (json.startsWith("{")) {
                    ServerPrinterDto single = objectMapper.readValue(json, ServerPrinterDto.class);
                    result = List.of(single);
                } else if (json.startsWith("[")) {
                    result = objectMapper.readValue(json, new TypeReference<List<ServerPrinterDto>>() {});
                } else {
                    result = Collections.emptyList();
                }
                cachedPrinters = result;
                printersCacheTime = now;
                return result;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to query server printers via PowerShell: " + e.getMessage(), e);
        }

        if (cachedPrinters != null) {
            return cachedPrinters;
        }
        return Collections.emptyList();
    }

    private boolean checkIsQueuePaused(String printerName) {
        if (printerName == null || printerName.isBlank()) return false;
        long now = System.currentTimeMillis();
        if (cachedIsPaused != null && (now - isPausedCacheTime < PAUSED_CACHE_TTL_MS)) {
            return cachedIsPaused;
        }

        try {
            String cmd = String.format(
                    "$p = Get-CimInstance Win32_Printer -Filter \"Name='%s'\" -ErrorAction SilentlyContinue; if ($p) { $p.Paused } else { $false }",
                    printerName.replace("'", "''")
            );
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                boolean paused = line != null && line.trim().equalsIgnoreCase("True");
                cachedIsPaused = paused;
                isPausedCacheTime = now;
                return paused;
            }
        } catch (Exception ignored) {
            return cachedIsPaused != null ? cachedIsPaused : false;
        }
    }

    private void pauseWindowsQueue(String printerName) {
        if (printerName == null || printerName.isBlank()) return;
        try {
            String cmd = String.format(
                    "$p = Get-CimInstance Win32_Printer -Filter \"Name='%s'\" -ErrorAction SilentlyContinue; if ($p) { Invoke-CimMethod -InputObject $p -MethodName Pause | Out-Null }",
                    printerName.replace("'", "''")
            );
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd);
            pb.start();
        } catch (Exception e) {
            LOGGER.warning("[VIRTUAL QUEUE] Could not pause queue '" + printerName + "': " + e.getMessage());
        }
    }

    private void saveConfigFile(String printerName) {
        try {
            File dir = CONFIG_FILE.getParent().toFile();
            if (!dir.exists()) dir.mkdirs();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("selectedPrinterName", printerName);
            map.put("updatedAt", new Date().toString());
            Files.writeString(CONFIG_FILE, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warning("[VIRTUAL QUEUE] Could not persist config file: " + e.getMessage());
        }
    }
}
