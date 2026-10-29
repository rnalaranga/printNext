package com.rr.secureprint.service;

import com.rr.secureprint.dto.FleetMonitoringSummaryDto;
import com.rr.secureprint.dto.PrinterMonitoringDto;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.repository.PrinterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SnmpMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(SnmpMonitoringService.class);

    private final PrinterRepository printerRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(16);

    private volatile FleetMonitoringSummaryDto cachedFleetData = null;
    private volatile long lastPollTime = 0L;
    private static final long CACHE_TTL_MS = 15_000L; // 15 seconds cache

    public SnmpMonitoringService(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    public FleetMonitoringSummaryDto getFleetMonitoring(boolean forceRefresh, int targetFleetSize) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedFleetData != null && (now - lastPollTime) < CACHE_TTL_MS) {
            return cachedFleetData;
        }

        synchronized (this) {
            if (!forceRefresh && cachedFleetData != null && (System.currentTimeMillis() - lastPollTime) < CACHE_TTL_MS) {
                return cachedFleetData;
            }

            FleetMonitoringSummaryDto summary = pollFleet();
            cachedFleetData = summary;
            lastPollTime = System.currentTimeMillis();
            return summary;
        }
    }

    public PrinterMonitoringDto getSinglePrinterMonitoring(Long id) {
        Optional<PrinterEntity> opt = printerRepository.findById(id);
        if (opt.isEmpty()) {
            return null;
        }
        return pollPhysicalPrinter(opt.get());
    }

    private FleetMonitoringSummaryDto pollFleet() {
        List<PrinterEntity> physicalPrinters = printerRepository.findAll();
        List<PrinterMonitoringDto> dtoList = new CopyOnWriteArrayList<>();

        // Poll physical printers in parallel via SNMP
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PrinterEntity entity : physicalPrinters) {
            futures.add(CompletableFuture.runAsync(() -> {
                PrinterMonitoringDto dto = pollPhysicalPrinter(entity);
                dtoList.add(dto);
            }, executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(4, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("SNMP fleet polling timed out or interrupted: {}", ex.getMessage());
        }

        // Sort physical printers by id
        dtoList.sort(Comparator.comparing(PrinterMonitoringDto::getId));

        // Compute KPI HUD aggregations
        FleetMonitoringSummaryDto summary = new FleetMonitoringSummaryDto();
        summary.setTotalPrinters(dtoList.size());
        summary.setLastUpdated(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        int online = 0;
        int printing = 0;
        int warning = 0;
        int error = 0;
        int offline = 0;
        long totalLifePrints = 0L;
        double sumToner = 0.0;
        int tonerCount = 0;

        for (PrinterMonitoringDto dto : dtoList) {
            String stat = dto.getStatus();
            if ("PRINTING".equalsIgnoreCase(stat)) {
                printing++;
                online++;
            } else if ("WARNING".equalsIgnoreCase(stat)) {
                warning++;
                online++;
            } else if ("ERROR".equalsIgnoreCase(stat)) {
                error++;
            } else if ("OFFLINE".equalsIgnoreCase(stat)) {
                offline++;
            } else {
                online++;
            }

            if (dto.getLifeCount() != null) {
                totalLifePrints += dto.getLifeCount();
            }

            if (dto.getToners() != null && !dto.getToners().isEmpty()) {
                for (PrinterMonitoringDto.TonerItem t : dto.getToners()) {
                    if (t.getLevelPercent() >= 0) {
                        sumToner += t.getLevelPercent();
                        tonerCount++;
                    }
                }
            }
        }

        summary.setOnlinePrinters(online);
        summary.setPrintingPrinters(printing);
        summary.setWarningPrinters(warning);
        summary.setErrorPrinters(error);
        summary.setOfflinePrinters(offline);
        summary.setTotalFleetPrints(totalLifePrints);
        summary.setAverageTonerPercent(tonerCount > 0 ? Math.round((sumToner / tonerCount) * 10.0) / 10.0 : 75.0);
        summary.setPrinters(dtoList);

        return summary;
    }

    public PrinterMonitoringDto pollPhysicalPrinter(PrinterEntity entity) {
        long start = System.currentTimeMillis();
        PrinterMonitoringDto dto = new PrinterMonitoringDto();
        dto.setId(entity.getId());
        dto.setPrinterCode(entity.getPrinterCode());
        dto.setName(entity.getName());
        dto.setIpAddress(entity.getIpAddress());
        dto.setLocation(entity.getLocation() != null ? entity.getLocation() : "Office");
        dto.setFloor(inferFloor(entity.getLocation()));
        dto.setDepartment(inferDepartment(entity.getLocation(), entity.getName()));
        dto.setModel(entity.getModel() != null ? entity.getModel() : "Toshiba e-STUDIO");
        dto.setRealHardware(true);
        dto.setColor(entity.isColorSupported());

        String ip = entity.getIpAddress();
        String comm = "public";

        try {
            // 1. SysDescr
            SnmpClient.SnmpItem sysDescr = SnmpClient.query(ip, comm, "1.3.6.1.2.1.1.1.0", false, 1200);
            if (sysDescr == null || sysDescr.getValue() == null || sysDescr.getValue().isEmpty()) {
                dto.setStatus("OFFLINE");
                dto.setStatusMessage("Device Unreachable / Network Offline");
                dto.setHrPrinterStatusText("OFFLINE");
                dto.setUptime("0s");
                dto.setPingMs(System.currentTimeMillis() - start);
                dto.setLastPolled(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                return dto;
            }

            if (sysDescr.getValue().length() > 5) {
                dto.setModel(sysDescr.getValue().replace("\r", " ").replace("\n", " ").trim());
            }

            // 2. SysUpTime
            SnmpClient.SnmpItem sysUptime = SnmpClient.query(ip, comm, "1.3.6.1.2.1.1.3.0", false, 800);
            if (sysUptime != null && sysUptime.getValue() != null) {
                dto.setUptime(sysUptime.getValue());
            }

            // 3. hrDeviceStatus: 1=unknown, 2=running, 3=warning, 5=down
            SnmpClient.SnmpItem devStat = SnmpClient.query(ip, comm, "1.3.6.1.2.1.25.3.2.1.5.1", false, 800);
            int hrDevStat = 2;
            if (devStat != null && devStat.getValue() != null && devStat.getValue().matches("\\d+")) {
                hrDevStat = Integer.parseInt(devStat.getValue());
            }

            // 4. hrPrinterStatus: 1=other, 2=unknown, 3=idle, 4=printing, 5=warmup
            SnmpClient.SnmpItem prnStat = SnmpClient.query(ip, comm, "1.3.6.1.2.1.25.3.5.1.1.1", false, 800);
            int hrPrnStat = 3;
            if (prnStat != null && prnStat.getValue() != null && prnStat.getValue().matches("\\d+")) {
                hrPrnStat = Integer.parseInt(prnStat.getValue());
            }

            // Is currently printing?
            boolean isPrinting = (hrPrnStat == 4);
            dto.setCurrentlyPrinting(isPrinting);
            if (isPrinting) {
                dto.setHrPrinterStatusText("PRINTING");
            } else if (hrPrnStat == 5) {
                dto.setHrPrinterStatusText("WARMUP");
            } else if (hrPrnStat == 3) {
                dto.setHrPrinterStatusText("IDLE");
            } else {
                dto.setHrPrinterStatusText("READY");
            }

            // 5. Alerts (Full RFC 3805 prtAlertTable with Error Codes)
            List<SnmpClient.SnmpItem> alerts = SnmpClient.walk(ip, comm, "1.3.6.1.2.1.43.18.1.1", 30, 1000);
            parseAlertDetails(alerts, dto);

            // 6. hrPrinterDetectedErrorState: bitfield error states
            SnmpClient.SnmpItem errBitsItem = SnmpClient.query(ip, comm, "1.3.6.1.2.1.25.3.5.1.2.1", false, 800);
            parseDetectedErrors(errBitsItem, dto);

            // 7. Life Count: prtMarkerLifeCount
            SnmpClient.SnmpItem lifeItem = SnmpClient.query(ip, comm, "1.3.6.1.2.1.43.10.2.1.4.1.1", false, 800);
            if (lifeItem != null && lifeItem.getValue() != null && lifeItem.getValue().matches("\\d+")) {
                dto.setLifeCount(Long.parseLong(lifeItem.getValue()));
            }

            // 8. Supplies (Toner Levels & Waste Toner Receptacle)
            List<SnmpClient.SnmpItem> supplies = SnmpClient.walk(ip, comm, "1.3.6.1.2.1.43.11.1.1", 20, 1000);
            parseSupplies(supplies, dto);

            // 9. Trays (Input Paper Drawers)
            List<SnmpClient.SnmpItem> trays = SnmpClient.walk(ip, comm, "1.3.6.1.2.1.43.8.2.1", 20, 1000);
            parseTrays(trays, dto);

            // Determine Overall Operational Status
            if (isPrinting) {
                dto.setStatus("PRINTING");
                dto.setStatusMessage("Currently Printing • Job in Progress");
            } else if (hasCriticalAlert(dto) || hrDevStat == 5) {
                dto.setStatus("ERROR");
                if (!dto.getAlertDetails().isEmpty()) {
                    PrinterMonitoringDto.AlertDetailItem first = dto.getAlertDetails().get(0);
                    dto.setStatusMessage(first.getDescription() + " (Code #" + first.getCode() + ")");
                } else if (!dto.getDetectedErrors().isEmpty()) {
                    dto.setStatusMessage(dto.getDetectedErrors().get(0));
                } else {
                    dto.setStatusMessage("Hardware Error / Device Down");
                }
            } else if (!dto.getAlertDetails().isEmpty() || hrDevStat == 3 || isAnyTonerLow(dto.getToners()) || !dto.getDetectedErrors().isEmpty()) {
                dto.setStatus("WARNING");
                if (!dto.getAlertDetails().isEmpty()) {
                    PrinterMonitoringDto.AlertDetailItem first = dto.getAlertDetails().get(0);
                    dto.setStatusMessage(first.getDescription() + " (Code #" + first.getCode() + ")");
                } else if (!dto.getDetectedErrors().isEmpty()) {
                    dto.setStatusMessage(dto.getDetectedErrors().get(0));
                } else if (isAnyTonerLow(dto.getToners())) {
                    dto.setStatusMessage("Toner Cartridge Supply Low (< 15%)");
                } else {
                    dto.setStatusMessage("Warning Condition Detected");
                }
            } else {
                dto.setStatus("READY");
                dto.setStatusMessage("Idle / Ready for Secure Print");
            }

        } catch (Exception ex) {
            log.error("Error polling printer {}: {}", ip, ex.getMessage());
            dto.setStatus("WARNING");
            dto.setStatusMessage("SNMP Poll Timeout: " + ex.getMessage());
        }

        dto.setPingMs(System.currentTimeMillis() - start);
        dto.setLastPolled(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return dto;
    }

    private void parseDetectedErrors(SnmpClient.SnmpItem item, PrinterMonitoringDto dto) {
        List<String> list = new ArrayList<>();
        if (item != null && item.getValue() != null && !item.getValue().isEmpty()) {
            String val = item.getValue();
            if (!val.startsWith("SNMP_ERR_") && !val.equals("NULL") && !val.contains("\uFFFD")) {
                int b0 = val.charAt(0) & 0xFF;
                if ((b0 & 0x80) != 0) list.add("Low Paper Warning");
                if ((b0 & 0x40) != 0) list.add("Paper Empty / Cassette Out");
                if ((b0 & 0x20) != 0) list.add("Toner Supply Low");
                if ((b0 & 0x10) != 0) list.add("Toner Empty");
                if ((b0 & 0x08) != 0) list.add("Door / Cover Open");
                if ((b0 & 0x04) != 0) list.add("Paper Jam in Engine Transport");
                if ((b0 & 0x02) != 0) list.add("Printer Offline");
                if ((b0 & 0x01) != 0) list.add("Service Call Required");
            }
        }

        // Also add concise flags from active RFC alert details if any
        if (dto.getAlertDetails() != null) {
            for (PrinterMonitoringDto.AlertDetailItem alert : dto.getAlertDetails()) {
                String desc = alert.getDescription();
                if (desc != null && !list.contains(desc)) {
                    list.add(desc + (alert.getLocation() != null ? " (" + alert.getLocation() + ")" : ""));
                }
            }
        }

        dto.setDetectedErrors(list);
    }

    private void parseSupplies(List<SnmpClient.SnmpItem> items, PrinterMonitoringDto dto) {
        Map<String, String> descMap = new HashMap<>();
        Map<String, Integer> maxMap = new HashMap<>();
        Map<String, Integer> curMap = new HashMap<>();

        for (SnmpClient.SnmpItem item : items) {
            String oid = item.getOid();
            if (oid.startsWith("1.3.6.1.2.1.43.11.1.1.6.1.")) { // Description
                String sub = oid.substring("1.3.6.1.2.1.43.11.1.1.6.1.".length());
                descMap.put(sub, item.getValue());
            } else if (oid.startsWith("1.3.6.1.2.1.43.11.1.1.8.1.")) { // Max Capacity
                String sub = oid.substring("1.3.6.1.2.1.43.11.1.1.8.1.".length());
                if (item.getValue().matches("-?\\d+")) {
                    maxMap.put(sub, Integer.parseInt(item.getValue()));
                }
            } else if (oid.startsWith("1.3.6.1.2.1.43.11.1.1.9.1.")) { // Current Level
                String sub = oid.substring("1.3.6.1.2.1.43.11.1.1.9.1.".length());
                if (item.getValue().matches("-?\\d+")) {
                    curMap.put(sub, Integer.parseInt(item.getValue()));
                }
            }
        }

        List<PrinterMonitoringDto.TonerItem> toners = new ArrayList<>();
        dto.setWasteTonerStatus("OK");

        for (String key : curMap.keySet()) {
            String desc = descMap.getOrDefault(key, "Toner " + key);
            int cur = curMap.get(key);
            int max = maxMap.getOrDefault(key, 100);

            // Waste Toner Receptacle
            if (desc.toLowerCase().contains("waste")) {
                if (cur == 0 || cur >= 95) {
                    dto.setWasteTonerStatus("FULL");
                } else if (cur >= 80) {
                    dto.setWasteTonerStatus("NEAR_FULL");
                } else {
                    dto.setWasteTonerStatus("OK");
                }
                continue; // Do not display waste toner as a print cartridge
            }

            // In Toshiba e-STUDIO, current is percentage (max is 100 or -2)
            int pct = cur;
            if (max > 0 && max != 100 && cur >= 0) {
                pct = (int) Math.round(((double) cur / max) * 100.0);
            }
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;

            String colorName = "Black";
            String hex = "#1e293b";
            String lower = desc.toLowerCase();
            if (lower.contains("cyan")) {
                colorName = "Cyan";
                hex = "#06b6d4";
            } else if (lower.contains("magenta")) {
                colorName = "Magenta";
                hex = "#ec4899";
            } else if (lower.contains("yellow")) {
                colorName = "Yellow";
                hex = "#eab308";
            }

            String stat = pct <= 5 ? "EMPTY" : (pct <= 15 ? "LOW" : "OK");
            toners.add(new PrinterMonitoringDto.TonerItem(colorName, hex, pct, stat));
        }

        if (toners.isEmpty()) {
            toners.add(new PrinterMonitoringDto.TonerItem("Black", "#1e293b", 80, "OK"));
        }

        dto.setToners(toners);
    }

    private void parseTrays(List<SnmpClient.SnmpItem> items, PrinterMonitoringDto dto) {
        Map<String, String> nameMap = new HashMap<>();
        Map<String, Integer> maxMap = new HashMap<>();
        Map<String, Integer> curMap = new HashMap<>();

        for (SnmpClient.SnmpItem item : items) {
            String oid = item.getOid();
            if (oid.startsWith("1.3.6.1.2.1.43.8.2.1.18.1.")) { // Description / Name
                String sub = oid.substring("1.3.6.1.2.1.43.8.2.1.18.1.".length());
                nameMap.put(sub, item.getValue());
            } else if (oid.startsWith("1.3.6.1.2.1.43.8.2.1.9.1.")) { // Max Capacity
                String sub = oid.substring("1.3.6.1.2.1.43.8.2.1.9.1.".length());
                if (item.getValue().matches("-?\\d+")) maxMap.put(sub, Integer.parseInt(item.getValue()));
            } else if (oid.startsWith("1.3.6.1.2.1.43.8.2.1.10.1.")) { // Current Level
                String sub = oid.substring("1.3.6.1.2.1.43.8.2.1.10.1.".length());
                if (item.getValue().matches("-?\\d+")) curMap.put(sub, Integer.parseInt(item.getValue()));
            }
        }

        List<PrinterMonitoringDto.TrayItem> trays = new ArrayList<>();
        int idx = 1;
        for (String key : curMap.keySet()) {
            String name = nameMap.getOrDefault(key, "Drawer " + idx);
            int cur = curMap.get(key);
            int max = maxMap.getOrDefault(key, 550);

            int pct = 50;
            String stat = "OK";
            if (cur == -2 || cur == 0) {
                pct = 0;
                stat = "EMPTY";
            } else if (cur == -3) { // SNMP Printer MIB: -3 means some paper present
                pct = 60;
                stat = "OK";
            } else if (max > 0 && cur > 0) {
                pct = (int) Math.min(100, Math.round(((double) cur / max) * 100.0));
                stat = pct <= 10 ? "LOW" : "OK";
            }

            String size = name.toLowerCase().contains("bypass") ? "Multi" : (idx % 2 == 0 ? "A3" : "A4");
            trays.add(new PrinterMonitoringDto.TrayItem(idx++, name, size, pct, stat));
        }

        if (trays.isEmpty()) {
            trays.add(new PrinterMonitoringDto.TrayItem(1, "Drawer 1", "A4", 75, "OK"));
            trays.add(new PrinterMonitoringDto.TrayItem(2, "Drawer 2", "A3", 40, "OK"));
        }

        dto.setTrays(trays);
    }

    private void parseAlertDetails(List<SnmpClient.SnmpItem> items, PrinterMonitoringDto dto) {
        Map<String, Integer> sevMap = new HashMap<>();
        Map<String, Integer> codeMap = new HashMap<>();
        Map<String, String> descMap = new HashMap<>();
        Map<String, String> locMap = new HashMap<>();
        Map<String, String> timeMap = new HashMap<>();

        for (SnmpClient.SnmpItem item : items) {
            String oid = item.getOid();
            if (oid.startsWith("1.3.6.1.2.1.43.18.1.1.2.1.")) { // Severity
                String sub = oid.substring("1.3.6.1.2.1.43.18.1.1.2.1.".length());
                if (item.getValue().matches("\\d+")) sevMap.put(sub, Integer.parseInt(item.getValue()));
            } else if (oid.startsWith("1.3.6.1.2.1.43.18.1.1.6.1.")) { // Location code
                String sub = oid.substring("1.3.6.1.2.1.43.18.1.1.6.1.".length());
                locMap.put(sub, item.getValue());
            } else if (oid.startsWith("1.3.6.1.2.1.43.18.1.1.7.1.")) { // Alert Code
                String sub = oid.substring("1.3.6.1.2.1.43.18.1.1.7.1.".length());
                if (item.getValue().matches("\\d+")) codeMap.put(sub, Integer.parseInt(item.getValue()));
            } else if (oid.startsWith("1.3.6.1.2.1.43.18.1.1.8.1.")) { // Description
                String sub = oid.substring("1.3.6.1.2.1.43.18.1.1.8.1.".length());
                descMap.put(sub, item.getValue());
            } else if (oid.startsWith("1.3.6.1.2.1.43.18.1.1.9.1.")) { // Time
                String sub = oid.substring("1.3.6.1.2.1.43.18.1.1.9.1.".length());
                timeMap.put(sub, item.getValue());
            }
        }

        List<PrinterMonitoringDto.AlertDetailItem> details = new ArrayList<>();
        List<String> simpleAlerts = new ArrayList<>();

        for (String sub : descMap.keySet()) {
            String desc = descMap.get(sub);
            int code = codeMap.getOrDefault(sub, 0);
            int sev = sevMap.getOrDefault(sub, 4);
            String rawLoc = locMap.getOrDefault(sub, "");
            String time = timeMap.getOrDefault(sub, "");

            String sevStr = (sev == 3) ? "CRITICAL" : "WARNING";
            String codeName = mapAlertCodeName(code);
            String locName = mapLocationName(rawLoc, desc);

            details.add(new PrinterMonitoringDto.AlertDetailItem(code, codeName, desc, sevStr, locName, time));
            if (!simpleAlerts.contains(desc)) {
                simpleAlerts.add(desc);
            }
        }

        dto.setAlertDetails(details);
        dto.setAlerts(simpleAlerts);
    }

    private String mapAlertCodeName(int code) {
        return switch (code) {
            case 3 -> "coverOpen";
            case 4 -> "coverClosed";
            case 5 -> "interlockOpen";
            case 8 -> "mediaJam";
            case 501 -> "doorOpen";
            case 503 -> "serviceRequested";
            case 801 -> "inputMediaTrayMissing";
            case 807 -> "inputMediaSupplyLow";
            case 808 -> "inputMediaEmpty";
            case 903 -> "outputMediaTrayFull";
            case 1102 -> "markerSupplyLow";
            case 1104 -> "markerSupplyEmpty";
            case 1106 -> "wasteTonerFull";
            case 1107 -> "fuserTempHigh";
            case 1108 -> "fuserTempLow";
            default -> "code_" + code;
        };
    }

    private String mapLocationName(String locCode, String desc) {
        if ("801".equals(locCode) || desc.toLowerCase().contains("drawer1")) return "Drawer 1 (A4)";
        if ("802".equals(locCode) || desc.toLowerCase().contains("drawer2")) return "Drawer 2 (A3)";
        if ("803".equals(locCode) || desc.toLowerCase().contains("drawer3")) return "Drawer 3";
        if ("804".equals(locCode) || desc.toLowerCase().contains("drawer4")) return "Drawer 4";
        if ("805".equals(locCode) || desc.toLowerCase().contains("large capacity")) return "Large Capacity Feeder (LCF)";
        if ("806".equals(locCode) || desc.toLowerCase().contains("bypass")) return "Bypass Tray";
        if (desc.toLowerCase().contains("fuser")) return "Fuser Unit";
        if (desc.toLowerCase().contains("transport")) return "Paper Transport Path";
        return !locCode.isEmpty() ? "Sub-unit #" + locCode : "Engine";
    }

    private boolean hasCriticalAlert(PrinterMonitoringDto dto) {
        if (dto.getAlertDetails() != null) {
            for (PrinterMonitoringDto.AlertDetailItem item : dto.getAlertDetails()) {
                if ("CRITICAL".equalsIgnoreCase(item.getSeverity())) return true;
                String c = item.getCodeName().toLowerCase();
                if (c.contains("jam") || c.contains("dooropen") || c.contains("coveropen") || c.contains("service")) {
                    return true;
                }
            }
        }
        if (dto.getAlerts() != null) {
            for (String a : dto.getAlerts()) {
                String l = a.toLowerCase();
                if (l.contains("jam") || l.contains("door open") || l.contains("cover open") || l.contains("fatal") || l.contains("service")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAnyTonerLow(List<PrinterMonitoringDto.TonerItem> toners) {
        if (toners == null) return false;
        for (PrinterMonitoringDto.TonerItem t : toners) {
            if ("LOW".equalsIgnoreCase(t.getStatus()) || "EMPTY".equalsIgnoreCase(t.getStatus()) || (t.getLevelPercent() >= 0 && t.getLevelPercent() <= 15)) {
                return true;
            }
        }
        return false;
    }

    private String inferFloor(String loc) {
        if (loc == null) return "Floor 1";
        String lower = loc.toLowerCase();
        for (int i = 1; i <= 10; i++) {
            if (lower.contains("floor " + i) || lower.contains("level " + i) || lower.contains(i + "f") || lower.contains("fl" + i)) {
                return "Floor " + i;
            }
        }
        if (lower.contains("main floor")) return "Ground Floor";
        if (lower.contains("main office")) return "Level 1";
        if (lower.contains("reception") || lower.contains("lobby")) return "Floor 1";
        if (lower.contains("finance") || lower.contains("accounts")) return "Floor 3";
        return "Office Floor";
    }

    private String inferDepartment(String loc, String name) {
        String combined = ((loc != null ? loc : "") + " " + (name != null ? name : "")).toLowerCase();
        if (combined.contains("finance") || combined.contains("account")) return "Finance & Accounts";
        if (combined.contains("hr") || combined.contains("human")) return "Human Resources";
        if (combined.contains("it") || combined.contains("tech") || combined.contains("dev")) return "IT & Software Eng";
        if (combined.contains("main floor")) return "Production Floor";
        if (combined.contains("main office")) return "Executive Management";
        return "General Office";
    }
}
