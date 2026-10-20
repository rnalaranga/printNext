package com.rr.secureprint.controller;

import com.rr.secureprint.dto.VirtualQueueConfigDto;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.service.SpoolStorageService;
import com.rr.secureprint.service.VirtualQueueConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final SpoolStorageService spoolStorageService;
    private final PrinterRepository printerRepository;
    private final VirtualQueueConfigService virtualQueueConfigService;

    @Value("${secureprint.simulation-mode:false}")
    private boolean simulationMode;

    public HealthController(SpoolStorageService spoolStorageService,
                            PrinterRepository printerRepository,
                            VirtualQueueConfigService virtualQueueConfigService) {
        this.spoolStorageService = spoolStorageService;
        this.printerRepository = printerRepository;
        this.virtualQueueConfigService = virtualQueueConfigService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("service", "nexPrint Server");
        resp.put("status", "UP");
        resp.put("version", "1.0.0-MVP");
        resp.put("simulationMode", simulationMode);

        File spoolDir = spoolStorageService.getRootStoragePath() != null 
                ? spoolStorageService.getRootStoragePath().toFile() 
                : new File("spool_data");
        Map<String, Object> spoolInfo = new LinkedHashMap<>();
        spoolInfo.put("path", spoolDir.getAbsolutePath());
        spoolInfo.put("exists", spoolDir.exists());
        spoolInfo.put("freeSpaceMB", spoolDir.getFreeSpace() / (1024 * 1024));
        resp.put("spoolStorage", spoolInfo);

        Map<String, Object> toshiba = new LinkedHashMap<>();
        toshiba.put("ewbSupported", true);
        toshiba.put("recommendedResolution", "1024x600");
        toshiba.put("jsEngineTarget", "ES5");
        resp.put("toshibaCompatibility", toshiba);

        String hostAddress = "127.0.0.1";
        try {
            hostAddress = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        resp.put("serverIp", hostAddress);
        resp.put("defaultTerminalUrl", "http://" + hostAddress + ":8080/");

        VirtualQueueConfigDto vq = virtualQueueConfigService.getConfig();
        Map<String, Object> virtualQueue = new LinkedHashMap<>();
        virtualQueue.put("name", "Virtual Print Queue");
        virtualQueue.put("selectedPrinterName", vq.selectedPrinterName());
        virtualQueue.put("shareName", vq.shareName());
        virtualQueue.put("windowsShare", vq.networkSharePath());
        virtualQueue.put("driverName", vq.driverName());
        virtualQueue.put("isShared", vq.isShared());
        virtualQueue.put("jobCount", vq.jobCount());
        virtualQueue.put("architecture", "PaperCut-Style Find-Me Pull Print");
        resp.put("virtualQueue", virtualQueue);

        PrinterEntity defaultPrinter = printerRepository.findAll().stream()
                .filter(PrinterEntity::isDefaultHoldQueue)
                .findFirst()
                .or(() -> printerRepository.findByIsActiveTrue().stream().findFirst())
                .orElse(null);

        if (defaultPrinter != null) {
            Map<String, Object> defMap = new LinkedHashMap<>();
            defMap.put("id", defaultPrinter.getId());
            defMap.put("printerCode", defaultPrinter.getPrinterCode());
            defMap.put("name", defaultPrinter.getName());
            defMap.put("ipAddress", defaultPrinter.getIpAddress());
            defMap.put("rawPort", defaultPrinter.getRawPort());
            defMap.put("model", defaultPrinter.getModel());
            resp.put("defaultHoldPrinter", defMap);
        }

        resp.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(resp);
    }
}
