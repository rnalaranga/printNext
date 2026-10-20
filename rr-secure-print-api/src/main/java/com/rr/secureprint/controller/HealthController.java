package com.rr.secureprint.controller;

import com.rr.secureprint.service.SpoolStorageService;
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

    @Value("${secureprint.simulation-mode:false}")
    private boolean simulationMode;

    public HealthController(SpoolStorageService spoolStorageService) {
        this.spoolStorageService = spoolStorageService;
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

        resp.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(resp);
    }
}
