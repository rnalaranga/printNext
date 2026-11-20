package com.rr.secureprint.controller;

import com.rr.secureprint.entity.AuditLog;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.PrinterMaintenanceEntity;
import com.rr.secureprint.repository.AuditLogRepository;
import com.rr.secureprint.repository.PrinterMaintenanceRepository;
import com.rr.secureprint.repository.PrinterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/maintenance")
public class PrinterMaintenanceController {

    private final PrinterMaintenanceRepository maintenanceRepository;
    private final PrinterRepository printerRepository;

    @Autowired(required = false)
    private AuditLogRepository auditLogRepository;

    public PrinterMaintenanceController(PrinterMaintenanceRepository maintenanceRepository,
                                        PrinterRepository printerRepository) {
        this.maintenanceRepository = maintenanceRepository;
        this.printerRepository = printerRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "printerCode", required = false) String printerCode,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status) {

        List<PrinterMaintenanceEntity> all = maintenanceRepository.findAllByOrderByServiceDateDescIdDesc();
        List<PrinterMaintenanceEntity> filtered = all.stream().filter(m -> {
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(m.getPrinterCode())) return false;
            if (type != null && !type.isBlank() && !type.equalsIgnoreCase(m.getMaintenanceType())) return false;
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(m.getStatus())) return false;
            return true;
        }).toList();

        long tonerCnt = 0, repairCnt = 0, openCnt = 0;
        double totalCost = 0.0;
        Map<String, Long> repairByPrinter = new LinkedHashMap<>();
        Map<String, Long> tonerByPrinter = new LinkedHashMap<>();
        Map<String, Double> costByPrinter = new LinkedHashMap<>();

        for (PrinterMaintenanceEntity m : filtered) {
            String pKey = (m.getPrinterCode() != null ? m.getPrinterCode() : "Unknown")
                    + (m.getPrinterName() != null && !m.getPrinterName().isBlank() ? " (" + m.getPrinterName() + ")" : "");
            if ("TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType())) {
                tonerCnt++;
                tonerByPrinter.merge(pKey, 1L, Long::sum);
            }
            if ("REPAIR".equalsIgnoreCase(m.getMaintenanceType()) || "DRUM_UNIT".equalsIgnoreCase(m.getMaintenanceType())
                    || "FUSER_UNIT".equalsIgnoreCase(m.getMaintenanceType())) {
                repairCnt++;
                repairByPrinter.merge(pKey, 1L, Long::sum);
            }
            if (!"COMPLETED".equalsIgnoreCase(m.getStatus())) openCnt++;
            double c = m.getCostLkr() != null ? m.getCostLkr() : 0.0;
            totalCost += c;
            costByPrinter.merge(pKey, c, Double::sum);
        }

        String topRepaired = repairByPrinter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " — " + e.getValue() + " repairs")
                .orElse("None");
        String topToner = tonerByPrinter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " — " + e.getValue() + " toners")
                .orElse("None");
        String topSpend = costByPrinter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " — LKR " + Math.round(e.getValue() * 100.0) / 100.0)
                .orElse("None");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", filtered);
        out.put("totalRecords", filtered.size());
        out.put("tonerReplacements", tonerCnt);
        out.put("repairsCount", repairCnt);
        out.put("openTickets", openCnt);
        out.put("totalCostLkr", Math.round(totalCost * 100.0) / 100.0);
        out.put("topRepairedPrinter", topRepaired);
        out.put("topTonerPrinter", topToner);
        out.put("topSpendPrinter", topSpend);
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<PrinterMaintenanceEntity> create(@RequestBody Map<String, Object> body) {
        PrinterMaintenanceEntity e = new PrinterMaintenanceEntity();
        String pCode = body.get("printerCode") != null ? String.valueOf(body.get("printerCode")).trim() : "";
        if (!pCode.isBlank()) {
            printerRepository.findByPrinterCode(pCode.toUpperCase()).ifPresent(p -> {
                e.setPrinterId(p.getId());
                e.setPrinterCode(p.getPrinterCode());
                e.setPrinterName(p.getName());
                e.setPrinterIp(p.getIpAddress());
            });
        }
        if (e.getPrinterCode() == null || e.getPrinterCode().isBlank()) {
            e.setPrinterCode(pCode);
            e.setPrinterName(body.get("printerName") != null ? String.valueOf(body.get("printerName")) : pCode);
        }
        e.setMaintenanceType(body.get("maintenanceType") != null ? String.valueOf(body.get("maintenanceType")).toUpperCase() : "REPAIR");
        e.setTonerColor(body.get("tonerColor") != null ? String.valueOf(body.get("tonerColor")).toUpperCase() : "N/A");
        e.setPartReplaced(body.get("partReplaced") != null ? String.valueOf(body.get("partReplaced")).trim() : "");
        e.setPartNumber(body.get("partNumber") != null ? String.valueOf(body.get("partNumber")).trim() : "");
        e.setPartSerial(body.get("partSerial") != null ? String.valueOf(body.get("partSerial")).trim() : "");
        e.setOdometerPages(body.get("odometerPages") != null && !String.valueOf(body.get("odometerPages")).isBlank()
                ? Long.parseLong(String.valueOf(body.get("odometerPages"))) : 0L);
        e.setCostLkr(body.get("costLkr") != null && !String.valueOf(body.get("costLkr")).isBlank()
                ? Double.parseDouble(String.valueOf(body.get("costLkr"))) : 0.0);
        e.setTechnician(body.get("technician") != null ? String.valueOf(body.get("technician")).trim() : "");
        e.setVendor(body.get("vendor") != null ? String.valueOf(body.get("vendor")).trim() : "");
        e.setStatus(body.get("status") != null ? String.valueOf(body.get("status")).toUpperCase() : "COMPLETED");
        e.setServiceDate(body.get("serviceDate") != null && !String.valueOf(body.get("serviceDate")).isBlank()
                ? String.valueOf(body.get("serviceDate")) : LocalDate.now().toString());
        e.setNextServiceDate(body.get("nextServiceDate") != null ? String.valueOf(body.get("nextServiceDate")).trim() : "");
        e.setNotes(body.get("notes") != null ? String.valueOf(body.get("notes")).trim() : "");
        e.setStartedAt(Instant.now());
        if ("COMPLETED".equalsIgnoreCase(e.getStatus())) {
            e.setCompletedAt(Instant.now());
            e.setDurationMinutes(0L);
        }

        PrinterMaintenanceEntity saved = maintenanceRepository.save(e);
        if (auditLogRepository != null) {
            AuditLog log = new AuditLog();
            log.setEventType("MAINTENANCE_RECORDED");
            log.setUsername("admin");
            log.setPrinterCode(saved.getPrinterCode());
            log.setActionDetails(saved.getMaintenanceType() + " | " + saved.getPartReplaced() + " | LKR " + saved.getCostLkr());
            log.setStatus("SUCCESS");
            log.setTimestamp(Instant.now());
            auditLogRepository.save(log);
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<PrinterMaintenanceEntity> markCompleted(@PathVariable("id") Long id) {
        return maintenanceRepository.findById(id).map(e -> {
            e.setStatus("COMPLETED");
            Instant now = Instant.now();
            e.setCompletedAt(now);
            Instant start = e.getStartedAt() != null ? e.getStartedAt() : e.getCreatedAt();
            if (start != null) {
                long mins = java.time.Duration.between(start, now).toMinutes();
                e.setDurationMinutes(Math.max(1L, mins));
            }
            return ResponseEntity.ok(maintenanceRepository.save(e));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!maintenanceRepository.existsById(id)) return ResponseEntity.notFound().build();
        maintenanceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
