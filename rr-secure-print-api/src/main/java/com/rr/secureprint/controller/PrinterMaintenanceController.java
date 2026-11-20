package com.rr.secureprint.controller;

import com.rr.secureprint.entity.AuditLog;
import com.rr.secureprint.entity.CopyJobEntity;
import com.rr.secureprint.entity.CounterResetLogEntity;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.PrinterMaintenanceEntity;
import com.rr.secureprint.entity.ScanJobEntity;
import com.rr.secureprint.repository.AuditLogRepository;
import com.rr.secureprint.repository.CopyJobRepository;
import com.rr.secureprint.repository.CounterResetLogRepository;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.PrinterMaintenanceRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.ScanJobRepository;
import com.rr.secureprint.service.SmtpEmailService;
import com.rr.secureprint.service.SystemSettingsService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/admin/maintenance")
public class PrinterMaintenanceController {

    private final PrinterMaintenanceRepository maintenanceRepository;
    private final PrinterRepository printerRepository;

    @Autowired(required = false)
    private AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private PrintJobRepository printJobRepository;

    @Autowired(required = false)
    private CopyJobRepository copyJobRepository;

    @Autowired(required = false)
    private ScanJobRepository scanJobRepository;

    @Autowired(required = false)
    private CounterResetLogRepository counterResetLogRepository;

    @Autowired(required = false)
    private SmtpEmailService smtpEmailService;

    @Autowired(required = false)
    private SystemSettingsService systemSettingsService;

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
            if (isRepairType(m.getMaintenanceType())) {
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

        long odo = body.get("odometerPages") != null && !String.valueOf(body.get("odometerPages")).isBlank()
                ? Long.parseLong(String.valueOf(body.get("odometerPages"))) : 0L;
        if (odo <= 0L && e.getPrinterCode() != null && !e.getPrinterCode().isBlank()) {
            PrinterEntity pEnt = printerRepository.findByPrinterCode(e.getPrinterCode().toUpperCase()).orElse(null);
            if (pEnt != null) {
                odo = computeEffectiveCounter(pEnt);
            }
        }
        e.setOdometerPages(odo);

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

    private boolean isRepairType(String type) {
        if (type == null) return false;
        String t = type.toUpperCase();
        return "REPAIR".equals(t) || "DRUM_UNIT".equals(t) || "FUSER_UNIT".equals(t) || "PART_REPLACEMENT".equals(t);
    }

    private long computeActualTotalPages(String printerCode) {
        if (printerCode == null || printerCode.isBlank()) return 0L;
        String code = printerCode.trim().toUpperCase();
        long total = 0L;
        if (printJobRepository != null) {
            for (PrintJob j : printJobRepository.findAll()) {
                String jCode = j.getTargetPrinter() != null ? j.getTargetPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(jCode)) {
                    total += (long) j.getPageCount() * Math.max(1, j.getCopies());
                }
            }
        }
        if (copyJobRepository != null) {
            for (CopyJobEntity c : copyJobRepository.findAll()) {
                String cCode = c.getPrinter() != null ? c.getPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(cCode)) {
                    total += c.getPages();
                }
            }
        }
        if (scanJobRepository != null) {
            for (ScanJobEntity s : scanJobRepository.findAll()) {
                String sCode = s.getPrinter() != null ? s.getPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(sCode)) {
                    total += s.getPages();
                }
            }
        }
        return total;
    }

    private long computeEffectiveCounter(PrinterEntity p) {
        long actual = computeActualTotalPages(p.getPrinterCode());
        long offset = p.getCounterOffsetPages() != null ? p.getCounterOffsetPages() : 0L;
        return Math.max(0L, actual - offset);
    }

    @PostMapping("/reset-counter/{printerCode}")
    public ResponseEntity<Map<String, Object>> resetPrinterCounter(@PathVariable("printerCode") String printerCode) {
        String code = printerCode != null ? printerCode.trim().toUpperCase() : "";
        Optional<PrinterEntity> opt = printerRepository.findByPrinterCode(code);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        PrinterEntity p = opt.get();
        long prevOffset = p.getCounterOffsetPages() != null ? p.getCounterOffsetPages() : 0L;
        long actual = computeActualTotalPages(code);
        Instant now = Instant.now();
        p.setCounterOffsetPages(actual);
        p.setCounterResetAt(now);
        printerRepository.save(p);

        if (counterResetLogRepository != null) {
            CounterResetLogEntity rl = new CounterResetLogEntity();
            rl.setPrinterCode(code);
            rl.setPrinterName(p.getName());
            rl.setDepartment(p.getDepartment());
            rl.setPreviousOffsetPages(prevOffset);
            rl.setActualPagesAtReset(actual);
            rl.setDeltaSinceLastReset(Math.max(0L, actual - prevOffset));
            rl.setResetBy("admin");
            rl.setResetAt(now);
            counterResetLogRepository.save(rl);
        }

        if (auditLogRepository != null) {
            AuditLog log = new AuditLog();
            log.setEventType("PRINTER_COUNTER_RESET");
            log.setUsername("admin");
            log.setPrinterCode(code);
            log.setActionDetails("Counter reset to 0 (baseline offset = " + actual + " pages)");
            log.setStatus("SUCCESS");
            log.setTimestamp(Instant.now());
            auditLogRepository.save(log);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("printerCode", code);
        res.put("counterOffsetPages", actual);
        res.put("currentCounterPages", 0L);
        res.put("counterResetAt", p.getCounterResetAt().toString());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/reset-all-counters")
    public ResponseEntity<Map<String, Object>> resetAllCounters() {
        List<PrinterEntity> all = printerRepository.findAll();
        Instant now = Instant.now();
        for (PrinterEntity p : all) {
            long prevOffset = p.getCounterOffsetPages() != null ? p.getCounterOffsetPages() : 0L;
            long actual = computeActualTotalPages(p.getPrinterCode());
            p.setCounterOffsetPages(actual);
            p.setCounterResetAt(now);
            if (counterResetLogRepository != null) {
                CounterResetLogEntity rl = new CounterResetLogEntity();
                rl.setPrinterCode(p.getPrinterCode());
                rl.setPrinterName(p.getName());
                rl.setDepartment(p.getDepartment());
                rl.setPreviousOffsetPages(prevOffset);
                rl.setActualPagesAtReset(actual);
                rl.setDeltaSinceLastReset(Math.max(0L, actual - prevOffset));
                rl.setResetBy("admin");
                rl.setResetAt(now);
                counterResetLogRepository.save(rl);
            }
        }
        printerRepository.saveAll(all);

        if (auditLogRepository != null) {
            AuditLog log = new AuditLog();
            log.setEventType("ALL_PRINTER_COUNTERS_RESET");
            log.setUsername("admin");
            log.setActionDetails("All " + all.size() + " printer counters reset to 0");
            log.setStatus("SUCCESS");
            log.setTimestamp(now);
            auditLogRepository.save(log);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("resetCount", all.size());
        res.put("counterResetAt", now.toString());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/printer-profile/{printerCode}")
    public ResponseEntity<Map<String, Object>> getPrinterProfile(@PathVariable("printerCode") String printerCode) {
        String code = printerCode != null ? printerCode.trim().toUpperCase() : "";
        Optional<PrinterEntity> printerOpt = printerRepository.findByPrinterCode(code);
        if (printerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PrinterEntity p = printerOpt.get();

        List<PrinterMaintenanceEntity> history = maintenanceRepository.findAllByOrderByServiceDateDescIdDesc()
                .stream()
                .filter(m -> code.equalsIgnoreCase(m.getPrinterCode()))
                .toList();

        long repairs = 0, toners = 0, openTickets = 0, totalDuration = 0, latestOdometer = 0;
        double totalCost = 0.0;
        String lastServiceDate = history.isEmpty() ? null : history.get(0).getServiceDate();

        for (PrinterMaintenanceEntity m : history) {
            if ("TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType())) toners++;
            else if (isRepairType(m.getMaintenanceType())) repairs++;
            if (!"COMPLETED".equalsIgnoreCase(m.getStatus())) openTickets++;
            if (m.getCostLkr() != null) totalCost += m.getCostLkr();
            if (m.getDurationMinutes() != null) totalDuration += m.getDurationMinutes();
            if (m.getOdometerPages() != null && m.getOdometerPages() > latestOdometer) {
                latestOdometer = m.getOdometerPages();
            }
        }

        long printJobs = 0, printPages = 0, colorPages = 0, bwPages = 0;
        if (printJobRepository != null) {
            for (PrintJob j : printJobRepository.findAll()) {
                String jCode = j.getTargetPrinter() != null ? j.getTargetPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(jCode)) {
                    printJobs++;
                    long pg = (long) j.getPageCount() * Math.max(1, j.getCopies());
                    printPages += pg;
                    if (j.isColor()) colorPages += pg;
                    else bwPages += pg;
                }
            }
        }

        long copyJobs = 0, copyPages = 0;
        if (copyJobRepository != null) {
            for (CopyJobEntity c : copyJobRepository.findAll()) {
                String cCode = c.getPrinter() != null ? c.getPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(cCode)) {
                    copyJobs++;
                    copyPages += c.getPages();
                }
            }
        }

        long scanJobs = 0, scanPages = 0;
        if (scanJobRepository != null) {
            for (ScanJobEntity s : scanJobRepository.findAll()) {
                String sCode = s.getPrinter() != null ? s.getPrinter().getPrinterCode() : null;
                if (code.equalsIgnoreCase(sCode)) {
                    scanJobs++;
                    scanPages += s.getPages();
                }
            }
        }

        Map<String, Object> printerInfo = new LinkedHashMap<>();
        printerInfo.put("printerCode", p.getPrinterCode());
        printerInfo.put("name", p.getName());
        printerInfo.put("model", p.getModel());
        printerInfo.put("ipAddress", p.getIpAddress());
        printerInfo.put("location", p.getLocation());
        printerInfo.put("department", p.getDepartment());
        printerInfo.put("status", p.getStatus());
        printerInfo.put("colorSupported", p.isColorSupported());
        printerInfo.put("duplexSupported", p.isDuplexSupported());
        printerInfo.put("ewbConfigured", p.isEwbConfigured());

        long actualTotal = printPages + copyPages + scanPages;
        long offset = p.getCounterOffsetPages() != null ? p.getCounterOffsetPages() : 0L;
        long currentCounter = Math.max(0L, actualTotal - offset);

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("printJobs", printJobs);
        counters.put("printPages", printPages);
        counters.put("colorPages", colorPages);
        counters.put("bwPages", bwPages);
        counters.put("copyJobs", copyJobs);
        counters.put("copyPages", copyPages);
        counters.put("scanJobs", scanJobs);
        counters.put("scanPages", scanPages);
        counters.put("totalVolumePages", actualTotal);
        counters.put("actualTotalPages", actualTotal);
        counters.put("counterOffsetPages", offset);
        counters.put("counterResetAt", p.getCounterResetAt() != null ? p.getCounterResetAt().toString() : null);
        counters.put("currentCounterPages", currentCounter);
        counters.put("latestOdometerPages", latestOdometer);

        Map<String, Object> maintenanceSummary = new LinkedHashMap<>();
        maintenanceSummary.put("totalRecords", history.size());
        maintenanceSummary.put("totalRepairs", repairs);
        maintenanceSummary.put("totalTonerReplacements", toners);
        maintenanceSummary.put("openTickets", openTickets);
        maintenanceSummary.put("totalCostLkr", Math.round(totalCost * 100.0) / 100.0);
        maintenanceSummary.put("totalDurationMinutes", totalDuration);
        maintenanceSummary.put("lastServiceDate", lastServiceDate);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("printer", printerInfo);
        out.put("counters", counters);
        out.put("maintenanceSummary", maintenanceSummary);
        out.put("history", history);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getMaintenanceReports(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "printerCode", required = false) String printerCode,
            @RequestParam(value = "maintenanceType", required = false) String maintenanceType) {

        Map<String, PrinterEntity> printerMap = new LinkedHashMap<>();
        for (PrinterEntity p : printerRepository.findAll()) {
            if (p.getPrinterCode() != null) {
                printerMap.put(p.getPrinterCode().toUpperCase(), p);
            }
        }

        List<PrinterMaintenanceEntity> all = maintenanceRepository.findAllByOrderByServiceDateDescIdDesc();
        List<PrinterMaintenanceEntity> filtered = all.stream().filter(m -> {
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "";
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null && !p.getDepartment().isBlank()) ? p.getDepartment() : "Unassigned";

            if (from != null && !from.isBlank() && m.getServiceDate() != null && m.getServiceDate().compareTo(from) < 0) return false;
            if (to != null && !to.isBlank() && m.getServiceDate() != null && m.getServiceDate().compareTo(to) > 0) return false;
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) return false;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) return false;
            if (maintenanceType != null && !maintenanceType.isBlank() && !maintenanceType.equalsIgnoreCase(m.getMaintenanceType())) return false;
            return true;
        }).toList();

        long totalRepairs = 0, totalToners = 0, openTickets = 0, totalDuration = 0, repairDuration = 0;
        double totalCost = 0.0;

        Map<String, Map<String, Object>> printerAgg = new LinkedHashMap<>();
        Map<String, List<LocalDate>> printerRepairDates = new LinkedHashMap<>();
        Map<String, Map<String, Object>> deptAgg = new LinkedHashMap<>();
        Map<String, Set<String>> deptPrinters = new LinkedHashMap<>();
        Map<String, Map<String, Object>> tonerAgg = new LinkedHashMap<>();

        for (PrinterMaintenanceEntity m : filtered) {
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "UNKNOWN";
            PrinterEntity p = printerMap.get(pCode);
            String pName = p != null ? p.getName() : (m.getPrinterName() != null ? m.getPrinterName() : pCode);
            String dept = (p != null && p.getDepartment() != null && !p.getDepartment().isBlank()) ? p.getDepartment() : "Unassigned";
            String model = p != null && p.getModel() != null ? p.getModel() : "-";

            boolean isToner = "TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType());
            boolean isRepair = isRepairType(m.getMaintenanceType());
            long dur = m.getDurationMinutes() != null ? m.getDurationMinutes() : 0L;
            double cost = m.getCostLkr() != null ? m.getCostLkr() : 0.0;

            if (isToner) totalToners++;
            if (isRepair) {
                totalRepairs++;
                repairDuration += dur;
            }
            if (!"COMPLETED".equalsIgnoreCase(m.getStatus())) openTickets++;
            totalDuration += dur;
            totalCost += cost;

            Map<String, Object> pa = printerAgg.computeIfAbsent(pCode, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("printerCode", pCode);
                map.put("printerName", pName);
                map.put("department", dept);
                map.put("model", model);
                map.put("repairCount", 0L);
                map.put("tonerCount", 0L);
                map.put("otherServiceCount", 0L);
                map.put("totalRecords", 0L);
                map.put("totalDurationMinutes", 0L);
                map.put("totalCostLkr", 0.0);
                map.put("lastServiceDate", m.getServiceDate());
                return map;
            });
            if (isToner) pa.put("tonerCount", (Long) pa.get("tonerCount") + 1L);
            else if (isRepair) {
                pa.put("repairCount", (Long) pa.get("repairCount") + 1L);
                if (m.getServiceDate() != null && !m.getServiceDate().isBlank()) {
                    try {
                        printerRepairDates.computeIfAbsent(pCode, k -> new ArrayList<>()).add(LocalDate.parse(m.getServiceDate()));
                    } catch (Exception ignored) {}
                }
            } else {
                pa.put("otherServiceCount", (Long) pa.get("otherServiceCount") + 1L);
            }
            pa.put("totalRecords", (Long) pa.get("totalRecords") + 1L);
            pa.put("totalDurationMinutes", (Long) pa.get("totalDurationMinutes") + dur);
            pa.put("totalCostLkr", Math.round(((Double) pa.get("totalCostLkr") + cost) * 100.0) / 100.0);

            deptPrinters.computeIfAbsent(dept, k -> new LinkedHashSet<>()).add(pCode);
            Map<String, Object> da = deptAgg.computeIfAbsent(dept, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("department", dept);
                map.put("printerCount", 0);
                map.put("repairCount", 0L);
                map.put("tonerCount", 0L);
                map.put("totalRecords", 0L);
                map.put("totalDurationMinutes", 0L);
                map.put("totalCostLkr", 0.0);
                return map;
            });
            da.put("printerCount", deptPrinters.get(dept).size());
            if (isToner) da.put("tonerCount", (Long) da.get("tonerCount") + 1L);
            else if (isRepair) da.put("repairCount", (Long) da.get("repairCount") + 1L);
            da.put("totalRecords", (Long) da.get("totalRecords") + 1L);
            da.put("totalDurationMinutes", (Long) da.get("totalDurationMinutes") + dur);
            da.put("totalCostLkr", Math.round(((Double) da.get("totalCostLkr") + cost) * 100.0) / 100.0);

            if (isToner) {
                Map<String, Object> ta = tonerAgg.computeIfAbsent(pCode, k -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("printerCode", pCode);
                    map.put("printerName", pName);
                    map.put("department", dept);
                    map.put("blackCount", 0L);
                    map.put("cyanCount", 0L);
                    map.put("magentaCount", 0L);
                    map.put("yellowCount", 0L);
                    map.put("totalToners", 0L);
                    map.put("totalTonerCostLkr", 0.0);
                    return map;
                });
                String col = m.getTonerColor() != null ? m.getTonerColor().toUpperCase() : "BLACK";
                if (col.contains("CYAN")) ta.put("cyanCount", (Long) ta.get("cyanCount") + 1L);
                else if (col.contains("MAGENTA")) ta.put("magentaCount", (Long) ta.get("magentaCount") + 1L);
                else if (col.contains("YELLOW")) ta.put("yellowCount", (Long) ta.get("yellowCount") + 1L);
                else ta.put("blackCount", (Long) ta.get("blackCount") + 1L);
                ta.put("totalToners", (Long) ta.get("totalToners") + 1L);
                ta.put("totalTonerCostLkr", Math.round(((Double) ta.get("totalTonerCostLkr") + cost) * 100.0) / 100.0);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : printerAgg.entrySet()) {
            List<LocalDate> dates = printerRepairDates.get(entry.getKey());
            String freqLabel = "-";
            if (dates != null && dates.size() >= 2) {
                Collections.sort(dates);
                long spanDays = ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1));
                long avgDays = Math.max(1L, spanDays / (dates.size() - 1));
                freqLabel = "Every " + avgDays + "d";
            } else if (dates != null && dates.size() == 1) {
                freqLabel = "1st repair";
            }
            entry.getValue().put("repairFrequency", freqLabel);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRecords", filtered.size());
        summary.put("totalRepairs", totalRepairs);
        summary.put("totalToners", totalToners);
        summary.put("openTickets", openTickets);
        summary.put("totalCostLkr", Math.round(totalCost * 100.0) / 100.0);
        summary.put("totalDurationMinutes", totalDuration);
        summary.put("avgRepairDurationMinutes", totalRepairs > 0 ? Math.round((double) repairDuration / totalRepairs) : 0L);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("printerWise", new ArrayList<>(printerAgg.values()));
        out.put("departmentWise", new ArrayList<>(deptAgg.values()));
        out.put("tonerBreakdown", new ArrayList<>(tonerAgg.values()));
        out.put("records", filtered);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/advanced-reports")
    public ResponseEntity<Map<String, Object>> getAdvancedReports(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "printerCode", required = false) String printerCode,
            @RequestParam(value = "slaHours", defaultValue = "4") int slaHours) {

        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
        ZoneId zone = ZoneId.systemDefault();

        List<PrinterEntity> allPrinters = printerRepository.findAll();
        Map<String, PrinterEntity> printerMap = new LinkedHashMap<>();
        for (PrinterEntity p : allPrinters) {
            if (p.getPrinterCode() != null) {
                printerMap.put(p.getPrinterCode().toUpperCase(), p);
            }
        }

        // 1. Period Counter Delta Report (Print BW, Print Color, Copy, Scan + Current Counter Since Reset)
        Map<String, long[]> periodVol = new LinkedHashMap<>(); // [printBw, printColor, copy, scan]
        if (printJobRepository != null) {
            for (PrintJob j : printJobRepository.findAll()) {
                String pCode = j.getTargetPrinter() != null && j.getTargetPrinter().getPrinterCode() != null
                        ? j.getTargetPrinter().getPrinterCode().toUpperCase() : null;
                if (pCode == null) continue;
                Instant ts = j.getReleasedAt() != null ? j.getReleasedAt() : j.getSubmittedAt();
                if (ts != null && (fromDate != null || toDate != null)) {
                    LocalDate d = ts.atZone(zone).toLocalDate();
                    if (fromDate != null && d.isBefore(fromDate)) continue;
                    if (toDate != null && d.isAfter(toDate)) continue;
                }
                long pg = (long) j.getPageCount() * Math.max(1, j.getCopies());
                long[] arr = periodVol.computeIfAbsent(pCode, k -> new long[4]);
                if (j.isColor()) arr[1] += pg; else arr[0] += pg;
            }
        }
        if (copyJobRepository != null) {
            for (CopyJobEntity c : copyJobRepository.findAll()) {
                String pCode = c.getPrinter() != null && c.getPrinter().getPrinterCode() != null
                        ? c.getPrinter().getPrinterCode().toUpperCase() : null;
                if (pCode == null) continue;
                Instant ts = c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt();
                if (ts != null && (fromDate != null || toDate != null)) {
                    LocalDate d = ts.atZone(zone).toLocalDate();
                    if (fromDate != null && d.isBefore(fromDate)) continue;
                    if (toDate != null && d.isAfter(toDate)) continue;
                }
                periodVol.computeIfAbsent(pCode, k -> new long[4])[2] += c.getPages();
            }
        }
        if (scanJobRepository != null) {
            for (ScanJobEntity s : scanJobRepository.findAll()) {
                String pCode = s.getPrinter() != null && s.getPrinter().getPrinterCode() != null
                        ? s.getPrinter().getPrinterCode().toUpperCase() : null;
                if (pCode == null) continue;
                Instant ts = s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt();
                if (ts != null && (fromDate != null || toDate != null)) {
                    LocalDate d = ts.atZone(zone).toLocalDate();
                    if (fromDate != null && d.isBefore(fromDate)) continue;
                    if (toDate != null && d.isAfter(toDate)) continue;
                }
                periodVol.computeIfAbsent(pCode, k -> new long[4])[3] += s.getPages();
            }
        }

        List<Map<String, Object>> counterDeltaReport = new ArrayList<>();
        for (PrinterEntity p : allPrinters) {
            String pCode = p.getPrinterCode() != null ? p.getPrinterCode().toUpperCase() : "";
            String dept = p.getDepartment() != null ? p.getDepartment() : "Operations";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;

            long[] pv = periodVol.getOrDefault(pCode, new long[4]);
            long periodTotal = pv[0] + pv[1] + pv[2] + pv[3];
            long lifetime = computeActualTotalPages(pCode);
            long offset = p.getCounterOffsetPages() != null ? p.getCounterOffsetPages() : 0L;
            long currentSinceReset = Math.max(0L, lifetime - offset);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("printerCode", pCode);
            row.put("printerName", p.getName());
            row.put("department", dept);
            row.put("model", p.getModel());
            row.put("printBwPages", pv[0]);
            row.put("printColorPages", pv[1]);
            row.put("copyPages", pv[2]);
            row.put("scanPages", pv[3]);
            row.put("periodTotalPages", periodTotal);
            row.put("lifetimeActualPages", lifetime);
            row.put("counterOffsetPages", offset);
            row.put("currentCounterSinceReset", currentSinceReset);
            row.put("lastResetAt", p.getCounterResetAt() != null ? p.getCounterResetAt().toString() : null);
            counterDeltaReport.add(row);
        }

        // 2. Counter Reset History
        List<CounterResetLogEntity> resetHistory = counterResetLogRepository != null
                ? counterResetLogRepository.findAllByOrderByResetAtDescIdDesc().stream().filter(r -> {
                    if (department != null && !department.isBlank() && !department.equalsIgnoreCase(r.getDepartment())) return false;
                    if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(r.getPrinterCode())) return false;
                    return true;
                }).toList()
                : List.of();

        // Filter maintenance records
        List<PrinterMaintenanceEntity> allMaint = maintenanceRepository.findAllByOrderByServiceDateDescIdDesc();
        List<PrinterMaintenanceEntity> filteredMaint = allMaint.stream().filter(m -> {
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "";
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null && !p.getDepartment().isBlank()) ? p.getDepartment() : "Unassigned";
            if (from != null && !from.isBlank() && m.getServiceDate() != null && m.getServiceDate().compareTo(from) < 0) return false;
            if (to != null && !to.isBlank() && m.getServiceDate() != null && m.getServiceDate().compareTo(to) > 0) return false;
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) return false;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) return false;
            return true;
        }).toList();

        // 3. Reliability Scorecard (MTBF, MTTR, SLA Breaches, TCO, Cost-Per-Page)
        long slaMinutes = Math.max(1, slaHours) * 60L;
        Map<String, List<PrinterMaintenanceEntity>> maintByPrinter = new LinkedHashMap<>();
        for (PrinterMaintenanceEntity m : filteredMaint) {
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "UNKNOWN";
            maintByPrinter.computeIfAbsent(pCode, k -> new ArrayList<>()).add(m);
        }

        List<Map<String, Object>> reliabilityScorecard = new ArrayList<>();
        double totalFleetCpp = 0.0;
        int cppCount = 0;

        for (PrinterEntity p : allPrinters) {
            String pCode = p.getPrinterCode() != null ? p.getPrinterCode().toUpperCase() : "";
            String dept = p.getDepartment() != null ? p.getDepartment() : "Operations";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;

            List<PrinterMaintenanceEntity> list = maintByPrinter.getOrDefault(pCode, List.of());
            long repairs = 0, slaBreaches = 0, repairMins = 0, totalMins = 0;
            double tcoCost = 0.0;
            List<LocalDate> repairDates = new ArrayList<>();
            for (PrinterMaintenanceEntity m : list) {
                double c = m.getCostLkr() != null ? m.getCostLkr() : 0.0;
                long dur = m.getDurationMinutes() != null ? m.getDurationMinutes() : 0L;
                tcoCost += c;
                totalMins += dur;
                if (dur > slaMinutes) slaBreaches++;
                if (isRepairType(m.getMaintenanceType())) {
                    repairs++;
                    repairMins += dur;
                    if (m.getServiceDate() != null && !m.getServiceDate().isBlank()) {
                        try { repairDates.add(LocalDate.parse(m.getServiceDate())); } catch (Exception ignored) {}
                    }
                }
            }

            long mtbfDays = 0L;
            if (repairDates.size() >= 2) {
                Collections.sort(repairDates);
                long span = ChronoUnit.DAYS.between(repairDates.get(0), repairDates.get(repairDates.size() - 1));
                mtbfDays = Math.max(1L, span / (repairDates.size() - 1));
            }
            long mttrMins = repairs > 0 ? Math.round((double) repairMins / repairs) : 0L;
            long lifetimePages = computeActualTotalPages(pCode);
            long mtbfPages = (repairs > 0 && lifetimePages > 0) ? (lifetimePages / repairs) : lifetimePages;
            double cpp = lifetimePages > 0 ? Math.round((tcoCost / lifetimePages) * 100.0) / 100.0 : 0.0;
            if (cpp > 0) { totalFleetCpp += cpp; cppCount++; }

            // Simple 30-day uptime % estimate based on downtime minutes
            double uptimePct = Math.max(0.0, Math.round((1.0 - (totalMins / (30.0 * 24.0 * 60.0))) * 10000.0) / 100.0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("printerCode", pCode);
            row.put("printerName", p.getName());
            row.put("department", dept);
            row.put("repairCount", repairs);
            row.put("mtbfDays", mtbfDays);
            row.put("mtbfPages", mtbfPages);
            row.put("mttrMinutes", mttrMins);
            row.put("slaBreaches", slaBreaches);
            row.put("uptimePct", uptimePct);
            row.put("tcoLkr", Math.round(tcoCost * 100.0) / 100.0);
            row.put("costPerPageLkr", cpp);
            row.put("isHighDrain", repairs >= 3 || slaBreaches >= 2);
            reliabilityScorecard.add(row);
        }
        double avgFleetCpp = cppCount > 0 ? totalFleetCpp / cppCount : 0.0;
        for (Map<String, Object> r : reliabilityScorecard) {
            double cpp = (Double) r.get("costPerPageLkr");
            if (avgFleetCpp > 0 && cpp > avgFleetCpp * 1.5) {
                r.put("isHighDrain", true);
            }
        }

        // 4. Part Life & Toner Yield Report (consecutive odometer deltas)
        Map<String, List<PrinterMaintenanceEntity>> itemGroups = new LinkedHashMap<>();
        for (PrinterMaintenanceEntity m : allMaint) {
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "UNKNOWN";
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null && !p.getDepartment().isBlank()) ? p.getDepartment() : "Unassigned";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;

            String itemKey;
            if ("TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType())) {
                itemKey = pCode + "::TONER (" + (m.getTonerColor() != null ? m.getTonerColor() : "BLACK") + ")";
            } else if (m.getPartReplaced() != null && !m.getPartReplaced().isBlank()) {
                itemKey = pCode + "::PART (" + m.getPartReplaced().trim() + ")";
            } else {
                continue;
            }
            itemGroups.computeIfAbsent(itemKey, k -> new ArrayList<>()).add(m);
        }

        List<Map<String, Object>> partLifeReport = new ArrayList<>();
        for (Map.Entry<String, List<PrinterMaintenanceEntity>> en : itemGroups.entrySet()) {
            List<PrinterMaintenanceEntity> items = en.getValue();
            items.sort(Comparator.comparingLong(m -> m.getOdometerPages() != null ? m.getOdometerPages() : 0L));
            String[] parts = en.getKey().split("::", 2);
            String pCode = parts[0];
            String itemName = parts[1];
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null) ? p.getDepartment() : "Unassigned";

            long totalCost = 0;
            long lastOdo = 0;
            List<Long> deltas = new ArrayList<>();
            long prevOdo = -1;
            for (PrinterMaintenanceEntity m : items) {
                totalCost += Math.round(m.getCostLkr() != null ? m.getCostLkr() : 0.0);
                long odo = m.getOdometerPages() != null ? m.getOdometerPages() : 0L;
                if (odo > lastOdo) lastOdo = odo;
                if (odo > 0 && prevOdo > 0 && odo > prevOdo) {
                    deltas.add(odo - prevOdo);
                }
                if (odo > 0) prevOdo = odo;
            }
            long avgYield = deltas.isEmpty() ? 0L : Math.round(deltas.stream().mapToLong(Long::longValue).average().orElse(0));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("printerCode", pCode);
            row.put("department", dept);
            row.put("itemType", itemName);
            row.put("replacements", items.size());
            row.put("avgPagesPerReplacement", avgYield);
            row.put("lastOdometerPages", lastOdo);
            row.put("totalCostLkr", totalCost);
            partLifeReport.add(row);
        }

        // 5. Technician Performance Scorecard
        Map<String, Map<String, Object>> techMap = new LinkedHashMap<>();
        for (PrinterMaintenanceEntity m : filteredMaint) {
            String tech = (m.getTechnician() != null && !m.getTechnician().isBlank()) ? m.getTechnician().trim() : "Unassigned";
            Map<String, Object> tm = techMap.computeIfAbsent(tech, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("technician", tech);
                map.put("totalTickets", 0L);
                map.put("completedTickets", 0L);
                map.put("openTickets", 0L);
                map.put("totalDurationMinutes", 0L);
                map.put("totalCostLkr", 0.0);
                return map;
            });
            tm.put("totalTickets", (Long) tm.get("totalTickets") + 1L);
            if ("COMPLETED".equalsIgnoreCase(m.getStatus())) tm.put("completedTickets", (Long) tm.get("completedTickets") + 1L);
            else tm.put("openTickets", (Long) tm.get("openTickets") + 1L);
            tm.put("totalDurationMinutes", (Long) tm.get("totalDurationMinutes") + (m.getDurationMinutes() != null ? m.getDurationMinutes() : 0L));
            tm.put("totalCostLkr", Math.round(((Double) tm.get("totalCostLkr") + (m.getCostLkr() != null ? m.getCostLkr() : 0.0)) * 100.0) / 100.0);
        }
        List<Map<String, Object>> technicianScorecard = new ArrayList<>(techMap.values());
        for (Map<String, Object> tm : technicianScorecard) {
            long tot = (Long) tm.get("totalTickets");
            long mins = (Long) tm.get("totalDurationMinutes");
            tm.put("avgDurationMinutes", tot > 0 ? Math.round((double) mins / tot) : 0L);
        }

        // 6. Preventive Maintenance (PM) Schedule & Overdue Alerts
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> pmScheduleReport = new ArrayList<>();
        for (PrinterMaintenanceEntity m : allMaint) {
            if (m.getNextServiceDate() == null || m.getNextServiceDate().isBlank()) continue;
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "";
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null) ? p.getDepartment() : "Unassigned";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;
            try {
                LocalDate nextD = LocalDate.parse(m.getNextServiceDate());
                long daysRem = ChronoUnit.DAYS.between(today, nextD);
                String pmStatus = daysRem < 0 ? "OVERDUE" : (daysRem <= 14 ? "DUE_SOON" : "SCHEDULED");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("printerCode", pCode);
                row.put("printerName", p != null ? p.getName() : m.getPrinterName());
                row.put("department", dept);
                row.put("maintenanceType", m.getMaintenanceType());
                row.put("lastServiceDate", m.getServiceDate());
                row.put("nextServiceDate", m.getNextServiceDate());
                row.put("daysRemaining", daysRem);
                row.put("pmStatus", pmStatus);
                pmScheduleReport.add(row);
            } catch (Exception ignored) {}
        }

        // 7. Phase 3: Toner Burn-Rate & Next Replacement Forecast
        Map<String, List<PrinterMaintenanceEntity>> tonerGroups = new LinkedHashMap<>();
        for (PrinterMaintenanceEntity m : allMaint) {
            if (!"TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType())) continue;
            String pCode = m.getPrinterCode() != null ? m.getPrinterCode().toUpperCase() : "UNKNOWN";
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null && !p.getDepartment().isBlank()) ? p.getDepartment() : "Unassigned";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;
            String color = (m.getTonerColor() != null && !m.getTonerColor().isBlank()) ? m.getTonerColor().toUpperCase() : "BLACK";
            tonerGroups.computeIfAbsent(pCode + "::" + color, k -> new ArrayList<>()).add(m);
        }

        List<Map<String, Object>> tonerBurnRateForecast = new ArrayList<>();
        for (Map.Entry<String, List<PrinterMaintenanceEntity>> en : tonerGroups.entrySet()) {
            String[] parts = en.getKey().split("::", 2);
            String pCode = parts[0];
            String color = parts[1];
            PrinterEntity p = printerMap.get(pCode);
            String dept = (p != null && p.getDepartment() != null) ? p.getDepartment() : "Unassigned";
            List<PrinterMaintenanceEntity> list = en.getValue();

            // Sort oldest to newest by serviceDate
            list.sort(Comparator.comparing(m -> m.getServiceDate() != null ? m.getServiceDate() : ""));
            List<LocalDate> dates = new ArrayList<>();
            List<Long> odoDeltas = new ArrayList<>();
            long prevOdo = -1;
            double totalTonerCost = 0.0;
            for (PrinterMaintenanceEntity m : list) {
                totalTonerCost += (m.getCostLkr() != null ? m.getCostLkr() : 0.0);
                if (m.getServiceDate() != null && !m.getServiceDate().isBlank()) {
                    try { dates.add(LocalDate.parse(m.getServiceDate())); } catch (Exception ignored) {}
                }
                long odo = m.getOdometerPages() != null ? m.getOdometerPages() : 0L;
                if (odo > 0 && prevOdo > 0 && odo > prevOdo) {
                    odoDeltas.add(odo - prevOdo);
                }
                if (odo > 0) prevOdo = odo;
            }

            long avgDays = 0L;
            if (dates.size() >= 2) {
                long span = ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1));
                avgDays = Math.max(1L, span / (dates.size() - 1));
            }
            long avgPages = odoDeltas.isEmpty() ? 0L : Math.round(odoDeltas.stream().mapToLong(Long::longValue).average().orElse(0));
            long ratedYield = "BLACK".equalsIgnoreCase(color) ? 25000L : 18000L;
            double efficiencyPct = (avgPages > 0) ? Math.round(((double) avgPages / ratedYield) * 1000.0) / 10.0 : 0.0;

            String lastDateStr = dates.isEmpty() ? "-" : dates.get(dates.size() - 1).toString();
            String predictedNextStr = "-";
            Long daysUntilEmpty = null;
            if (!dates.isEmpty() && avgDays > 0) {
                LocalDate pred = dates.get(dates.size() - 1).plusDays(avgDays);
                predictedNextStr = pred.toString();
                daysUntilEmpty = ChronoUnit.DAYS.between(today, pred);
            }

            String alert = "NORMAL";
            if (efficiencyPct > 0 && efficiencyPct < 70.0) alert = "LOW_YIELD";
            else if (daysUntilEmpty != null && daysUntilEmpty <= 14) alert = "DUE_SOON";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("printerCode", pCode);
            row.put("department", dept);
            row.put("tonerColor", color);
            row.put("replacementsCount", list.size());
            row.put("avgDaysBetween", avgDays);
            row.put("avgPagesPerCartridge", avgPages);
            row.put("ratedYieldPages", ratedYield);
            row.put("yieldEfficiencyPct", efficiencyPct);
            row.put("lastReplacedDate", lastDateStr);
            row.put("predictedNextDate", predictedNextStr);
            row.put("daysUntilEmpty", daysUntilEmpty);
            row.put("totalTonerCostLkr", Math.round(totalTonerCost * 100.0) / 100.0);
            row.put("yieldAlert", alert);
            tonerBurnRateForecast.add(row);
        }

        // 8. Phase 4: Department TCO Rollup & Replace-vs-Repair Executive Matrix
        Map<String, Map<String, Object>> deptTcoMap = new LinkedHashMap<>();
        List<Map<String, Object>> replaceVsRepairMatrix = new ArrayList<>();

        for (PrinterEntity p : allPrinters) {
            String pCode = p.getPrinterCode() != null ? p.getPrinterCode().toUpperCase() : "";
            String dept = p.getDepartment() != null ? p.getDepartment() : "Operations";
            if (department != null && !department.isBlank() && !department.equalsIgnoreCase(dept)) continue;
            if (printerCode != null && !printerCode.isBlank() && !printerCode.equalsIgnoreCase(pCode)) continue;

            List<PrinterMaintenanceEntity> list = maintByPrinter.getOrDefault(pCode, List.of());
            double repairCost = 0.0, tonerCost = 0.0, otherCost = 0.0;
            long repairs = 0, slaBreaches = 0, totalMins = 0;
            for (PrinterMaintenanceEntity m : list) {
                double c = m.getCostLkr() != null ? m.getCostLkr() : 0.0;
                long dur = m.getDurationMinutes() != null ? m.getDurationMinutes() : 0L;
                totalMins += dur;
                if (dur > slaMinutes) slaBreaches++;
                if ("TONER_REPLACEMENT".equalsIgnoreCase(m.getMaintenanceType())) tonerCost += c;
                else if (isRepairType(m.getMaintenanceType())) { repairs++; repairCost += c; }
                else otherCost += c;
            }

            long lifetimePages = computeActualTotalPages(pCode);
            double totalTco = repairCost + tonerCost + otherCost;
            double cpp = lifetimePages > 0 ? Math.round((totalTco / lifetimePages) * 100.0) / 100.0 : 0.0;
            double uptimePct = Math.max(0.0, Math.round((1.0 - (totalMins / (30.0 * 24.0 * 60.0))) * 10000.0) / 100.0);

            int healthScore = 100;
            healthScore -= (int) Math.min(50, repairs * 12);
            healthScore -= (int) Math.min(25, slaBreaches * 10);
            if (avgFleetCpp > 0 && cpp > avgFleetCpp * 1.5) healthScore -= 20;
            if (uptimePct < 98.0) healthScore -= 10;
            healthScore = Math.max(0, healthScore);

            String recommendation = healthScore < 50 ? "REPLACE_RECOMMENDED"
                    : (healthScore < 75 ? "MONITOR_CLOSELY" : "KEEP_HEALTHY");

            Map<String, Object> matRow = new LinkedHashMap<>();
            matRow.put("printerCode", pCode);
            matRow.put("printerName", p.getName());
            matRow.put("department", dept);
            matRow.put("model", p.getModel());
            matRow.put("lifetimePages", lifetimePages);
            matRow.put("repairCount", repairs);
            matRow.put("repairCostLkr", Math.round(repairCost * 100.0) / 100.0);
            matRow.put("tonerCostLkr", Math.round(tonerCost * 100.0) / 100.0);
            matRow.put("totalTcoLkr", Math.round(totalTco * 100.0) / 100.0);
            matRow.put("costPerPageLkr", cpp);
            matRow.put("healthScore", healthScore);
            matRow.put("recommendation", recommendation);
            replaceVsRepairMatrix.add(matRow);

            Map<String, Object> dt = deptTcoMap.computeIfAbsent(dept, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("department", dept);
                map.put("printerCount", 0L);
                map.put("lifetimePages", 0L);
                map.put("repairCostLkr", 0.0);
                map.put("tonerCostLkr", 0.0);
                map.put("totalTcoLkr", 0.0);
                map.put("highDrainCount", 0L);
                return map;
            });
            dt.put("printerCount", (Long) dt.get("printerCount") + 1L);
            dt.put("lifetimePages", (Long) dt.get("lifetimePages") + lifetimePages);
            dt.put("repairCostLkr", Math.round(((Double) dt.get("repairCostLkr") + repairCost) * 100.0) / 100.0);
            dt.put("tonerCostLkr", Math.round(((Double) dt.get("tonerCostLkr") + tonerCost) * 100.0) / 100.0);
            dt.put("totalTcoLkr", Math.round(((Double) dt.get("totalTcoLkr") + totalTco) * 100.0) / 100.0);
            if ("REPLACE_RECOMMENDED".equals(recommendation)) {
                dt.put("highDrainCount", (Long) dt.get("highDrainCount") + 1L);
            }
        }

        List<Map<String, Object>> departmentTcoReport = new ArrayList<>(deptTcoMap.values());
        for (Map<String, Object> dt : departmentTcoReport) {
            long pgs = (Long) dt.get("lifetimePages");
            double tco = (Double) dt.get("totalTcoLkr");
            dt.put("costPerPageLkr", pgs > 0 ? Math.round((tco / pgs) * 100.0) / 100.0 : 0.0);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counterDeltaReport", counterDeltaReport);
        out.put("counterResetHistory", resetHistory);
        out.put("reliabilityScorecard", reliabilityScorecard);
        out.put("partLifeReport", partLifeReport);
        out.put("technicianScorecard", technicianScorecard);
        out.put("pmScheduleReport", pmScheduleReport);
        out.put("tonerBurnRateForecast", tonerBurnRateForecast);
        out.put("departmentTcoReport", departmentTcoReport);
        out.put("replaceVsRepairMatrix", replaceVsRepairMatrix);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/export-executive-excel")
    public ResponseEntity<byte[]> exportExecutiveExcel(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "printerCode", required = false) String printerCode) throws Exception {
        byte[] bytes = buildExecutiveWorkbookBytes(from, to, department, printerCode);
        String filename = "nexPrint_executive_maintenance_tco_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/email-executive-report")
    public ResponseEntity<Map<String, Object>> emailExecutiveReport(@RequestBody Map<String, String> body) {
        String email = body != null ? body.getOrDefault("email", "").trim() : "";
        if (email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Recipient email is required."));
        }
        if (smtpEmailService == null || systemSettingsService == null) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "SMTP service unavailable."));
        }
        try {
            byte[] bytes = buildExecutiveWorkbookBytes(
                    body.get("from"), body.get("to"), body.get("department"), body.get("printerCode"));
            String fname = "nexPrint_executive_maintenance_tco_" + LocalDate.now() + ".xlsx";
            SmtpEmailService.EmailResult res = smtpEmailService.sendScanEmail(
                    email,
                    "nexPrint Executive Maintenance, Counter & TCO Report (" + LocalDate.now() + ")",
                    fname,
                    bytes,
                    "admin",
                    "Fleet Analytics Hub",
                    systemSettingsService.getOrCreateSettings());
            return ResponseEntity.ok(Map.of("success", res.success(), "message", res.message()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] buildExecutiveWorkbookBytes(String from, String to, String department, String printerCode) throws Exception {
        Map<String, Object> data = getAdvancedReports(from, to, department, printerCode, 4).getBody();
        if (data == null) data = Map.of();

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle hdrStyle = wb.createCellStyle();
            Font hdrFont = wb.createFont();
            hdrFont.setBold(true);
            hdrFont.setColor(IndexedColors.WHITE.getIndex());
            hdrStyle.setFont(hdrFont);
            hdrStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hdrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            writeSheet(wb, hdrStyle, "Counter Delta",
                    List.of("Printer", "Name", "Department", "Print B&W", "Print Color", "Copy", "Scan", "Period Total", "Current (Since Reset)", "Reset Offset", "Lifetime"),
                    (List<Map<String, Object>>) data.getOrDefault("counterDeltaReport", List.of()),
                    List.of("printerCode", "printerName", "department", "printBwPages", "printColorPages", "copyPages", "scanPages", "periodTotalPages", "currentCounterSinceReset", "counterOffsetPages", "lifetimeActualPages"));

            writeSheet(wb, hdrStyle, "Reliability & MTBF",
                    List.of("Printer", "Department", "Repairs", "MTBF (Days)", "MTBF (Pages)", "MTTR (min)", "SLA Breaches", "Uptime %", "TCO (LKR)", "CPP (LKR)", "High Drain"),
                    (List<Map<String, Object>>) data.getOrDefault("reliabilityScorecard", List.of()),
                    List.of("printerCode", "department", "repairCount", "mtbfDays", "mtbfPages", "mttrMinutes", "slaBreaches", "uptimePct", "tcoLkr", "costPerPageLkr", "isHighDrain"));

            writeSheet(wb, hdrStyle, "Toner Burn Forecast",
                    List.of("Printer", "Department", "Color", "Count", "Avg Days", "Actual Yield", "Rated Yield", "Efficiency %", "Last Replaced", "Predicted Next", "Alert"),
                    (List<Map<String, Object>>) data.getOrDefault("tonerBurnRateForecast", List.of()),
                    List.of("printerCode", "department", "tonerColor", "replacementsCount", "avgDaysBetween", "avgPagesPerCartridge", "ratedYieldPages", "yieldEfficiencyPct", "lastReplacedDate", "predictedNextDate", "yieldAlert"));

            writeSheet(wb, hdrStyle, "Department TCO",
                    List.of("Department", "Printers", "Lifetime Pages", "Repair Cost (LKR)", "Toner Cost (LKR)", "Total TCO (LKR)", "CPP (LKR)", "High Drain Units"),
                    (List<Map<String, Object>>) data.getOrDefault("departmentTcoReport", List.of()),
                    List.of("department", "printerCount", "lifetimePages", "repairCostLkr", "tonerCostLkr", "totalTcoLkr", "costPerPageLkr", "highDrainCount"));

            writeSheet(wb, hdrStyle, "Replace vs Repair",
                    List.of("Printer", "Department", "Model", "Lifetime Pages", "Repairs", "Repair Cost", "Toner Cost", "Total TCO", "CPP", "Health Score", "Recommendation"),
                    (List<Map<String, Object>>) data.getOrDefault("replaceVsRepairMatrix", List.of()),
                    List.of("printerCode", "department", "model", "lifetimePages", "repairCount", "repairCostLkr", "tonerCostLkr", "totalTcoLkr", "costPerPageLkr", "healthScore", "recommendation"));

            writeSheet(wb, hdrStyle, "PM Schedule",
                    List.of("Printer", "Department", "Service Type", "Last Service", "Next PM Date", "Days Remaining", "Status"),
                    (List<Map<String, Object>>) data.getOrDefault("pmScheduleReport", List.of()),
                    List.of("printerCode", "department", "maintenanceType", "lastServiceDate", "nextServiceDate", "daysRemaining", "pmStatus"));

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(XSSFWorkbook wb, CellStyle hdrStyle, String sheetName,
                            List<String> headers, List<Map<String, Object>> rows, List<String> keys) {
        Sheet sheet = wb.createSheet(sheetName);
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(hdrStyle);
        }
        int rIdx = 1;
        for (Map<String, Object> rowMap : rows) {
            Row r = sheet.createRow(rIdx++);
            for (int cIdx = 0; cIdx < keys.size(); cIdx++) {
                Object val = rowMap.get(keys.get(cIdx));
                Cell cell = r.createCell(cIdx);
                if (val instanceof Number num) cell.setCellValue(num.doubleValue());
                else cell.setCellValue(val != null ? String.valueOf(val) : "");
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
