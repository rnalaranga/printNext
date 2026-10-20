package com.rr.secureprint.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rr.secureprint.dto.ServerPrinterDto;
import com.rr.secureprint.dto.VirtualQueueConfigDto;
import com.rr.secureprint.dto.VirtualQueueSaveRequest;
import com.rr.secureprint.dto.VirtualQueueSummaryDto;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.VirtualQueueEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.VirtualQueueRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final VirtualQueueRepository virtualQueueRepository;
    private final RawSocketPrinterService rawSocketPrinterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile List<ServerPrinterDto> cachedPrinters = null;
    private volatile long printersCacheTime = 0;
    private static final long PRINTERS_CACHE_TTL_MS = 45_000;

    private final Map<String, Boolean> cachedIsPausedMap = new HashMap<>();
    private final Map<String, Long> isPausedCacheTimeMap = new HashMap<>();
    private static final long PAUSED_CACHE_TTL_MS = 15_000;

    public VirtualQueueConfigService(AuditService auditService,
                                     PrinterRepository printerRepository,
                                     VirtualQueueRepository virtualQueueRepository,
                                     RawSocketPrinterService rawSocketPrinterService) {
        this.auditService = auditService;
        this.printerRepository = printerRepository;
        this.virtualQueueRepository = virtualQueueRepository;
        this.rawSocketPrinterService = rawSocketPrinterService;
    }

    @PostConstruct
    @Transactional
    public void init() {
        try {
            ensureDefaultVirtualQueueExists();
        } catch (Exception e) {
            LOGGER.warning("[VIRTUAL QUEUE CONFIG] Error ensuring default virtual queue: " + e.getMessage());
        }

        // Asynchronously pre-warm server printers cache
        Thread.ofVirtual().start(() -> {
            try {
                getInstalledServerPrinters(true);
            } catch (Exception e) {
                LOGGER.warning("[VIRTUAL QUEUE CONFIG] Background printer pre-warm: " + e.getMessage());
            }
        });
    }

    @Transactional
    public void ensureDefaultVirtualQueueExists() {
        List<VirtualQueueEntity> existing = virtualQueueRepository.findAll();
        if (existing.isEmpty()) {
            String defaultPrinterName = "QOA_Printer";
            try {
                if (Files.exists(CONFIG_FILE)) {
                    String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
                    Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
                    if (map.containsKey("selectedPrinterName") && map.get("selectedPrinterName") != null) {
                        defaultPrinterName = map.get("selectedPrinterName").toString().trim();
                    }
                }
            } catch (Exception ignored) {}

            VirtualQueueEntity defaultQueue = new VirtualQueueEntity(
                    "VQ-DEFAULT",
                    "General Find-Me Virtual Queue",
                    defaultPrinterName,
                    defaultPrinterName,
                    "TOSHIBA Universal Printer 2",
                    "Default Organization Pull Print Virtual Queue",
                    true
            );
            List<PrinterEntity> allPrinters = printerRepository.findAll();
            defaultQueue.setPhysicalPrinters(new ArrayList<>(allPrinters));
            virtualQueueRepository.save(defaultQueue);
            LOGGER.info("[VIRTUAL QUEUE CONFIG] Created initial default virtual queue: " + defaultQueue.getName() + " with " + allPrinters.size() + " physical printers.");
        }
    }

    @Transactional(readOnly = true)
    public List<VirtualQueueSummaryDto> getAllQueuesSummary() {
        return virtualQueueRepository.findAllByOrderByIsDefaultDescIdAsc().stream()
                .map(q -> new VirtualQueueSummaryDto(
                        q.getId(),
                        q.getQueueCode(),
                        q.getName(),
                        q.getWindowsPrinterName(),
                        q.getShareName(),
                        q.getDriverName(),
                        q.getDescription(),
                        q.isDefault(),
                        q.isActive(),
                        q.getPhysicalPrinters() != null ? q.getPhysicalPrinters().size() : 0,
                        q.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public VirtualQueueConfigDto getConfig() {
        VirtualQueueEntity queue = virtualQueueRepository.findByIsDefaultTrue()
                .orElseGet(() -> virtualQueueRepository.findAllByOrderByIsDefaultDescIdAsc().stream().findFirst().orElse(null));

        if (queue == null) {
            ensureDefaultVirtualQueueExists();
            queue = virtualQueueRepository.findAll().stream().findFirst().orElseThrow();
        }
        return buildConfigDto(queue);
    }

    @Transactional(readOnly = true)
    public VirtualQueueConfigDto getQueueConfig(Long id) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + id));
        return buildConfigDto(queue);
    }

    private VirtualQueueConfigDto buildConfigDto(VirtualQueueEntity queue) {
        List<ServerPrinterDto> printers = getInstalledServerPrinters(false);
        String selectedPrinterName = queue.getWindowsPrinterName();

        ServerPrinterDto current = printers.stream()
                .filter(p -> p.name() != null && p.name().equalsIgnoreCase(selectedPrinterName))
                .findFirst()
                .orElse(null);

        String host = "127.0.0.1";
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}

        String shareName = queue.getShareName() != null && !queue.getShareName().isBlank()
                ? queue.getShareName()
                : (current != null && current.shareName() != null ? current.shareName() : selectedPrinterName);

        boolean isShared = current != null ? Boolean.TRUE.equals(current.shared()) : false;
        String driver = queue.getDriverName() != null && !queue.getDriverName().isBlank()
                ? queue.getDriverName()
                : (current != null && current.driverName() != null ? current.driverName() : "TOSHIBA Universal Printer 2");

        int jobCount = current != null && current.jobCount() != null ? current.jobCount() : 0;
        boolean isPaused = checkIsQueuePaused(selectedPrinterName);
        String networkSharePath = "\\\\" + host + "\\" + shareName;

        List<PrinterEntity> physicalPrinters = queue.getPhysicalPrinters() != null
                ? new ArrayList<>(queue.getPhysicalPrinters())
                : Collections.emptyList();

        boolean liveMode = !rawSocketPrinterService.isSimulationMode();
        List<VirtualQueueSummaryDto> allQueues = getAllQueuesSummary();

        return new VirtualQueueConfigDto(
                queue.getId(),
                queue.getQueueCode(),
                queue.getName(),
                selectedPrinterName,
                shareName,
                driver,
                queue.getDescription(),
                queue.isDefault(),
                isShared,
                isPaused,
                jobCount,
                host,
                networkSharePath,
                printers,
                physicalPrinters,
                physicalPrinters.size(),
                liveMode,
                allQueues
        );
    }

    @Transactional
    public VirtualQueueConfigDto createQueue(VirtualQueueSaveRequest req, String actor, String clientIp) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Virtual Queue name is required.");
        }
        String printerName = req.windowsPrinterName() != null && !req.windowsPrinterName().isBlank()
                ? req.windowsPrinterName().trim()
                : req.name().trim();

        String code = req.queueCode() != null && !req.queueCode().isBlank()
                ? req.queueCode().trim().toUpperCase()
                : "VQ-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        if (virtualQueueRepository.findByQueueCode(code).isPresent()) {
            throw new IllegalArgumentException("Queue code '" + code + "' already exists.");
        }

        boolean makeDefault = Boolean.TRUE.equals(req.isDefault()) || virtualQueueRepository.count() == 0;
        if (makeDefault) {
            virtualQueueRepository.findAll().forEach(q -> q.setDefault(false));
        }

        VirtualQueueEntity queue = new VirtualQueueEntity(
                code,
                req.name().trim(),
                printerName,
                req.shareName() != null ? req.shareName().trim() : printerName,
                req.driverName() != null ? req.driverName().trim() : "TOSHIBA Universal Printer 2",
                req.description(),
                makeDefault
        );

        if (req.physicalPrinterIds() != null && !req.physicalPrinterIds().isEmpty()) {
            List<PrinterEntity> printers = printerRepository.findAllById(req.physicalPrinterIds());
            queue.setPhysicalPrinters(printers);
        }

        VirtualQueueEntity saved = virtualQueueRepository.save(queue);
        pauseWindowsQueue(saved.getWindowsPrinterName());

        auditService.logEvent("VIRTUAL_QUEUE_CREATED", null, actor, null,
                saved.getQueueCode(), clientIp,
                "Created new Virtual Queue '" + saved.getName() + "' (" + saved.getQueueCode() + ") pointing to Windows printer '" + saved.getWindowsPrinterName() + "' with " + saved.getPhysicalPrinters().size() + " linked MFPs.",
                "SUCCESS");

        LOGGER.info("[VIRTUAL QUEUE] Created virtual queue: " + saved.getName() + " (" + saved.getQueueCode() + ")");
        return buildConfigDto(saved);
    }

    @Transactional
    public VirtualQueueConfigDto updateQueue(Long id, VirtualQueueSaveRequest req, String actor, String clientIp) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + id));

        if (req.name() != null && !req.name().isBlank()) {
            queue.setName(req.name().trim());
        }
        if (req.windowsPrinterName() != null && !req.windowsPrinterName().isBlank()) {
            queue.setWindowsPrinterName(req.windowsPrinterName().trim());
            pauseWindowsQueue(queue.getWindowsPrinterName());
        }
        if (req.shareName() != null && !req.shareName().isBlank()) {
            queue.setShareName(req.shareName().trim());
        }
        if (req.driverName() != null && !req.driverName().isBlank()) {
            queue.setDriverName(req.driverName().trim());
        }
        if (req.description() != null) {
            queue.setDescription(req.description().trim());
        }
        if (Boolean.TRUE.equals(req.isDefault()) && !queue.isDefault()) {
            virtualQueueRepository.findAll().forEach(q -> q.setDefault(false));
            queue.setDefault(true);
        }

        if (req.physicalPrinterIds() != null) {
            List<PrinterEntity> printers = printerRepository.findAllById(req.physicalPrinterIds());
            queue.setPhysicalPrinters(printers);
        }

        VirtualQueueEntity saved = virtualQueueRepository.save(queue);
        auditService.logEvent("VIRTUAL_QUEUE_UPDATED", null, actor, null,
                saved.getQueueCode(), clientIp,
                "Updated Virtual Queue '" + saved.getName() + "' (" + saved.getQueueCode() + "). Linked physical MFPs: " + saved.getPhysicalPrinters().size(),
                "SUCCESS");

        return buildConfigDto(saved);
    }

    @Transactional
    public void deleteQueue(Long id, String actor, String clientIp) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + id));

        if (virtualQueueRepository.count() <= 1) {
            throw new IllegalArgumentException("Cannot delete the only remaining Virtual Queue.");
        }
        if (queue.isDefault()) {
            throw new IllegalArgumentException("Cannot delete the Default Virtual Queue. Designate another queue as default first.");
        }

        virtualQueueRepository.delete(queue);
        auditService.logEvent("VIRTUAL_QUEUE_DELETED", null, actor, null,
                queue.getQueueCode(), clientIp,
                "Deleted Virtual Queue '" + queue.getName() + "' (" + queue.getQueueCode() + ").",
                "SUCCESS");
        LOGGER.info("[VIRTUAL QUEUE] Deleted virtual queue: " + queue.getName());
    }

    @Transactional
    public VirtualQueueConfigDto setDefaultQueue(Long id, String actor, String clientIp) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + id));

        virtualQueueRepository.findAll().forEach(q -> q.setDefault(false));
        queue.setDefault(true);
        VirtualQueueEntity saved = virtualQueueRepository.save(queue);
        saveConfigFile(saved.getWindowsPrinterName());

        auditService.logEvent("VIRTUAL_QUEUE_DEFAULT_SET", null, actor, null,
                saved.getQueueCode(), clientIp,
                "Set Virtual Queue '" + saved.getName() + "' (" + saved.getQueueCode() + ") as the Primary Default Queue.",
                "SUCCESS");

        return buildConfigDto(saved);
    }

    @Transactional
    public VirtualQueueConfigDto assignPhysicalPrinters(Long queueId, List<Long> printerIds, String actor, String clientIp) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + queueId));

        List<PrinterEntity> printers = (printerIds != null && !printerIds.isEmpty())
                ? printerRepository.findAllById(printerIds)
                : Collections.emptyList();

        queue.setPhysicalPrinters(printers);
        VirtualQueueEntity saved = virtualQueueRepository.save(queue);

        auditService.logEvent("VIRTUAL_QUEUE_POOL_UPDATED", null, actor, null,
                saved.getQueueCode(), clientIp,
                "Assigned " + printers.size() + " physical MFPs to Virtual Queue '" + saved.getName() + "'.",
                "SUCCESS");

        return buildConfigDto(saved);
    }

    @Transactional
    public VirtualQueueConfigDto togglePrinterInPool(Long queueId, Long printerId, String actor, String clientIp) {
        VirtualQueueEntity queue = virtualQueueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Virtual Queue not found with ID: " + queueId));
        PrinterEntity printer = printerRepository.findById(printerId)
                .orElseThrow(() -> new IllegalArgumentException("Physical Printer not found with ID: " + printerId));

        boolean exists = queue.getPhysicalPrinters().stream().anyMatch(p -> p.getId().equals(printerId));
        if (exists) {
            queue.getPhysicalPrinters().removeIf(p -> p.getId().equals(printerId));
        } else {
            queue.getPhysicalPrinters().add(printer);
        }

        VirtualQueueEntity saved = virtualQueueRepository.save(queue);
        String action = exists ? "Removed" : "Added";
        auditService.logEvent("VIRTUAL_QUEUE_POOL_TOGGLE", null, actor, null,
                saved.getQueueCode(), clientIp,
                action + " physical printer '" + printer.getName() + "' (" + printer.getPrinterCode() + ") to Virtual Queue '" + saved.getName() + "'.",
                "SUCCESS");

        return buildConfigDto(saved);
    }

    public synchronized String getSelectedPrinterName() {
        return virtualQueueRepository.findByIsDefaultTrue()
                .map(VirtualQueueEntity::getWindowsPrinterName)
                .orElse("QOA_Printer");
    }

    @Transactional
    public synchronized VirtualQueueConfigDto selectPrinter(String printerName, String adminActor, String clientIp) {
        if (printerName == null || printerName.isBlank()) {
            throw new IllegalArgumentException("Printer name cannot be blank.");
        }
        String cleanName = printerName.trim();

        VirtualQueueEntity queue = virtualQueueRepository.findByIsDefaultTrue()
                .orElseGet(() -> virtualQueueRepository.findAll().stream().findFirst().orElse(null));

        if (queue != null) {
            queue.setWindowsPrinterName(cleanName);
            queue.setShareName(cleanName);
            virtualQueueRepository.save(queue);
        }

        saveConfigFile(cleanName);
        pauseWindowsQueue(cleanName);

        auditService.logEvent("VIRTUAL_QUEUE_CHANGED", null, adminActor, null,
                "VIRTUAL-QUEUE", clientIp,
                "Designated Windows printer '" + cleanName + "' as active Virtual Print Queue.",
                "SUCCESS");

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
        Long cachedTime = isPausedCacheTimeMap.get(printerName);
        Boolean cachedVal = cachedIsPausedMap.get(printerName);
        if (cachedVal != null && cachedTime != null && (now - cachedTime < PAUSED_CACHE_TTL_MS)) {
            return cachedVal;
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
                cachedIsPausedMap.put(printerName, paused);
                isPausedCacheTimeMap.put(printerName, now);
                return paused;
            }
        } catch (Exception ignored) {
            return cachedVal != null ? cachedVal : false;
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
