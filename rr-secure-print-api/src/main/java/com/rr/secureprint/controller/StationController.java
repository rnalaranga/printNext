package com.rr.secureprint.controller;

import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/station")
public class StationController {

    private final PrinterRepository printerRepository;

    public StationController(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    /**
     * Resolves the physical MFP release station for the current EWB terminal session.
     * Identifies the station via:
     * 1. URL parameter (?printer=PCT0001)
     * 2. Physical device HTTP client IP address matching printer.ipAddress
     * 3. Fallback to first active physical printer (for desktop simulation/browser testing)
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentStation(
            @RequestParam(value = "printer", required = false) String printerParam,
            HttpServletRequest request) {

        String clientIp = request.getRemoteAddr();
        List<PrinterEntity> allPhysical = printerRepository.findAll().stream()
                .filter(p -> p.isActive() && !"VIRTUAL-QUEUE".equalsIgnoreCase(p.getPrinterCode()))
                .toList();

        PrinterEntity matched = null;

        // 1. By explicit printer code param
        if (printerParam != null && !printerParam.isBlank() && !"VIRTUAL-QUEUE".equalsIgnoreCase(printerParam)) {
            matched = printerRepository.findByPrinterCode(printerParam.trim().toUpperCase()).orElse(null);
        }

        // 2. By physical MFP client IP address
        if (matched == null && clientIp != null && !clientIp.isBlank()) {
            String cleanIp = clientIp.replace("/", "").trim();
            for (PrinterEntity p : allPhysical) {
                if (cleanIp.equalsIgnoreCase(p.getIpAddress()) || cleanIp.endsWith(p.getIpAddress())) {
                    matched = p;
                    break;
                }
            }
        }

        // 3. Fallback to first physical printer
        if (matched == null && !allPhysical.isEmpty()) {
            matched = allPhysical.getFirst();
        }

        return ResponseEntity.ok(Map.of(
                "currentStation", matched != null ? matched : Map.of(
                        "printerCode", "STATION-01",
                        "name", "Toshiba e-STUDIO MFP",
                        "ipAddress", "192.168.1.11",
                        "rawPort", 9100,
                        "location", "Main Office"
                ),
                "allStations", allPhysical,
                "virtualQueueName", "RR Secure Virtual Queue",
                "virtualQueueShare", "\\\\SERVER\\RR-Secure-Print",
                "clientIp", clientIp != null ? clientIp : ""
        ));
    }
}
