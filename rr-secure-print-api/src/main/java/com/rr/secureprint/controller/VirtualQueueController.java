package com.rr.secureprint.controller;

import com.rr.secureprint.dto.ServerPrinterDto;
import com.rr.secureprint.dto.VirtualQueueConfigDto;
import com.rr.secureprint.dto.VirtualQueueSelectRequest;
import com.rr.secureprint.service.RawSocketPrinterService;
import com.rr.secureprint.service.VirtualQueueConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/virtual-queue")
public class VirtualQueueController {

    private final VirtualQueueConfigService virtualQueueConfigService;
    private final RawSocketPrinterService rawSocketPrinterService;

    public VirtualQueueController(VirtualQueueConfigService virtualQueueConfigService,
                                  RawSocketPrinterService rawSocketPrinterService) {
        this.virtualQueueConfigService = virtualQueueConfigService;
        this.rawSocketPrinterService = rawSocketPrinterService;
    }

    @GetMapping
    public ResponseEntity<VirtualQueueConfigDto> getVirtualQueueConfig() {
        return ResponseEntity.ok(virtualQueueConfigService.getConfig());
    }

    @PostMapping("/select")
    public ResponseEntity<?> selectVirtualQueuePrinter(
            @RequestBody VirtualQueueSelectRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto updated = virtualQueueConfigService.selectPrinter(request.printerName(), actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Windows printer '" + updated.selectedPrinterName() + "' is now the active Virtual Print Queue.",
                    "config", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/installed-printers")
    public ResponseEntity<List<ServerPrinterDto>> getInstalledServerPrinters(
            @RequestParam(value = "refresh", defaultValue = "true") boolean refresh) {
        return ResponseEntity.ok(virtualQueueConfigService.getInstalledServerPrinters(refresh));
    }

    @PostMapping("/toggle-live-mode")
    public ResponseEntity<?> toggleLiveMode() {
        boolean currentlySimulation = rawSocketPrinterService.isSimulationMode();
        boolean newSimulation = !currentlySimulation;
        rawSocketPrinterService.setSimulationMode(newSimulation);
        boolean liveActive = !newSimulation;
        return ResponseEntity.ok(Map.of(
                "success", true,
                "livePrintingEnabled", liveActive,
                "message", liveActive
                        ? "Live Hardware RAW Socket Transmission (Port 9100) is now ENABLED!"
                        : "Simulation Guard Mode is now ENABLED (Testing without physical paper)."
        ));
    }
}
