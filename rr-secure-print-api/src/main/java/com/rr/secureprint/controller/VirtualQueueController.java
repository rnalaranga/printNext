package com.rr.secureprint.controller;

import com.rr.secureprint.dto.ServerPrinterDto;
import com.rr.secureprint.dto.VirtualQueueAssignRequest;
import com.rr.secureprint.dto.VirtualQueueConfigDto;
import com.rr.secureprint.dto.VirtualQueueSaveRequest;
import com.rr.secureprint.dto.VirtualQueueSelectRequest;
import com.rr.secureprint.dto.VirtualQueueSummaryDto;
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

    @GetMapping("/all")
    public ResponseEntity<List<VirtualQueueSummaryDto>> getAllQueues() {
        return ResponseEntity.ok(virtualQueueConfigService.getAllQueuesSummary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getQueueById(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(virtualQueueConfigService.getQueueConfig(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createVirtualQueue(
            @RequestBody VirtualQueueSaveRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto created = virtualQueueConfigService.createQueue(request, actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Virtual Queue '" + created.name() + "' (" + created.queueCode() + ") created successfully!",
                    "queue", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVirtualQueue(
            @PathVariable("id") Long id,
            @RequestBody VirtualQueueSaveRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto updated = virtualQueueConfigService.updateQueue(id, request, actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Virtual Queue '" + updated.name() + "' updated successfully!",
                    "queue", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVirtualQueue(
            @PathVariable("id") Long id,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            virtualQueueConfigService.deleteQueue(id, actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Virtual Queue deleted successfully!"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/set-default")
    public ResponseEntity<?> setDefaultQueue(
            @PathVariable("id") Long id,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto updated = virtualQueueConfigService.setDefaultQueue(id, actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Virtual Queue '" + updated.name() + "' is now the Primary Default Queue.",
                    "queue", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/assign-printers")
    public ResponseEntity<?> assignPrintersToQueue(
            @PathVariable("id") Long id,
            @RequestBody VirtualQueueAssignRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto updated = virtualQueueConfigService.assignPhysicalPrinters(
                    id, request.physicalPrinterIds(), actor, clientIp
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Physical printers pool updated! (" + updated.physicalPrinterCount() + " printers linked)",
                    "queue", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/toggle-printer/{printerId}")
    public ResponseEntity<?> togglePrinterInPool(
            @PathVariable("id") Long id,
            @PathVariable("printerId") Long printerId,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = httpRequest.getRemoteAddr();
            String actor = "admin";
            VirtualQueueConfigDto updated = virtualQueueConfigService.togglePrinterInPool(id, printerId, actor, clientIp);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Physical printer membership toggled in pool.",
                    "queue", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
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
