package com.rr.secureprint.service;

import com.rr.secureprint.dto.FleetMonitoringSummaryDto;
import com.rr.secureprint.dto.PrinterMonitoringDto;
import com.rr.secureprint.entity.CopyJobEntity;
import com.rr.secureprint.entity.PrinterEntity;
import com.rr.secureprint.entity.PrintJob;
import com.rr.secureprint.entity.ScanJobEntity;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.repository.CopyJobRepository;
import com.rr.secureprint.repository.PrinterRepository;
import com.rr.secureprint.repository.PrintJobRepository;
import com.rr.secureprint.repository.ScanJobRepository;
import com.rr.secureprint.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final double DEFAULT_PAGE_RATE_LKR = 3.00;
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final PrintJobRepository printJobRepository;
    private final CopyJobRepository copyJobRepository;
    private final PrinterRepository printerRepository;
    private final ScanJobRepository scanJobRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.rr.secureprint.repository.AuditLogRepository auditLogRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SnmpMonitoringService snmpMonitoringService;

    public ReportService(UserRepository userRepository,
                         PrintJobRepository printJobRepository,
                         CopyJobRepository copyJobRepository,
                         PrinterRepository printerRepository,
                         ScanJobRepository scanJobRepository) {
        this.userRepository = userRepository;
        this.printJobRepository = printJobRepository;
        this.copyJobRepository = copyJobRepository;
        this.printerRepository = printerRepository;
        this.scanJobRepository = scanJobRepository;
    }

    public record ReportSummaryDto(
            long totalPrintPages,
            long totalCopyPages,
            long totalScanCount,
            long totalVolumePages,
            double totalEstimatedCostLkr,
            long totalUsers,
            long totalDepartments,
            long totalCostCenters,
            long totalPrinters,
            String topDepartment,
            long topDepartmentPages,
            String topCostCenter,
            long topCostCenterPages,
            String topPrinter,
            long topPrinterPages
    ) {}

    public record DepartmentReportDto(
            String department,
            int userCount,
            int printerCount,
            List<String> assignedPrinters,
            long printPages,
            long copyPages,
            long totalPages,
            double estimatedCostLkr,
            double percentOfTotal,
            List<UserSummaryDto> topUsers
    ) {}

    public record CostCenterReportDto(
            String costCenter,
            String departments,
            int userCount,
            long printPages,
            long copyPages,
            long totalPages,
            double estimatedCostLkr,
            double percentOfTotal,
            List<UserSummaryDto> topUsers
    ) {}

    public record PrinterReportDto(
            Long id,
            String printerCode,
            String name,
            String ipAddress,
            String model,
            String department,
            String location,
            String status,
            long printJobs,
            long printPages,
            long copyJobs,
            long copyPages,
            long totalPages,
            double estimatedCostLkr,
            double percentOfTotal
    ) {}

    public record UserSummaryDto(
            Long id,
            String username,
            String displayName,
            String department,
            String costCenter,
            String badgeId,
            long printPages,
            long copyPages,
            long scanPages,
            long totalPages,
            double estimatedCostLkr,
            boolean isActive
    ) {}

    public record TransactionReportDto(
            String id,
            String timestamp,
            String type,
            String username,
            String displayName,
            String department,
            String costCenter,
            String printerName,
            String printerIp,
            String documentName,
            int pages,
            int colorPages,
            int monoPages,
            double estimatedCostLkr,
            String status
    ) {}

    public record ScanReportDto(
            Long id,
            String timestamp,
            String username,
            String displayName,
            String department,
            String costCenter,
            String printerName,
            String printerIp,
            String recipientEmail,
            String fileName,
            int pages,
            String colorMode,
            String status
    ) {}

    public record EsgReportDto(
            long totalPagesPrinted,
            long duplexPagesSaved,
            long digitalScansProcessed,
            double treesConsumed,
            double treesSaved,
            double carbonKgEmitted,
            double carbonKgSaved,
            double energyKwhConsumed,
            double duplexSavingPercentage,
            List<DepartmentEsgDto> departmentBreakdown
    ) {}

    public record DepartmentEsgDto(
            String department,
            long pagesPrinted,
            long scansProcessed,
            double treesConsumed,
            double carbonKgEmitted
    ) {}

    public ReportSummaryDto getSummary() {
        List<UserEntity> users = userRepository.findAll();
        List<PrinterEntity> printers = printerRepository.findAll();

        long totalPrint = users.stream().mapToLong(UserEntity::getPrintCount).sum();
        long totalCopy = users.stream().mapToLong(UserEntity::getCopyCount).sum();
        long totalScan = users.stream().mapToLong(UserEntity::getScanCount).sum();
        long totalVolume = totalPrint + totalCopy;
        double totalCost = roundCost(totalVolume * DEFAULT_PAGE_RATE_LKR);

        Set<String> departments = new HashSet<>();
        users.forEach(u -> departments.add(u.getDepartment() != null && !u.getDepartment().isBlank() ? u.getDepartment().trim() : "Unassigned"));
        printers.forEach(p -> departments.add(p.getDepartment() != null && !p.getDepartment().isBlank() ? p.getDepartment().trim() : "Unassigned"));

        Set<String> costCenters = users.stream()
                .map(u -> (u.getExternalAttribute3() != null && !u.getExternalAttribute3().isBlank()) ? u.getExternalAttribute3().trim() : "General")
                .collect(Collectors.toSet());

        // Top department
        Map<String, Long> deptTotals = users.stream()
                .collect(Collectors.groupingBy(
                        u -> (u.getDepartment() != null && !u.getDepartment().isBlank()) ? u.getDepartment().trim() : "Unassigned",
                        Collectors.summingLong(UserEntity::getTotalPages)
                ));

        String topDept = "None";
        long topDeptPages = 0;
        for (Map.Entry<String, Long> e : deptTotals.entrySet()) {
            if (e.getValue() > topDeptPages) {
                topDept = e.getKey();
                topDeptPages = e.getValue();
            }
        }

        // Top cost center
        Map<String, Long> ccTotals = users.stream()
                .collect(Collectors.groupingBy(
                        u -> (u.getExternalAttribute3() != null && !u.getExternalAttribute3().isBlank()) ? u.getExternalAttribute3().trim() : "General",
                        Collectors.summingLong(UserEntity::getTotalPages)
                ));

        String topCc = "None";
        long topCcPages = 0;
        for (Map.Entry<String, Long> e : ccTotals.entrySet()) {
            if (e.getValue() > topCcPages) {
                topCc = e.getKey();
                topCcPages = e.getValue();
            }
        }

        // Top printer device
        List<PrinterReportDto> printerReports = getPrinterReports(null, null);
        String topPrinter = "None";
        long topPrinterPages = 0;
        if (!printerReports.isEmpty()) {
            topPrinter = printerReports.get(0).name();
            topPrinterPages = printerReports.get(0).totalPages();
        }

        return new ReportSummaryDto(
                totalPrint,
                totalCopy,
                totalScan,
                totalVolume,
                totalCost,
                users.size(),
                departments.size(),
                costCenters.size(),
                printers.size(),
                topDept,
                topDeptPages,
                topCc,
                topCcPages,
                topPrinter,
                topPrinterPages
        );
    }

    public List<DepartmentReportDto> getDepartmentReports() {
        List<UserEntity> users = userRepository.findAll();
        List<PrinterEntity> allPrinters = printerRepository.findAll();
        long overallTotal = users.stream().mapToLong(UserEntity::getTotalPages).sum();

        // Collect all distinct departments across users and printers
        Set<String> allDeptNames = new HashSet<>();
        users.forEach(u -> allDeptNames.add((u.getDepartment() != null && !u.getDepartment().isBlank()) ? u.getDepartment().trim() : "Operations"));
        allPrinters.forEach(p -> allDeptNames.add(p.getDepartment() != null && !p.getDepartment().isBlank() ? p.getDepartment().trim() : "Operations"));

        Map<String, List<UserEntity>> byDept = users.stream()
                .collect(Collectors.groupingBy(
                        u -> (u.getDepartment() != null && !u.getDepartment().isBlank()) ? u.getDepartment().trim() : "Operations"
                ));

        List<DepartmentReportDto> result = new ArrayList<>();
        for (String dept : allDeptNames) {
            List<UserEntity> deptUsers = byDept.getOrDefault(dept, Collections.emptyList());

            long printP = deptUsers.stream().mapToLong(UserEntity::getPrintCount).sum();
            long copyP = deptUsers.stream().mapToLong(UserEntity::getCopyCount).sum();
            long totalP = printP + copyP;
            double cost = roundCost(totalP * DEFAULT_PAGE_RATE_LKR);
            double pct = overallTotal > 0 ? roundCost(((double) totalP / overallTotal) * 100.0) : 0.0;

            List<String> assignedPrinters = allPrinters.stream()
                    .filter(p -> dept.equalsIgnoreCase(p.getDepartment()))
                    .map(p -> p.getName() + " (" + p.getIpAddress() + ")")
                    .collect(Collectors.toList());

            List<UserSummaryDto> topUsers = deptUsers.stream()
                    .sorted((a, b) -> Long.compare(b.getTotalPages(), a.getTotalPages()))
                    .limit(10)
                    .map(this::mapUserToDto)
                    .collect(Collectors.toList());

            result.add(new DepartmentReportDto(
                    dept,
                    deptUsers.size(),
                    assignedPrinters.size(),
                    assignedPrinters,
                    printP,
                    copyP,
                    totalP,
                    cost,
                    pct,
                    topUsers
            ));
        }

        result.sort((a, b) -> Long.compare(b.totalPages(), a.totalPages()));
        return result;
    }

    public List<CostCenterReportDto> getCostCenterReports() {
        List<UserEntity> users = userRepository.findAll();
        long overallTotal = users.stream().mapToLong(UserEntity::getTotalPages).sum();

        Map<String, List<UserEntity>> byCc = users.stream()
                .collect(Collectors.groupingBy(
                        u -> (u.getExternalAttribute3() != null && !u.getExternalAttribute3().isBlank()) ? u.getExternalAttribute3().trim() : "General"
                ));

        List<CostCenterReportDto> result = new ArrayList<>();
        for (Map.Entry<String, List<UserEntity>> entry : byCc.entrySet()) {
            String cc = entry.getKey();
            List<UserEntity> ccUsers = entry.getValue();

            long printP = ccUsers.stream().mapToLong(UserEntity::getPrintCount).sum();
            long copyP = ccUsers.stream().mapToLong(UserEntity::getCopyCount).sum();
            long totalP = printP + copyP;
            double cost = roundCost(totalP * DEFAULT_PAGE_RATE_LKR);
            double pct = overallTotal > 0 ? roundCost(((double) totalP / overallTotal) * 100.0) : 0.0;

            String depts = ccUsers.stream()
                    .map(u -> (u.getDepartment() != null && !u.getDepartment().isBlank()) ? u.getDepartment().trim() : "Operations")
                    .distinct()
                    .collect(Collectors.joining(", "));

            List<UserSummaryDto> topUsers = ccUsers.stream()
                    .sorted((a, b) -> Long.compare(b.getTotalPages(), a.getTotalPages()))
                    .limit(10)
                    .map(this::mapUserToDto)
                    .collect(Collectors.toList());

            result.add(new CostCenterReportDto(
                    cc,
                    depts,
                    ccUsers.size(),
                    printP,
                    copyP,
                    totalP,
                    cost,
                    pct,
                    topUsers
            ));
        }

        result.sort((a, b) -> Long.compare(b.totalPages(), a.totalPages()));
        return result;
    }

    public List<PrinterReportDto> getPrinterReports(String departmentFilter, String search) {
        List<PrinterEntity> printers = printerRepository.findAll();
        List<PrintJob> printJobs = printJobRepository.findAll();
        List<CopyJobEntity> copyJobs = copyJobRepository.findAll();

        long fleetTotalPages = 0;
        for (PrinterEntity p : printers) {
            long pPages = printJobs.stream().filter(j -> j.getTargetPrinter() != null && j.getTargetPrinter().getId().equals(p.getId())).mapToLong(PrintJob::getPageCount).sum();
            long cPages = copyJobs.stream().filter(c -> (c.getPrinter() != null && c.getPrinter().getId().equals(p.getId())) || (c.getPrinterIp() != null && c.getPrinterIp().equals(p.getIpAddress()))).mapToLong(CopyJobEntity::getPages).sum();
            fleetTotalPages += (pPages + cPages);
        }

        List<PrinterReportDto> list = new ArrayList<>();
        for (PrinterEntity p : printers) {
            String pDept = p.getDepartment();
            if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)) {
                if (!pDept.equalsIgnoreCase(departmentFilter.trim())) {
                    continue;
                }
            }

            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase().trim();
                boolean matchName = p.getName().toLowerCase().contains(q);
                boolean matchCode = p.getPrinterCode().toLowerCase().contains(q);
                boolean matchIp = p.getIpAddress().toLowerCase().contains(q);
                boolean matchModel = p.getModel() != null && p.getModel().toLowerCase().contains(q);
                boolean matchDept = pDept.toLowerCase().contains(q);
                boolean matchLoc = p.getLocation() != null && p.getLocation().toLowerCase().contains(q);
                if (!matchName && !matchCode && !matchIp && !matchModel && !matchDept && !matchLoc) {
                    continue;
                }
            }

            long pJobs = printJobs.stream().filter(j -> j.getTargetPrinter() != null && j.getTargetPrinter().getId().equals(p.getId())).count();
            long pPages = printJobs.stream().filter(j -> j.getTargetPrinter() != null && j.getTargetPrinter().getId().equals(p.getId())).mapToLong(PrintJob::getPageCount).sum();

            long cJobs = copyJobs.stream().filter(c -> (c.getPrinter() != null && c.getPrinter().getId().equals(p.getId())) || (c.getPrinterIp() != null && c.getPrinterIp().equals(p.getIpAddress()))).count();
            long cPages = copyJobs.stream().filter(c -> (c.getPrinter() != null && c.getPrinter().getId().equals(p.getId())) || (c.getPrinterIp() != null && c.getPrinterIp().equals(p.getIpAddress()))).mapToLong(CopyJobEntity::getPages).sum();

            long totalP = pPages + cPages;
            double cost = roundCost(totalP * DEFAULT_PAGE_RATE_LKR);
            double pct = fleetTotalPages > 0 ? roundCost(((double) totalP / fleetTotalPages) * 100.0) : 0.0;

            list.add(new PrinterReportDto(
                    p.getId(),
                    p.getPrinterCode(),
                    p.getName(),
                    p.getIpAddress(),
                    p.getModel(),
                    pDept,
                    p.getLocation(),
                    p.getStatus(),
                    pJobs,
                    pPages,
                    cJobs,
                    cPages,
                    totalP,
                    cost,
                    pct
            ));
        }

        list.sort((a, b) -> Long.compare(b.totalPages(), a.totalPages()));
        return list;
    }

    public List<UserSummaryDto> getUserReports(String departmentFilter, String costCenterFilter, String search) {
        List<UserEntity> users = userRepository.findAll();

        return users.stream()
                .filter(u -> {
                    if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)) {
                        String dept = u.getDepartment() != null ? u.getDepartment().trim() : "Operations";
                        if (!dept.equalsIgnoreCase(departmentFilter.trim())) return false;
                    }
                    if (costCenterFilter != null && !costCenterFilter.isBlank() && !"ALL".equalsIgnoreCase(costCenterFilter)) {
                        String cc = u.getExternalAttribute3() != null ? u.getExternalAttribute3().trim() : "General";
                        if (!cc.equalsIgnoreCase(costCenterFilter.trim())) return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase();
                        boolean matchName = u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(q);
                        boolean matchUser = u.getUsername() != null && u.getUsername().toLowerCase().contains(q);
                        boolean matchCard = u.getBadgeId() != null && u.getBadgeId().toLowerCase().contains(q);
                        boolean matchDept = u.getDepartment() != null && u.getDepartment().toLowerCase().contains(q);
                        boolean matchCc = u.getExternalAttribute3() != null && u.getExternalAttribute3().toLowerCase().contains(q);
                        if (!matchName && !matchUser && !matchCard && !matchDept && !matchCc) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> Long.compare(b.getTotalPages(), a.getTotalPages()))
                .map(this::mapUserToDto)
                .collect(Collectors.toList());
    }

    public List<TransactionReportDto> getTransactionReports(String typeFilter, String departmentFilter, String costCenterFilter,
                                                           String printerFilter, String startDate, String endDate, String search) {
        List<TransactionReportDto> list = new ArrayList<>();

        Instant startInstant = parseStartInstant(startDate);
        Instant endInstant = parseEndInstant(endDate);

        // 1. Process Print Jobs
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter) || "PRINT".equalsIgnoreCase(typeFilter)) {
            List<PrintJob> printJobs = printJobRepository.findAll();
            for (PrintJob j : printJobs) {
                Instant time = j.getReleasedAt() != null ? j.getReleasedAt() : (j.getSubmittedAt() != null ? j.getSubmittedAt() : Instant.now());
                if (startInstant != null && time.isBefore(startInstant)) continue;
                if (endInstant != null && time.isAfter(endInstant)) continue;

                UserEntity u = j.getUser();
                String dept = u != null && u.getDepartment() != null ? u.getDepartment() : "Operations";
                String cc = u != null && u.getExternalAttribute3() != null ? u.getExternalAttribute3() : "General";
                String printerName = j.getTargetPrinter() != null ? j.getTargetPrinter().getName() : "Virtual Print Queue";
                String printerIp = j.getTargetPrinter() != null ? j.getTargetPrinter().getIpAddress() : "127.0.0.1";

                if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter) && !dept.equalsIgnoreCase(departmentFilter.trim())) continue;
                if (costCenterFilter != null && !costCenterFilter.isBlank() && !"ALL".equalsIgnoreCase(costCenterFilter) && !cc.equalsIgnoreCase(costCenterFilter.trim())) continue;
                if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter) && !printerName.equalsIgnoreCase(printerFilter.trim()) && !printerIp.equalsIgnoreCase(printerFilter.trim())) continue;

                if (search != null && !search.isBlank()) {
                    String q = search.toLowerCase().trim();
                    boolean mDoc = j.getDocumentName() != null && j.getDocumentName().toLowerCase().contains(q);
                    boolean mUser = j.getUsername() != null && j.getUsername().toLowerCase().contains(q);
                    boolean mDept = dept.toLowerCase().contains(q);
                    boolean mCc = cc.toLowerCase().contains(q);
                    boolean mPrinter = printerName.toLowerCase().contains(q);
                    if (!mDoc && !mUser && !mDept && !mCc && !mPrinter) continue;
                }

                int p = j.getPageCount() * Math.max(1, j.getCopies());
                double cost = roundCost(p * DEFAULT_PAGE_RATE_LKR);

                list.add(new TransactionReportDto(
                        "PRINT-" + (j.getJobNumber() != null ? j.getJobNumber() : j.getId().toString().substring(0, 8)),
                        DISPLAY_DATE_FMT.format(time),
                        "PRINT",
                        j.getUsername(),
                        u != null ? u.getDisplayName() : j.getUsername(),
                        dept,
                        cc,
                        printerName,
                        printerIp,
                        j.getDocumentName(),
                        p,
                        j.isColor() ? p : 0,
                        j.isColor() ? 0 : p,
                        cost,
                        j.getStatus() != null ? j.getStatus().name() : "COMPLETED"
                ));
            }
        }

        // 2. Process Walk-up Photocopy Jobs
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter) || "COPY".equalsIgnoreCase(typeFilter)) {
            List<CopyJobEntity> copyJobs = copyJobRepository.findAll();
            for (CopyJobEntity c : copyJobs) {
                Instant time = c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt();
                if (startInstant != null && time.isBefore(startInstant)) continue;
                if (endInstant != null && time.isAfter(endInstant)) continue;

                UserEntity u = c.getUser();
                String dept = u != null && u.getDepartment() != null ? u.getDepartment() : "Operations";
                String cc = u != null && u.getExternalAttribute3() != null ? u.getExternalAttribute3() : "General";
                String printerName = c.getPrinterName() != null ? c.getPrinterName() : (c.getPrinter() != null ? c.getPrinter().getName() : "Toshiba MFP");
                String printerIp = c.getPrinterIp() != null ? c.getPrinterIp() : (c.getPrinter() != null ? c.getPrinter().getIpAddress() : "");

                if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter) && !dept.equalsIgnoreCase(departmentFilter.trim())) continue;
                if (costCenterFilter != null && !costCenterFilter.isBlank() && !"ALL".equalsIgnoreCase(costCenterFilter) && !cc.equalsIgnoreCase(costCenterFilter.trim())) continue;
                if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter) && !printerName.equalsIgnoreCase(printerFilter.trim()) && !printerIp.equalsIgnoreCase(printerFilter.trim())) continue;

                if (search != null && !search.isBlank()) {
                    String q = search.toLowerCase().trim();
                    boolean mUser = c.getUsername() != null && c.getUsername().toLowerCase().contains(q);
                    boolean mDept = dept.toLowerCase().contains(q);
                    boolean mCc = cc.toLowerCase().contains(q);
                    boolean mPrinter = printerName.toLowerCase().contains(q);
                    if (!mUser && !mDept && !mCc && !mPrinter) continue;
                }

                int p = c.getPages();
                double cost = roundCost(p * DEFAULT_PAGE_RATE_LKR);

                list.add(new TransactionReportDto(
                        "COPY-" + (c.getId() != null ? c.getId() : "LOG"),
                        DISPLAY_DATE_FMT.format(time),
                        "COPY",
                        c.getUsername(),
                        u != null ? u.getDisplayName() : c.getUsername(),
                        dept,
                        cc,
                        printerName,
                        printerIp,
                        "Walk-up Copy Job (" + (c.getColorPages() > 0 ? "Color " + c.getColorPages() + "p, " : "") + "Mono " + c.getMonoPages() + "p)",
                        p,
                        c.getColorPages(),
                        c.getMonoPages(),
                        cost,
                        "COMPLETED"
                ));
            }
        }

        // 3. Process Scan to Email Jobs
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter) || "SCAN".equalsIgnoreCase(typeFilter)) {
            List<ScanJobEntity> scanJobs = scanJobRepository.findAll();
            for (ScanJobEntity s : scanJobs) {
                Instant time = s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt();
                if (startInstant != null && time.isBefore(startInstant)) continue;
                if (endInstant != null && time.isAfter(endInstant)) continue;

                UserEntity u = s.getUser();
                String dept = u != null && u.getDepartment() != null ? u.getDepartment() : "Operations";
                String cc = u != null && u.getExternalAttribute3() != null ? u.getExternalAttribute3() : "General";
                String printerName = s.getPrinterName() != null ? s.getPrinterName() : (s.getPrinter() != null ? s.getPrinter().getName() : "Toshiba MFP");
                String printerIp = s.getPrinterIp() != null ? s.getPrinterIp() : (s.getPrinter() != null ? s.getPrinter().getIpAddress() : "");

                if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter) && !dept.equalsIgnoreCase(departmentFilter.trim())) continue;
                if (costCenterFilter != null && !costCenterFilter.isBlank() && !"ALL".equalsIgnoreCase(costCenterFilter) && !cc.equalsIgnoreCase(costCenterFilter.trim())) continue;
                if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter) && !printerName.equalsIgnoreCase(printerFilter.trim()) && !printerIp.equalsIgnoreCase(printerFilter.trim())) continue;

                if (search != null && !search.isBlank()) {
                    String q = search.toLowerCase().trim();
                    boolean mUser = s.getUsername() != null && s.getUsername().toLowerCase().contains(q);
                    boolean mDept = dept.toLowerCase().contains(q);
                    boolean mCc = cc.toLowerCase().contains(q);
                    boolean mPrinter = printerName.toLowerCase().contains(q);
                    boolean mFile = s.getFileName() != null && s.getFileName().toLowerCase().contains(q);
                    if (!mUser && !mDept && !mCc && !mPrinter && !mFile) continue;
                }

                list.add(new TransactionReportDto(
                        "SCAN-" + (s.getId() != null ? s.getId() : "LOG"),
                        DISPLAY_DATE_FMT.format(time),
                        "SCAN",
                        s.getUsername(),
                        u != null ? u.getDisplayName() : s.getUsername(),
                        dept,
                        cc,
                        printerName,
                        printerIp,
                        "Scan to Email -> " + s.getRecipientEmail() + " (" + (s.getFileName() != null ? s.getFileName() : "Document.pdf") + ")",
                        s.getPages(),
                        "COLOR".equalsIgnoreCase(s.getColorMode()) ? s.getPages() : 0,
                        "COLOR".equalsIgnoreCase(s.getColorMode()) ? 0 : s.getPages(),
                        0.0,
                        s.getStatus() != null ? s.getStatus() : "SENT"
                ));
            }
        }

        list.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
        return list;
    }

    public List<ScanReportDto> getScanReports(String departmentFilter, String costCenterFilter,
                                             String printerFilter, String startDate, String endDate, String search) {
        Instant startInstant = parseStartInstant(startDate);
        Instant endInstant = parseEndInstant(endDate);

        List<ScanJobEntity> scans = scanJobRepository.findAll();
        List<ScanReportDto> list = new ArrayList<>();

        for (ScanJobEntity s : scans) {
            Instant time = s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt();
            if (startInstant != null && time.isBefore(startInstant)) continue;
            if (endInstant != null && time.isAfter(endInstant)) continue;

            UserEntity u = s.getUser();
            String dept = u != null && u.getDepartment() != null ? u.getDepartment() : "Operations";
            String cc = u != null && u.getExternalAttribute3() != null ? u.getExternalAttribute3() : "General";
            String prnName = s.getPrinterName() != null ? s.getPrinterName() : (s.getPrinter() != null ? s.getPrinter().getName() : "Toshiba MFP");
            String prnIp = s.getPrinterIp() != null ? s.getPrinterIp() : (s.getPrinter() != null ? s.getPrinter().getIpAddress() : "");

            if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter) && !dept.equalsIgnoreCase(departmentFilter.trim())) continue;
            if (costCenterFilter != null && !costCenterFilter.isBlank() && !"ALL".equalsIgnoreCase(costCenterFilter) && !cc.equalsIgnoreCase(costCenterFilter.trim())) continue;
            if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter) && !prnName.equalsIgnoreCase(printerFilter.trim()) && !prnIp.equalsIgnoreCase(printerFilter.trim())) continue;

            if (search != null && !search.isBlank()) {
                String q = search.toLowerCase().trim();
                boolean mUser = s.getUsername() != null && s.getUsername().toLowerCase().contains(q);
                boolean mEmail = s.getRecipientEmail() != null && s.getRecipientEmail().toLowerCase().contains(q);
                boolean mFile = s.getFileName() != null && s.getFileName().toLowerCase().contains(q);
                boolean mDept = dept.toLowerCase().contains(q);
                boolean mCc = cc.toLowerCase().contains(q);
                boolean mPrn = prnName.toLowerCase().contains(q);
                if (!mUser && !mEmail && !mFile && !mDept && !mCc && !mPrn) continue;
            }

            list.add(new ScanReportDto(
                    s.getId(),
                    DISPLAY_DATE_FMT.format(time),
                    s.getUsername(),
                    u != null ? u.getDisplayName() : s.getUsername(),
                    dept,
                    cc,
                    prnName,
                    prnIp,
                    s.getRecipientEmail(),
                    s.getFileName() != null ? s.getFileName() : "Document.pdf",
                    s.getPages(),
                    s.getColorMode() != null ? s.getColorMode() : "COLOR",
                    s.getStatus() != null ? s.getStatus() : "SENT"
            ));
        }

        list.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
        return list;
    }

    public EsgReportDto getEsgReport(String startDate, String endDate) {
        List<UserEntity> users = userRepository.findAll();
        long totalPrinted = users.stream().mapToLong(UserEntity::getTotalPages).sum();
        long totalScans = users.stream().mapToLong(UserEntity::getScanCount).sum();

        long duplexSaved = Math.round(totalPrinted * 0.20); 

        double treesConsumed = BigDecimal.valueOf(totalPrinted / 8333.0).setScale(3, RoundingMode.HALF_UP).doubleValue();
        double treesSaved = BigDecimal.valueOf((duplexSaved + (totalScans * 1.5)) / 8333.0).setScale(3, RoundingMode.HALF_UP).doubleValue();

        double carbonEmitted = BigDecimal.valueOf(totalPrinted * 0.0045).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double carbonSaved = BigDecimal.valueOf((duplexSaved + (totalScans * 1.5)) * 0.0045).setScale(2, RoundingMode.HALF_UP).doubleValue();

        double energyKwh = BigDecimal.valueOf(totalPrinted * 0.026).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double duplexPct = totalPrinted > 0 ? ((double) duplexSaved / totalPrinted) * 100.0 : 0.0;

        Map<String, List<UserEntity>> deptMap = users.stream()
                .collect(Collectors.groupingBy(u -> u.getDepartment() != null && !u.getDepartment().isBlank() ? u.getDepartment().trim() : "Operations"));

        List<DepartmentEsgDto> deptList = new ArrayList<>();
        for (Map.Entry<String, List<UserEntity>> entry : deptMap.entrySet()) {
            long pPages = entry.getValue().stream().mapToLong(UserEntity::getTotalPages).sum();
            long sPages = entry.getValue().stream().mapToLong(UserEntity::getScanCount).sum();
            double deptTrees = BigDecimal.valueOf(pPages / 8333.0).setScale(3, RoundingMode.HALF_UP).doubleValue();
            double deptCarbon = BigDecimal.valueOf(pPages * 0.0045).setScale(2, RoundingMode.HALF_UP).doubleValue();

            deptList.add(new DepartmentEsgDto(entry.getKey(), pPages, sPages, deptTrees, deptCarbon));
        }

        deptList.sort((a, b) -> Long.compare(b.pagesPrinted(), a.pagesPrinted()));

        return new EsgReportDto(
                totalPrinted,
                duplexSaved,
                totalScans,
                treesConsumed,
                treesSaved,
                carbonEmitted,
                carbonSaved,
                energyKwh,
                roundCost(duplexPct),
                deptList
        );
    }

    public String generateCsv(String reportType, String departmentFilter, String costCenterFilter,
                              String printerFilter, String startDate, String endDate) {
        StringBuilder csv = new StringBuilder();

        if ("cost-centers".equalsIgnoreCase(reportType)) {
            csv.append("Cost Center,Associated Departments,User Count,Print Pages,Copy Pages,Total Volume (Pages),Estimated Cost (LKR),% of Fleet Volume\n");
            List<CostCenterReportDto> list = getCostCenterReports();
            for (CostCenterReportDto r : list) {
                csv.append(escapeCsv(r.costCenter())).append(",")
                        .append(escapeCsv(r.departments())).append(",")
                        .append(r.userCount()).append(",")
                        .append(r.printPages()).append(",")
                        .append(r.copyPages()).append(",")
                        .append(r.totalPages()).append(",")
                        .append(String.format(Locale.US, "%.2f", r.estimatedCostLkr())).append(",")
                        .append(String.format(Locale.US, "%.1f%%", r.percentOfTotal())).append("\n");
            }
        } else if ("printers".equalsIgnoreCase(reportType)) {
            csv.append("Printer Code,Printer Name,IP Address,Model,Assigned Department,Location,Status,Print Jobs,Print Pages,Copy Jobs,Copy Pages,Total Pages,Estimated Cost (LKR),% of Fleet Volume\n");
            List<PrinterReportDto> list = getPrinterReports(departmentFilter, null);
            for (PrinterReportDto p : list) {
                csv.append(escapeCsv(p.printerCode())).append(",")
                        .append(escapeCsv(p.name())).append(",")
                        .append(escapeCsv(p.ipAddress())).append(",")
                        .append(escapeCsv(p.model())).append(",")
                        .append(escapeCsv(p.department())).append(",")
                        .append(escapeCsv(p.location())).append(",")
                        .append(escapeCsv(p.status())).append(",")
                        .append(p.printJobs()).append(",")
                        .append(p.printPages()).append(",")
                        .append(p.copyJobs()).append(",")
                        .append(p.copyPages()).append(",")
                        .append(p.totalPages()).append(",")
                        .append(String.format(Locale.US, "%.2f", p.estimatedCostLkr())).append(",")
                        .append(String.format(Locale.US, "%.1f%%", p.percentOfTotal())).append("\n");
            }
        } else if ("users".equalsIgnoreCase(reportType)) {
            csv.append("Username,Display Name,Department,Cost Center,Badge ID / Card ID,Print Pages,Copy Pages,Scan Docs,Total Volume (Pages),Estimated Cost (LKR),Status\n");
            List<UserSummaryDto> list = getUserReports(departmentFilter, costCenterFilter, null);
            for (UserSummaryDto u : list) {
                csv.append(escapeCsv(u.username())).append(",")
                        .append(escapeCsv(u.displayName())).append(",")
                        .append(escapeCsv(u.department())).append(",")
                        .append(escapeCsv(u.costCenter())).append(",")
                        .append(escapeCsv(u.badgeId() != null ? u.badgeId() : "")).append(",")
                        .append(u.printPages()).append(",")
                        .append(u.copyPages()).append(",")
                        .append(u.scanPages()).append(",")
                        .append(u.totalPages()).append(",")
                        .append(String.format(Locale.US, "%.2f", u.estimatedCostLkr())).append(",")
                        .append(u.isActive() ? "ACTIVE" : "DISABLED").append("\n");
            }
        } else if ("transactions".equalsIgnoreCase(reportType)) {
            csv.append("Transaction ID,Timestamp,Type,Username,Display Name,Department,Cost Center,Printer Device,Printer IP,Document / Action,Total Pages,Color Pages,Mono Pages,Estimated Cost (LKR),Status\n");
            List<TransactionReportDto> list = getTransactionReports(null, departmentFilter, costCenterFilter, printerFilter, startDate, endDate, null);
            for (TransactionReportDto t : list) {
                csv.append(escapeCsv(t.id())).append(",")
                        .append(escapeCsv(t.timestamp())).append(",")
                        .append(escapeCsv(t.type())).append(",")
                        .append(escapeCsv(t.username())).append(",")
                        .append(escapeCsv(t.displayName())).append(",")
                        .append(escapeCsv(t.department())).append(",")
                        .append(escapeCsv(t.costCenter())).append(",")
                        .append(escapeCsv(t.printerName())).append(",")
                        .append(escapeCsv(t.printerIp())).append(",")
                        .append(escapeCsv(t.documentName())).append(",")
                        .append(t.pages()).append(",")
                        .append(t.colorPages()).append(",")
                        .append(t.monoPages()).append(",")
                        .append(String.format(Locale.US, "%.2f", t.estimatedCostLkr())).append(",")
                        .append(escapeCsv(t.status())).append("\n");
            }
        } else if ("scans".equalsIgnoreCase(reportType)) {
            csv.append("Scan ID,Date Time,Username,Display Name,Department,Cost Center,MFP Printer,Printer IP,Recipient Email,Document File,Pages,Color Mode,Delivery Status\n");
            List<ScanReportDto> list = getScanReports(departmentFilter, costCenterFilter, printerFilter, startDate, endDate, null);
            for (ScanReportDto s : list) {
                csv.append(s.id()).append(",")
                        .append(escapeCsv(s.timestamp())).append(",")
                        .append(escapeCsv(s.username())).append(",")
                        .append(escapeCsv(s.displayName())).append(",")
                        .append(escapeCsv(s.department())).append(",")
                        .append(escapeCsv(s.costCenter())).append(",")
                        .append(escapeCsv(s.printerName())).append(",")
                        .append(escapeCsv(s.printerIp())).append(",")
                        .append(escapeCsv(s.recipientEmail())).append(",")
                        .append(escapeCsv(s.fileName())).append(",")
                        .append(s.pages()).append(",")
                        .append(escapeCsv(s.colorMode())).append(",")
                        .append(escapeCsv(s.status())).append("\n");
            }
        } else if ("esg".equalsIgnoreCase(reportType)) {
            csv.append("Department,Pages Printed,Digital Scans,Trees Consumed,CO2 Emitted (kg)\n");
            EsgReportDto esg = getEsgReport(startDate, endDate);
            for (DepartmentEsgDto d : esg.departmentBreakdown()) {
                csv.append(escapeCsv(d.department())).append(",")
                        .append(d.pagesPrinted()).append(",")
                        .append(d.scansProcessed()).append(",")
                        .append(String.format(Locale.US, "%.3f", d.treesConsumed())).append(",")
                        .append(String.format(Locale.US, "%.2f", d.carbonKgEmitted())).append("\n");
            }
        } else {
            // Default: Department report
            csv.append("Department,User Count,Printer Count,Assigned Printers,Print Pages,Copy Pages,Total Volume (Pages),Estimated Cost (LKR),% of Fleet Volume\n");
            List<DepartmentReportDto> list = getDepartmentReports();
            for (DepartmentReportDto r : list) {
                String printersJoined = r.assignedPrinters() != null ? String.join("; ", r.assignedPrinters()) : "";
                csv.append(escapeCsv(r.department())).append(",")
                        .append(r.userCount()).append(",")
                        .append(r.printerCount()).append(",")
                        .append(escapeCsv(printersJoined)).append(",")
                        .append(r.printPages()).append(",")
                        .append(r.copyPages()).append(",")
                        .append(r.totalPages()).append(",")
                        .append(String.format(Locale.US, "%.2f", r.estimatedCostLkr())).append(",")
                        .append(String.format(Locale.US, "%.1f%%", r.percentOfTotal())).append("\n");
            }
        }

        return csv.toString();
    }

    private UserSummaryDto mapUserToDto(UserEntity u) {
        String dept = (u.getDepartment() != null && !u.getDepartment().isBlank()) ? u.getDepartment().trim() : "Operations";
        String cc = (u.getExternalAttribute3() != null && !u.getExternalAttribute3().isBlank()) ? u.getExternalAttribute3().trim() : "General";
        double cost = roundCost(u.getTotalPages() * DEFAULT_PAGE_RATE_LKR);

        return new UserSummaryDto(
                u.getId(),
                u.getUsername(),
                u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                dept,
                cc,
                u.getBadgeId(),
                u.getPrintCount(),
                u.getCopyCount(),
                u.getScanCount(),
                u.getTotalPages(),
                cost,
                u.isActive()
        );
    }

    private Instant parseStartInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(dateStr.trim());
            return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseEndInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(dateStr.trim());
            return d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private double roundCost(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private Map<String, Object> kpi(String label, Object value, String sub) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("sub", sub != null ? sub : "");
        return m;
    }

    private String fmtTs(Instant ts) {
        return ts != null ? DISPLAY_DATE_FMT.format(ts) : "";
    }

    private String isoMonthOrDay(Instant ts, int len) {
        if (ts == null) return "Unknown";
        String formatted = DISPLAY_DATE_FMT.format(ts);
        return formatted.length() >= len ? formatted.substring(0, len) : formatted;
    }

    public Map<String, Object> generateHubReport(String reportType, String from, String to) {
        return generateHubReport(reportType, from, to, null, null);
    }

    public Map<String, Object> generateHubReport(String reportType, String from, String to,
                                                 String departmentFilter, String printerFilter) {
        String code = "RPT-00";
        String category = "General";
        String title = reportType;
        String description = "";
        List<Map<String, Object>> kpis = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        List<Object> footer = new ArrayList<>();

        Instant fromInst = parseStartInstant(from);
        Instant toInst = parseEndInstant(to);

        List<UserEntity> users = userRepository.findAll();
        List<PrinterEntity> printers = printerRepository.findAll();

        List<PrintJob> allPrintJobs = printJobRepository.findAll();
        List<CopyJobEntity> allCopyJobs = copyJobRepository.findAll();
        List<ScanJobEntity> allScanJobs = scanJobRepository.findAll();

        List<PrintJob> filteredPrint = allPrintJobs.stream().filter(j -> {
            Instant t = j.getReleasedAt() != null ? j.getReleasedAt() : j.getSubmittedAt();
            if (fromInst != null && (t == null || t.isBefore(fromInst))) return false;
            if (toInst != null && (t == null || t.isAfter(toInst))) return false;
            if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)) {
                String dept = (j.getUser() != null && j.getUser().getDepartment() != null) ? j.getUser().getDepartment() : "Operations";
                if (!dept.equalsIgnoreCase(departmentFilter.trim())) return false;
            }
            if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter)) {
                String pName = j.getTargetPrinter() != null ? j.getTargetPrinter().getName() : "";
                String pIp = j.getTargetPrinter() != null ? j.getTargetPrinter().getIpAddress() : "";
                if (!pName.equalsIgnoreCase(printerFilter.trim()) && !pIp.equalsIgnoreCase(printerFilter.trim())) return false;
            }
            return true;
        }).toList();

        List<CopyJobEntity> filteredCopy = allCopyJobs.stream().filter(c -> {
            Instant t = c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt();
            if (fromInst != null && (t == null || t.isBefore(fromInst))) return false;
            if (toInst != null && (t == null || t.isAfter(toInst))) return false;
            if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)) {
                String dept = (c.getUser() != null && c.getUser().getDepartment() != null) ? c.getUser().getDepartment() : "Operations";
                if (!dept.equalsIgnoreCase(departmentFilter.trim())) return false;
            }
            if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter)) {
                String pName = c.getPrinterName() != null ? c.getPrinterName() : (c.getPrinter() != null ? c.getPrinter().getName() : "");
                String pIp = c.getPrinterIp() != null ? c.getPrinterIp() : (c.getPrinter() != null ? c.getPrinter().getIpAddress() : "");
                if (!pName.equalsIgnoreCase(printerFilter.trim()) && !pIp.equalsIgnoreCase(printerFilter.trim())) return false;
            }
            return true;
        }).toList();

        List<ScanJobEntity> filteredScan = allScanJobs.stream().filter(s -> {
            Instant t = s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt();
            if (fromInst != null && (t == null || t.isBefore(fromInst))) return false;
            if (toInst != null && (t == null || t.isAfter(toInst))) return false;
            if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)) {
                String dept = (s.getUser() != null && s.getUser().getDepartment() != null) ? s.getUser().getDepartment() : "Operations";
                if (!dept.equalsIgnoreCase(departmentFilter.trim())) return false;
            }
            if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter)) {
                String pName = s.getPrinterName() != null ? s.getPrinterName() : (s.getPrinter() != null ? s.getPrinter().getName() : "");
                String pIp = s.getPrinterIp() != null ? s.getPrinterIp() : (s.getPrinter() != null ? s.getPrinter().getIpAddress() : "");
                if (!pName.equalsIgnoreCase(printerFilter.trim()) && !pIp.equalsIgnoreCase(printerFilter.trim())) return false;
            }
            return true;
        }).toList();

        switch (reportType) {
            case "user-summary" -> {
                code = "RPT-01";
                category = "Users & Cost Centers";
                title = "User-Wise Print, Copy & Scan Summary";
                description = "Centralized page volume, scan counts, and estimated LKR billing per user account.";
                columns = List.of("Username", "Display Name", "Department", "Cost Center", "Print Pages", "Copy Pages", "Scan Docs", "Total Pages", "Est. Cost (LKR)", "Status");
                List<UserSummaryDto> list = getUserReports(departmentFilter, null, null);
                long sumP = 0, sumC = 0, sumS = 0, sumT = 0;
                double sumCost = 0;
                for (UserSummaryDto u : list) {
                    sumP += u.printPages(); sumC += u.copyPages(); sumS += u.scanPages(); sumT += u.totalPages(); sumCost += u.estimatedCostLkr();
                    rows.add(List.of(nvl(u.username()), nvl(u.displayName()), nvl(u.department()), nvl(u.costCenter()),
                            u.printPages(), u.copyPages(), u.scanPages(), u.totalPages(), u.estimatedCostLkr(),
                            u.isActive() ? "ACTIVE" : "DISABLED"));
                }
                kpis = List.of(
                        kpi("Active Users", list.size(), "Directory accounts"),
                        kpi("Total Prints + Copies", sumT, "Print: " + sumP + " | Copy: " + sumC),
                        kpi("Digital Scans", sumS, "Scan-to-Email docs"),
                        kpi("Est. Cost (LKR)", roundCost(sumCost), "@ LKR 3.00 / page")
                );
                footer = List.of("TOTAL (" + list.size() + " Users)", "", "", "", sumP, sumC, sumS, sumT, roundCost(sumCost), "");
            }
            case "user-history" -> {
                code = "RPT-02";
                category = "Users & Cost Centers";
                title = "Detailed Transaction History";
                description = "Chronological ISO 8601 audit ledger of all print releases, walk-up copies, and scan relays.";
                columns = List.of("Timestamp (ISO)", "Type", "Username", "Department", "Printer", "Document / Operation", "Pages", "Mono", "Color", "Est. Cost (LKR)", "Status");
                List<TransactionReportDto> txs = getTransactionReports(null, departmentFilter, null, printerFilter, from, to, null);
                long sumP = 0, sumM = 0, sumCol = 0;
                double sumCost = 0;
                for (TransactionReportDto t : txs) {
                    sumP += t.pages(); sumM += t.monoPages(); sumCol += t.colorPages(); sumCost += t.estimatedCostLkr();
                    rows.add(List.of(t.timestamp(), t.type(), nvl(t.username()), nvl(t.department()), nvl(t.printerName()),
                            nvl(t.documentName()), t.pages(), t.monoPages(), t.colorPages(), t.estimatedCostLkr(), nvl(t.status())));
                }
                kpis = List.of(
                        kpi("Transactions", txs.size(), "Print, Copy & Scan events"),
                        kpi("Total Pages", sumP, "Mono: " + sumM + " | Color: " + sumCol),
                        kpi("Est. Cost (LKR)", roundCost(sumCost), "Filtered period"),
                        kpi("Period", (from != null && !from.isBlank() ? from : "Start") + " .. " + (to != null && !to.isBlank() ? to : "Now"), "ISO 8601 range")
                );
                footer = List.of("TOTAL (" + txs.size() + " Events)", "", "", "", "", "", sumP, sumM, sumCol, roundCost(sumCost), "");
            }
            case "department-summary" -> {
                code = "RPT-03";
                category = "Users & Cost Centers";
                title = "Department Usage & Cost Allocation";
                description = "Page volume share and cost attribution grouped by organizational department.";
                columns = List.of("Department", "Users", "Printers", "Print Pages", "Copy Pages", "Total Pages", "Fleet Share %", "Est. Cost (LKR)");
                List<DepartmentReportDto> depts = getDepartmentReports();
                long sumU = 0, sumPrn = 0, sumP = 0, sumC = 0, sumT = 0;
                double sumCost = 0;
                for (DepartmentReportDto d : depts) {
                    if (departmentFilter != null && !departmentFilter.isBlank() && !"ALL".equalsIgnoreCase(departmentFilter)
                            && !d.department().equalsIgnoreCase(departmentFilter.trim())) continue;
                    sumU += d.userCount(); sumPrn += d.printerCount(); sumP += d.printPages(); sumC += d.copyPages();
                    sumT += d.totalPages(); sumCost += d.estimatedCostLkr();
                    rows.add(List.of(d.department(), d.userCount(), d.printerCount(), d.printPages(), d.copyPages(),
                            d.totalPages(), d.percentOfTotal(), d.estimatedCostLkr()));
                }
                kpis = List.of(
                        kpi("Departments", rows.size(), "Active units"),
                        kpi("Total Users", sumU, "Across departments"),
                        kpi("Total Volume", sumT, "Print: " + sumP + " | Copy: " + sumC),
                        kpi("Est. Cost (LKR)", roundCost(sumCost), "Total allocation")
                );
                footer = List.of("TOTAL (" + rows.size() + " Depts)", sumU, sumPrn, sumP, sumC, sumT, 100.0, roundCost(sumCost));
            }
            case "top-users" -> {
                code = "RPT-04";
                category = "Users & Cost Centers";
                title = "Top 10 Heavy Users";
                description = "Highest-volume print, copy, and scan users ranked by total page activity.";
                columns = List.of("Rank", "Username", "Display Name", "Department", "Cost Center", "Print Pages", "Copy Pages", "Scan Docs", "Total Pages", "Est. Cost (LKR)");
                List<UserSummaryDto> sorted = getUserReports(departmentFilter, null, null).stream().limit(10).toList();
                int r = 1;
                long sumP = 0, sumC = 0, sumS = 0, sumT = 0;
                double sumCost = 0;
                for (UserSummaryDto u : sorted) {
                    sumP += u.printPages(); sumC += u.copyPages(); sumS += u.scanPages(); sumT += u.totalPages(); sumCost += u.estimatedCostLkr();
                    rows.add(List.of(r++, u.username(), u.displayName(), u.department(), u.costCenter(),
                            u.printPages(), u.copyPages(), u.scanPages(), u.totalPages(), u.estimatedCostLkr()));
                }
                String topName = !sorted.isEmpty() ? sorted.get(0).displayName() : "None";
                long topVol = !sorted.isEmpty() ? sorted.get(0).totalPages() : 0;
                kpis = List.of(
                        kpi("#1 Top User", topName, topVol + " total pages"),
                        kpi("Top 10 Prints", sumP, "Print pages"),
                        kpi("Top 10 Copies", sumC, "Copy pages"),
                        kpi("Top 10 Cost (LKR)", roundCost(sumCost), "Estimated spend")
                );
                footer = List.of("TOP " + sorted.size(), "", "", "", "", sumP, sumC, sumS, sumT, roundCost(sumCost));
            }
            case "inactive-users" -> {
                code = "RPT-05";
                category = "Users & Cost Centers";
                title = "Zero-Usage / Inactive Users";
                description = "Directory accounts with zero recorded print, copy, or scan output.";
                columns = List.of("Username", "Display Name", "Department", "Cost Center", "Badge ID", "Status");
                List<UserSummaryDto> allU = getUserReports(departmentFilter, null, null);
                int activeWithUsage = 0;
                for (UserSummaryDto u : allU) {
                    if (u.totalPages() + u.scanPages() == 0) {
                        rows.add(List.of(u.username(), u.displayName(), u.department(), u.costCenter(),
                                nvl(u.badgeId()), u.isActive() ? "ACTIVE" : "DISABLED"));
                    } else {
                        activeWithUsage++;
                    }
                }
                kpis = List.of(
                        kpi("Inactive Users", rows.size(), "Zero page output"),
                        kpi("Active Users", activeWithUsage, "With recorded usage"),
                        kpi("Total Directory", allU.size(), "Registered accounts"),
                        kpi("Adoption Rate", allU.isEmpty() ? "0%" : Math.round((activeWithUsage * 100.0) / allU.size()) + "%", "Accounts utilizing MFPs")
                );
                footer = List.of("TOTAL INACTIVE: " + rows.size(), "", "", "", "", "");
            }
            case "quota-compliance" -> {
                code = "RPT-06";
                category = "Users & Cost Centers";
                title = "User Quota & Budget Compliance";
                description = "Tracks user consumption against the standard 1,000-page monthly threshold.";
                columns = List.of("Username", "Display Name", "Department", "Used Pages", "Threshold", "Utilization %", "Est. Cost (LKR)", "Compliance");
                int quotaLimit = 1000;
                int exceeded = 0, warning = 0;
                long sumUsed = 0;
                double sumCost = 0;
                for (UserSummaryDto u : getUserReports(departmentFilter, null, null)) {
                    long used = u.printPages() + u.copyPages();
                    double pct = roundCost((used * 100.0) / quotaLimit);
                    String status = used > quotaLimit ? "EXCEEDED" : (used >= 750 ? "WARNING" : "COMPLIANT");
                    if ("EXCEEDED".equals(status)) exceeded++; else if ("WARNING".equals(status)) warning++;
                    sumUsed += used; sumCost += u.estimatedCostLkr();
                    rows.add(List.of(u.username(), u.displayName(), u.department(), used, quotaLimit, pct, u.estimatedCostLkr(), status));
                }
                kpis = List.of(
                        kpi("Total Accounts", rows.size(), "Threshold: " + quotaLimit + " pgs"),
                        kpi("Exceeded Quota", exceeded, "Over 1,000 pages"),
                        kpi("Warning (>=75%)", warning, "Approaching limit"),
                        kpi("Total Pages Used", sumUsed, "Cost: LKR " + roundCost(sumCost))
                );
                footer = List.of("TOTAL (" + rows.size() + " Users)", "", "", sumUsed, quotaLimit, "", roundCost(sumCost), "");
            }
            case "printer-utilization" -> {
                code = "RPT-07";
                category = "Printers & Consumables";
                title = "Printer / MFP Fleet Utilization";
                description = "Print & copy job distribution, hardware share %, and online state per Toshiba MFP.";
                columns = List.of("Code", "Printer Name", "IP Address", "Model", "Department", "Print Jobs", "Print Pages", "Copy Jobs", "Copy Pages", "Total Pages", "Share %", "Est. Cost (LKR)", "Status");
                List<PrinterReportDto> list = getPrinterReports(departmentFilter, null);
                long sumPJ = 0, sumPP = 0, sumCJ = 0, sumCP = 0, sumTP = 0, onlineCnt = 0;
                double sumCost = 0;
                for (PrinterReportDto p : list) {
                    if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter)
                            && !p.name().equalsIgnoreCase(printerFilter.trim()) && !p.ipAddress().equalsIgnoreCase(printerFilter.trim())) continue;
                    sumPJ += p.printJobs(); sumPP += p.printPages(); sumCJ += p.copyJobs(); sumCP += p.copyPages();
                    sumTP += p.totalPages(); sumCost += p.estimatedCostLkr();
                    if ("ONLINE".equalsIgnoreCase(p.status())) onlineCnt++;
                    rows.add(List.of(nvl(p.printerCode()), nvl(p.name()), nvl(p.ipAddress()), nvl(p.model()), nvl(p.department()),
                            p.printJobs(), p.printPages(), p.copyJobs(), p.copyPages(), p.totalPages(), p.percentOfTotal(), p.estimatedCostLkr(), nvl(p.status())));
                }
                kpis = List.of(
                        kpi("Managed MFPs", rows.size(), onlineCnt + " online"),
                        kpi("Print Pages", sumPP, sumPJ + " print jobs"),
                        kpi("Copy Pages", sumCP, sumCJ + " copy jobs"),
                        kpi("Fleet Cost (LKR)", roundCost(sumCost), sumTP + " total pages")
                );
                footer = List.of("TOTAL (" + rows.size() + " Printers)", "", "", "", "", sumPJ, sumPP, sumCJ, sumCP, sumTP, 100.0, roundCost(sumCost), "");
            }
            case "series-comparison" -> {
                code = "RPT-08";
                category = "Printers & Consumables";
                title = "MFP Series Comparison (08 vs 18 vs 28)";
                description = "Workload and availability breakdown across Toshiba e-STUDIO hardware generations.";
                columns = List.of("Series", "Devices", "Online", "Print Jobs", "Print Pages", "Copy Jobs", "Copy Pages", "Total Pages", "Est. Cost (LKR)");
                Map<String, String> seriesByCode = new HashMap<>();
                for (PrinterEntity pe : printers) {
                    seriesByCode.put(pe.getPrinterCode(), (pe.getSeriesName() == null || pe.getSeriesName().isBlank()) ? "Other" : pe.getSeriesName());
                }
                Map<String, long[]> serMap = new LinkedHashMap<>();
                for (PrinterReportDto p : getPrinterReports(departmentFilter, null)) {
                    String s = seriesByCode.getOrDefault(p.printerCode(), "Other");
                    long[] arr = serMap.computeIfAbsent(s, k -> new long[7]);
                    arr[0]++;
                    if ("ONLINE".equalsIgnoreCase(p.status())) arr[1]++;
                    arr[2] += p.printJobs(); arr[3] += p.printPages();
                    arr[4] += p.copyJobs(); arr[5] += p.copyPages();
                    arr[6] += p.totalPages();
                }
                long sDev = 0, sOn = 0, sPJ = 0, sPP = 0, sCJ = 0, sCP = 0, sTP = 0;
                for (Map.Entry<String, long[]> e : serMap.entrySet()) {
                    long[] a = e.getValue();
                    double c = roundCost(a[6] * DEFAULT_PAGE_RATE_LKR);
                    sDev += a[0]; sOn += a[1]; sPJ += a[2]; sPP += a[3]; sCJ += a[4]; sCP += a[5]; sTP += a[6];
                    rows.add(List.of(e.getKey(), a[0], a[1], a[2], a[3], a[4], a[5], a[6], c));
                }
                kpis = List.of(
                        kpi("Hardware Series", rows.size(), "Active generations"),
                        kpi("Online / Total", sOn + " / " + sDev, "Fleet readiness"),
                        kpi("Total Pages", sTP, "Print + Copy"),
                        kpi("Est. Cost (LKR)", roundCost(sTP * DEFAULT_PAGE_RATE_LKR), "Across all series")
                );
                footer = List.of("TOTAL", sDev, sOn, sPJ, sPP, sCJ, sCP, sTP, roundCost(sTP * DEFAULT_PAGE_RATE_LKR));
            }
            case "toner-forecast" -> {
                code = "RPT-09";
                category = "Printers & Consumables";
                title = "Live SNMP Toner & Consumables Forecast";
                description = "Real-time RFC 3805 toner levels, engine status, and active hardware alerts per MFP.";
                columns = List.of("Code", "Printer Name", "IP Address", "Black Toner %", "Engine Status", "Active Alerts", "Supply Forecast");
                List<PrinterMonitoringDto> liveList = Collections.emptyList();
                if (snmpMonitoringService != null) {
                    try {
                        FleetMonitoringSummaryDto fleet = snmpMonitoringService.getFleetMonitoring(false, printers.size());
                        if (fleet != null && fleet.getPrinters() != null) liveList = fleet.getPrinters();
                    } catch (Exception ignored) {}
                }
                int lowCnt = 0, offlineCnt = 0;
                if (!liveList.isEmpty()) {
                    for (PrinterMonitoringDto m : liveList) {
                        if (printerFilter != null && !printerFilter.isBlank() && !"ALL".equalsIgnoreCase(printerFilter)
                                && !nvl(m.getName()).equalsIgnoreCase(printerFilter.trim()) && !nvl(m.getIpAddress()).equalsIgnoreCase(printerFilter.trim())) continue;
                        int pct = -1;
                        if (m.getToners() != null && !m.getToners().isEmpty()) {
                            pct = m.getToners().get(0).getLevelPercent();
                        }
                        String alertText = (m.getAlerts() != null && !m.getAlerts().isEmpty())
                                ? String.join("; ", m.getAlerts()) : "None";
                        String forecast;
                        if ("OFFLINE".equalsIgnoreCase(m.getStatus())) {
                            forecast = "OFFLINE"; offlineCnt++;
                        } else if (pct >= 0 && pct <= 15) {
                            forecast = "REPLACE SOON"; lowCnt++;
                        } else if (pct >= 0 && pct <= 30) {
                            forecast = "LOW TONER"; lowCnt++;
                        } else {
                            forecast = "OK";
                        }
                        rows.add(List.of(nvl(m.getPrinterCode()), nvl(m.getName()), nvl(m.getIpAddress()),
                                pct >= 0 ? pct : "N/A", nvl(m.getStatus()), alertText, forecast));
                    }
                } else {
                    for (PrinterEntity p : printers) {
                        rows.add(List.of(nvl(p.getPrinterCode()), nvl(p.getName()), nvl(p.getIpAddress()), "N/A", nvl(p.getStatus()), "SNMP unavailable", nvl(p.getStatus())));
                    }
                }
                kpis = List.of(
                        kpi("Monitored MFPs", rows.size(), "RFC 3805 SNMP"),
                        kpi("Online MFPs", rows.size() - offlineCnt, "Reporting telemetry"),
                        kpi("Low / Replace Toner", lowCnt, "<= 30% remaining"),
                        kpi("Offline / Unreachable", offlineCnt, "Check network")
                );
                footer = List.of("TOTAL (" + rows.size() + " Devices)", "", "", "", "", "", "");
            }
            case "paper-errors" -> {
                code = "RPT-10";
                category = "Printers & Consumables";
                title = "Paper Empty & Hardware Error Incidents";
                description = "Blocked releases, paper-empty warnings, and device errors recorded across print jobs and audit logs.";
                columns = List.of("Timestamp (ISO)", "Source", "User", "Device / Document", "Error Details", "Severity");
                for (PrintJob j : filteredPrint) {
                    if (j.getErrorMessage() != null && !j.getErrorMessage().isBlank()) {
                        rows.add(List.of(fmtTs(j.getSubmittedAt()), "PRINT JOB", nvl(j.getUsername()), nvl(j.getDocumentName()), j.getErrorMessage(), "WARNING"));
                    }
                }
                if (auditLogRepository != null) {
                    for (com.rr.secureprint.entity.AuditLog a : auditLogRepository.findTop100ByOrderByTimestampDesc()) {
                        Instant ts = a.getTimestamp();
                        if (fromInst != null && ts.isBefore(fromInst)) continue;
                        if (toInst != null && ts.isAfter(toInst)) continue;
                        String ev = nvl(a.getEventType());
                        if (ev.contains("BLOCKED") || ev.contains("FAILED") || "FAILURE".equalsIgnoreCase(a.getStatus()) || "WARNING".equalsIgnoreCase(a.getStatus())) {
                            rows.add(List.of(fmtTs(ts), ev, nvl(a.getUsername()), nvl(a.getPrinterCode()), nvl(a.getActionDetails()), nvl(a.getStatus())));
                        }
                    }
                }
                kpis = List.of(
                        kpi("Total Incidents", rows.size(), "Blocked & failed events"),
                        kpi("Job Errors", filteredPrint.stream().filter(j -> j.getErrorMessage() != null && !j.getErrorMessage().isBlank()).count(), "Print release alerts"),
                        kpi("Status", rows.isEmpty() ? "Healthy" : "Attention", "Hardware readiness"),
                        kpi("Standard", "RFC 3805", "SNMP pre-release guard")
                );
                footer = List.of("TOTAL (" + rows.size() + " Incidents)", "", "", "", "", "");
            }
            case "paper-size" -> {
                code = "RPT-11";
                category = "Paper & Environment";
                title = "Paper Size Breakdown (A4 / A3)";
                description = "Volume split between standard A4/smaller output and A3 large-format weighted jobs.";
                columns = List.of("Paper Format", "Jobs", "Weighted Pages", "Share %", "Est. Cost (LKR)");
                long a4Jobs = 0, a4Pages = 0, a3Jobs = 0, a3Pages = 0;
                for (PrintJob j : filteredPrint) {
                    int p = j.getPageCount() * Math.max(1, j.getCopies());
                    String docLower = nvl(j.getDocumentName()).toLowerCase();
                    if (docLower.contains("a3") || (j.getErrorMessage() != null && j.getErrorMessage().toLowerCase().contains("a3"))) {
                        a3Jobs++; a3Pages += p * 2L;
                    } else {
                        a4Jobs++; a4Pages += p;
                    }
                }
                for (CopyJobEntity c : filteredCopy) {
                    a4Jobs++; a4Pages += c.getPages();
                }
                long totJ = a4Jobs + a3Jobs;
                long totP = a4Pages + a3Pages;
                rows.add(List.of("A4 / Standard (1x)", a4Jobs, a4Pages, totP > 0 ? roundCost((a4Pages * 100.0) / totP) : 0.0, roundCost(a4Pages * DEFAULT_PAGE_RATE_LKR)));
                rows.add(List.of("A3 / Large Format (2x)", a3Jobs, a3Pages, totP > 0 ? roundCost((a3Pages * 100.0) / totP) : 0.0, roundCost(a3Pages * DEFAULT_PAGE_RATE_LKR)));
                kpis = List.of(
                        kpi("Total Jobs", totJ, "Print + Copy"),
                        kpi("A4 / Standard Pages", a4Pages, a4Jobs + " jobs"),
                        kpi("A3 Pages (2x)", a3Pages, a3Jobs + " jobs"),
                        kpi("Est. Cost (LKR)", roundCost(totP * DEFAULT_PAGE_RATE_LKR), "Weighted billing")
                );
                footer = List.of("TOTAL", totJ, totP, 100.0, roundCost(totP * DEFAULT_PAGE_RATE_LKR));
            }
            case "duplex-ratio" -> {
                code = "RPT-12";
                category = "Paper & Environment";
                title = "Simplex vs Duplex (Double-Sided) Ratio";
                description = "Measures double-sided printing adoption and physical paper sheets conserved.";
                columns = List.of("Print Mode", "Jobs", "Logical Pages", "Physical Sheets", "Sheets Saved", "Share %");
                long dupJobs = 0, simJobs = 0, dupPages = 0, simPages = 0;
                for (PrintJob j : filteredPrint) {
                    int p = j.getPageCount() * Math.max(1, j.getCopies());
                    if (j.isDuplex()) { dupJobs++; dupPages += p; }
                    else { simJobs++; simPages += p; }
                }
                long dupSheets = (dupPages + 1) / 2;
                long saved = Math.max(0, dupPages - dupSheets);
                long totP = dupPages + simPages;
                rows.add(List.of("Simplex (Single-Sided)", simJobs, simPages, simPages, 0L, totP > 0 ? roundCost((simPages * 100.0) / totP) : 0.0));
                rows.add(List.of("Duplex (Double-Sided)", dupJobs, dupPages, dupSheets, saved, totP > 0 ? roundCost((dupPages * 100.0) / totP) : 0.0));
                kpis = List.of(
                        kpi("Duplex Adoption", totP > 0 ? roundCost((dupPages * 100.0) / totP) + "%" : "0%", dupJobs + " duplex jobs"),
                        kpi("Logical Pages", totP, "All filtered print jobs"),
                        kpi("Physical Sheets", simPages + dupSheets, "Actual paper used"),
                        kpi("Sheets Saved", saved, "Conserved via duplex")
                );
                footer = List.of("TOTAL", simJobs + dupJobs, totP, simPages + dupSheets, saved, 100.0);
            }
            case "color-mono" -> {
                code = "RPT-13";
                category = "Paper & Environment";
                title = "Color vs Mono (B&W) Usage";
                description = "Breakdown of monochrome and color pages across Pull-Print and Walk-Up Copy operations.";
                columns = List.of("Color Mode", "Print Pages", "Copy Pages", "Total Pages", "Share %", "Est. Cost (LKR)");
                long pMono = 0, pCol = 0, cMono = 0, cCol = 0;
                for (PrintJob j : filteredPrint) {
                    int p = j.getPageCount() * Math.max(1, j.getCopies());
                    if (j.isColor()) pCol += p; else pMono += p;
                }
                for (CopyJobEntity c : filteredCopy) {
                    cMono += c.getMonoPages(); cCol += c.getColorPages();
                }
                long totMono = pMono + cMono;
                long totCol = pCol + cCol;
                long tot = totMono + totCol;
                rows.add(List.of("Monochrome (B&W)", pMono, cMono, totMono, tot > 0 ? roundCost((totMono * 100.0) / tot) : 0.0, roundCost(totMono * DEFAULT_PAGE_RATE_LKR)));
                rows.add(List.of("Full Color", pCol, cCol, totCol, tot > 0 ? roundCost((totCol * 100.0) / tot) : 0.0, roundCost(totCol * DEFAULT_PAGE_RATE_LKR)));
                kpis = List.of(
                        kpi("Mono Pages", totMono, (tot > 0 ? roundCost((totMono * 100.0) / tot) : 0.0) + "% of fleet"),
                        kpi("Color Pages", totCol, (tot > 0 ? roundCost((totCol * 100.0) / tot) : 0.0) + "% of fleet"),
                        kpi("Total Volume", tot, "Print + Copy"),
                        kpi("Est. Cost (LKR)", roundCost(tot * DEFAULT_PAGE_RATE_LKR), "Combined baseline")
                );
                footer = List.of("TOTAL", pMono + pCol, cMono + cCol, tot, 100.0, roundCost(tot * DEFAULT_PAGE_RATE_LKR));
            }
            case "waste-expired" -> {
                code = "RPT-14";
                category = "Paper & Environment";
                title = "Waste Reduction / Expired Jobs (60m Policy)";
                description = "Unreleased held jobs automatically purged after timeout, preventing forgotten paper waste.";
                columns = List.of("Submitted (ISO)", "Username", "Document Name", "Pages Saved", "Cost Saved (LKR)", "Status / Reason");
                long savedPages = 0;
                for (PrintJob j : filteredPrint) {
                    boolean expired = j.getStatus() == com.rr.secureprint.entity.JobStatus.EXPIRED
                            || (j.getErrorMessage() != null && j.getErrorMessage().contains("Auto-deleted"));
                    if (expired) {
                        int p = j.getPageCount() * Math.max(1, j.getCopies());
                        savedPages += p;
                        rows.add(List.of(fmtTs(j.getSubmittedAt()), nvl(j.getUsername()), nvl(j.getDocumentName()),
                                p, roundCost(p * DEFAULT_PAGE_RATE_LKR), nvl(j.getErrorMessage())));
                    }
                }
                kpis = List.of(
                        kpi("Expired Jobs", rows.size(), "Never printed & purged"),
                        kpi("Pages Saved", savedPages, "Zero paper wasted"),
                        kpi("Cost Avoided (LKR)", roundCost(savedPages * DEFAULT_PAGE_RATE_LKR), "Toner & paper savings"),
                        kpi("Trees Conserved", BigDecimal.valueOf(savedPages / 8333.0).setScale(3, RoundingMode.HALF_UP), "ESG conservation")
                );
                footer = List.of("TOTAL (" + rows.size() + " Jobs)", "", "", savedPages, roundCost(savedPages * DEFAULT_PAGE_RATE_LKR), "");
            }
            case "user-cancelled" -> {
                code = "RPT-15";
                category = "Paper & Environment";
                title = "User-Cancelled Jobs";
                description = "Print jobs manually deleted by users from the touchscreen queue prior to printing.";
                columns = List.of("Submitted (ISO)", "Username", "Document Name", "Pages Avoided", "Cost Saved (LKR)", "Status");
                long cancelledPages = 0;
                for (PrintJob j : filteredPrint) {
                    if (j.getStatus() == com.rr.secureprint.entity.JobStatus.CANCELLED
                            && (j.getErrorMessage() == null || !j.getErrorMessage().contains("Auto-deleted"))) {
                        int p = j.getPageCount() * Math.max(1, j.getCopies());
                        cancelledPages += p;
                        rows.add(List.of(fmtTs(j.getSubmittedAt()), nvl(j.getUsername()), nvl(j.getDocumentName()),
                                p, roundCost(p * DEFAULT_PAGE_RATE_LKR), "CANCELLED"));
                    }
                }
                kpis = List.of(
                        kpi("Cancelled Jobs", rows.size(), "Deleted at terminal"),
                        kpi("Pages Avoided", cancelledPages, "Mistaken prints stopped"),
                        kpi("Savings (LKR)", roundCost(cancelledPages * DEFAULT_PAGE_RATE_LKR), "@ LKR 3.00 / page"),
                        kpi("Status", "Tracked", "Full user accountability")
                );
                footer = List.of("TOTAL (" + rows.size() + " Jobs)", "", "", cancelledPages, roundCost(cancelledPages * DEFAULT_PAGE_RATE_LKR), "");
            }
            case "daily-trend" -> {
                code = "RPT-16";
                category = "Time Trends & Security";
                title = "Daily Volume Trend (ISO 8601)";
                description = "Day-by-day summary of print, copy, and scan volume formatted in YYYY-MM-DD order.";
                columns = List.of("Date (ISO 8601)", "Print Pages", "Copy Pages", "Scan Docs", "Total Print+Copy", "Est. Cost (LKR)");
                Map<String, long[]> dayMap = new TreeMap<>(Collections.reverseOrder());
                for (PrintJob j : filteredPrint) {
                    String d = isoMonthOrDay(j.getReleasedAt() != null ? j.getReleasedAt() : j.getSubmittedAt(), 10);
                    dayMap.computeIfAbsent(d, k -> new long[3])[0] += j.getPageCount() * Math.max(1L, j.getCopies());
                }
                for (CopyJobEntity c : filteredCopy) {
                    String d = isoMonthOrDay(c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt(), 10);
                    dayMap.computeIfAbsent(d, k -> new long[3])[1] += c.getPages();
                }
                for (ScanJobEntity s : filteredScan) {
                    String d = isoMonthOrDay(s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt(), 10);
                    dayMap.computeIfAbsent(d, k -> new long[3])[2] += Math.max(1, s.getPages());
                }
                long sP = 0, sC = 0, sS = 0, sT = 0;
                for (Map.Entry<String, long[]> e : dayMap.entrySet()) {
                    long[] a = e.getValue();
                    long t = a[0] + a[1];
                    sP += a[0]; sC += a[1]; sS += a[2]; sT += t;
                    rows.add(List.of(e.getKey(), a[0], a[1], a[2], t, roundCost(t * DEFAULT_PAGE_RATE_LKR)));
                }
                kpis = List.of(
                        kpi("Active Days", rows.size(), "Days with activity"),
                        kpi("Avg Daily Volume", rows.isEmpty() ? 0 : Math.round((double) sT / rows.size()), "Pages / day"),
                        kpi("Total Prints + Copies", sT, "Scans: " + sS),
                        kpi("Est. Cost (LKR)", roundCost(sT * DEFAULT_PAGE_RATE_LKR), "Filtered period")
                );
                footer = List.of("TOTAL (" + rows.size() + " Days)", sP, sC, sS, sT, roundCost(sT * DEFAULT_PAGE_RATE_LKR));
            }
            case "monthly-summary" -> {
                code = "RPT-17";
                category = "Time Trends & Security";
                title = "Monthly Executive Summary (ISO 8601)";
                description = "Month-over-month fleet billing and throughput roll-up (YYYY-MM).";
                columns = List.of("Month (ISO 8601)", "Print Pages", "Copy Pages", "Scan Docs", "Total Print+Copy", "Est. Cost (LKR)");
                Map<String, long[]> monMap = new TreeMap<>(Collections.reverseOrder());
                for (PrintJob j : filteredPrint) {
                    String m = isoMonthOrDay(j.getReleasedAt() != null ? j.getReleasedAt() : j.getSubmittedAt(), 7);
                    monMap.computeIfAbsent(m, k -> new long[3])[0] += j.getPageCount() * Math.max(1L, j.getCopies());
                }
                for (CopyJobEntity c : filteredCopy) {
                    String m = isoMonthOrDay(c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt(), 7);
                    monMap.computeIfAbsent(m, k -> new long[3])[1] += c.getPages();
                }
                for (ScanJobEntity s : filteredScan) {
                    String m = isoMonthOrDay(s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt(), 7);
                    monMap.computeIfAbsent(m, k -> new long[3])[2] += Math.max(1, s.getPages());
                }
                long sP = 0, sC = 0, sS = 0, sT = 0;
                for (Map.Entry<String, long[]> e : monMap.entrySet()) {
                    long[] a = e.getValue();
                    long t = a[0] + a[1];
                    sP += a[0]; sC += a[1]; sS += a[2]; sT += t;
                    rows.add(List.of(e.getKey(), a[0], a[1], a[2], t, roundCost(t * DEFAULT_PAGE_RATE_LKR)));
                }
                kpis = List.of(
                        kpi("Months Covered", rows.size(), "YYYY-MM periods"),
                        kpi("Total Prints", sP, "Pages"),
                        kpi("Total Copies", sC, "Pages"),
                        kpi("Est. Cost (LKR)", roundCost(sT * DEFAULT_PAGE_RATE_LKR), "Executive total")
                );
                footer = List.of("TOTAL (" + rows.size() + " Months)", sP, sC, sS, sT, roundCost(sT * DEFAULT_PAGE_RATE_LKR));
            }
            case "peak-hours" -> {
                code = "RPT-18";
                category = "Time Trends & Security";
                title = "Peak Hour Activity (24h ISO Clock)";
                description = "Identifies busiest office hours across print, copy, and scan operations.";
                columns = List.of("Hour Slot (24h)", "Print Jobs", "Copy Jobs", "Scan Jobs", "Total Pages", "Load Level");
                Map<Integer, long[]> hrMap = new TreeMap<>();
                for (PrintJob j : filteredPrint) {
                    Instant t = j.getReleasedAt() != null ? j.getReleasedAt() : j.getSubmittedAt();
                    if (t != null) {
                        int h = t.atZone(ZoneId.systemDefault()).getHour();
                        long[] a = hrMap.computeIfAbsent(h, k -> new long[4]);
                        a[0]++; a[3] += j.getPageCount() * Math.max(1L, j.getCopies());
                    }
                }
                for (CopyJobEntity c : filteredCopy) {
                    Instant t = c.getCompletedAt() != null ? c.getCompletedAt() : c.getCreatedAt();
                    if (t != null) {
                        int h = t.atZone(ZoneId.systemDefault()).getHour();
                        long[] a = hrMap.computeIfAbsent(h, k -> new long[4]);
                        a[1]++; a[3] += c.getPages();
                    }
                }
                for (ScanJobEntity s : filteredScan) {
                    Instant t = s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt();
                    if (t != null) {
                        int h = t.atZone(ZoneId.systemDefault()).getHour();
                        long[] a = hrMap.computeIfAbsent(h, k -> new long[4]);
                        a[2]++; a[3] += Math.max(1, s.getPages());
                    }
                }
                int peakHour = -1; long peakPages = 0; long sPJ = 0, sCJ = 0, sSJ = 0, sTP = 0;
                for (Map.Entry<Integer, long[]> e : hrMap.entrySet()) {
                    if (e.getValue()[3] > peakPages) { peakPages = e.getValue()[3]; peakHour = e.getKey(); }
                }
                for (Map.Entry<Integer, long[]> e : hrMap.entrySet()) {
                    long[] a = e.getValue();
                    String lvl = (peakPages > 0 && a[3] >= peakPages * 0.7) ? "PEAK" : (a[3] >= peakPages * 0.35 ? "MODERATE" : "LOW");
                    sPJ += a[0]; sCJ += a[1]; sSJ += a[2]; sTP += a[3];
                    rows.add(List.of(String.format("%02d:00 - %02d:59", e.getKey(), e.getKey()), a[0], a[1], a[2], a[3], lvl));
                }
                kpis = List.of(
                        kpi("Peak Hour", peakHour >= 0 ? String.format("%02d:00", peakHour) : "N/A", peakPages + " pages"),
                        kpi("Total Print Jobs", sPJ, "By hour"),
                        kpi("Total Copy Jobs", sCJ, "By hour"),
                        kpi("Total Scan Jobs", sSJ, sTP + " combined pages")
                );
                footer = List.of("TOTAL", sPJ, sCJ, sSJ, sTP, "");
            }
            case "scan-activity" -> {
                code = "RPT-19";
                category = "Time Trends & Security";
                title = "Scan-to-Email Activity & Relay Log";
                description = "Full digital document relay history with recipient addresses and SMTP status.";
                columns = List.of("Timestamp (ISO)", "Username", "Department", "Source MFP", "Recipient Email", "File Name", "Pages", "Mode", "Delivery Status");
                List<ScanReportDto> scans = getScanReports(departmentFilter, null, printerFilter, from, to, null);
                long sumPages = 0, sentCnt = 0;
                for (ScanReportDto s : scans) {
                    sumPages += s.pages();
                    if ("SENT".equalsIgnoreCase(s.status()) || "DELIVERED".equalsIgnoreCase(s.status())) sentCnt++;
                    rows.add(List.of(s.timestamp(), nvl(s.username()), nvl(s.department()), nvl(s.printerName()),
                            nvl(s.recipientEmail()), nvl(s.fileName()), s.pages(), nvl(s.colorMode()), nvl(s.status())));
                }
                kpis = List.of(
                        kpi("Total Scans", scans.size(), "Digital captures"),
                        kpi("Delivered", sentCnt, "Successfully relayed"),
                        kpi("Total Scanned Pages", sumPages, "Paperless pages"),
                        kpi("Delivery Rate", scans.isEmpty() ? "100%" : Math.round((sentCnt * 100.0) / scans.size()) + "%", "SMTP relay gateway")
                );
                footer = List.of("TOTAL (" + scans.size() + " Scans)", "", "", "", "", "", sumPages, "", "");
            }
            case "security-audit" -> {
                code = "RPT-20";
                category = "Time Trends & Security";
                title = "Security & Auto-Lock Audit Trail";
                description = "Authentication events, device auto-locks, job expiries, and admin configuration audits.";
                columns = List.of("Timestamp (ISO)", "Event Type", "User", "Device Code", "IP Address", "Action Details", "Status");
                int lockCnt = 0, failCnt = 0;
                if (auditLogRepository != null) {
                    List<com.rr.secureprint.entity.AuditLog> logs = auditLogRepository
                            .findAllByOrderByTimestampDesc(org.springframework.data.domain.PageRequest.of(0, 200))
                            .getContent();
                    for (com.rr.secureprint.entity.AuditLog a : logs) {
                        Instant ts = a.getTimestamp();
                        if (fromInst != null && ts.isBefore(fromInst)) continue;
                        if (toInst != null && ts.isAfter(toInst)) continue;
                        String ev = nvl(a.getEventType());
                        if (ev.contains("LOCK")) lockCnt++;
                        if ("FAILURE".equalsIgnoreCase(a.getStatus()) || "WARNING".equalsIgnoreCase(a.getStatus())) failCnt++;
                        rows.add(List.of(fmtTs(ts), ev, nvl(a.getUsername()), nvl(a.getPrinterCode()),
                                nvl(a.getIpAddress()), nvl(a.getActionDetails()), nvl(a.getStatus())));
                    }
                }
                kpis = List.of(
                        kpi("Audit Events", rows.size(), "Latest 200 filtered"),
                        kpi("Device Auto-Locks", lockCnt, "Sleep & idle protection"),
                        kpi("Warnings / Failures", failCnt, "Requires attention"),
                        kpi("Compliance", "ISO 27001 / 9241", "Tamper-evident trail")
                );
                footer = List.of("TOTAL (" + rows.size() + " Events)", "", "", "", "", "", "");
            }
            case "user-printer-matrix" -> {
                code = "RPT-21";
                category = "Users & Cost Centers";
                title = "User x Printer Copy & Print Breakdown";
                description = "Shows which printers each user used and exact print/copy/scan volumes per device.";
                columns = List.of("Username", "Department", "Printer Name", "Printer IP", "Print Jobs", "Print Pages", "Copy Jobs", "Copy Pages", "Scan Docs", "Total Pages", "Est. Cost (LKR)");
                Map<String, long[]> mat = new LinkedHashMap<>();
                Map<String, String[]> matMeta = new LinkedHashMap<>();
                for (PrintJob j : filteredPrint) {
                    String u = nvl(j.getUsername()).isBlank() ? "unknown" : j.getUsername();
                    String d = (j.getUser() != null && j.getUser().getDepartment() != null) ? j.getUser().getDepartment() : "Operations";
                    String pName = j.getTargetPrinter() != null ? nvl(j.getTargetPrinter().getName()) : "Virtual Queue";
                    String pIp = j.getTargetPrinter() != null ? nvl(j.getTargetPrinter().getIpAddress()) : "-";
                    String key = u + "|" + pName + "|" + pIp;
                    long[] a = mat.computeIfAbsent(key, k -> new long[5]);
                    matMeta.putIfAbsent(key, new String[]{u, d, pName, pIp});
                    a[0]++;
                    a[1] += (long) j.getPageCount() * Math.max(1, j.getCopies());
                }
                for (CopyJobEntity c : filteredCopy) {
                    String u = (c.getUser() != null && c.getUser().getUsername() != null) ? c.getUser().getUsername() : nvl(c.getUsername());
                    if (u.isBlank()) u = "walkup";
                    String d = (c.getUser() != null && c.getUser().getDepartment() != null) ? c.getUser().getDepartment() : "Operations";
                    String pName = c.getPrinterName() != null ? c.getPrinterName() : (c.getPrinter() != null ? nvl(c.getPrinter().getName()) : "Walk-Up MFP");
                    String pIp = c.getPrinterIp() != null ? c.getPrinterIp() : (c.getPrinter() != null ? nvl(c.getPrinter().getIpAddress()) : "-");
                    String key = u + "|" + pName + "|" + pIp;
                    long[] a = mat.computeIfAbsent(key, k -> new long[5]);
                    matMeta.putIfAbsent(key, new String[]{u, d, pName, pIp});
                    a[2]++;
                    a[3] += c.getPages();
                }
                for (ScanJobEntity s : filteredScan) {
                    String u = (s.getUser() != null && s.getUser().getUsername() != null) ? s.getUser().getUsername() : nvl(s.getUsername());
                    if (u.isBlank()) u = "walkup";
                    String d = (s.getUser() != null && s.getUser().getDepartment() != null) ? s.getUser().getDepartment() : "Operations";
                    String pName = s.getPrinterName() != null ? s.getPrinterName() : (s.getPrinter() != null ? nvl(s.getPrinter().getName()) : "MFP Scanner");
                    String pIp = s.getPrinterIp() != null ? s.getPrinterIp() : (s.getPrinter() != null ? nvl(s.getPrinter().getIpAddress()) : "-");
                    String key = u + "|" + pName + "|" + pIp;
                    long[] a = mat.computeIfAbsent(key, k -> new long[5]);
                    matMeta.putIfAbsent(key, new String[]{u, d, pName, pIp});
                    a[4]++;
                }
                long sPJ = 0, sPP = 0, sCJ = 0, sCP = 0, sSC = 0, sTP = 0;
                for (Map.Entry<String, long[]> e : mat.entrySet()) {
                    String[] m = matMeta.get(e.getKey());
                    long[] a = e.getValue();
                    long tot = a[1] + a[3];
                    sPJ += a[0]; sPP += a[1]; sCJ += a[2]; sCP += a[3]; sSC += a[4]; sTP += tot;
                    rows.add(List.of(m[0], m[1], m[2], m[3], a[0], a[1], a[2], a[3], a[4], tot, roundCost(tot * DEFAULT_PAGE_RATE_LKR)));
                }
                kpis = List.of(
                        kpi("User-Printer Pairs", rows.size(), "Active combinations"),
                        kpi("Total Print Pages", sPP, sPJ + " print jobs"),
                        kpi("Total Copy Pages", sCP, sCJ + " copy jobs"),
                        kpi("Est. Cost (LKR)", roundCost(sTP * DEFAULT_PAGE_RATE_LKR), sTP + " total pages")
                );
                footer = List.of("TOTAL (" + rows.size() + " Pairs)", "", "", "", sPJ, sPP, sCJ, sCP, sSC, sTP, roundCost(sTP * DEFAULT_PAGE_RATE_LKR));
            }
            default -> {
                title = "Unknown Report";
                description = "Selected report type was not recognized.";
                columns = List.of("Info");
                rows.add(List.of("Unknown report type: " + reportType));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("category", category);
        out.put("title", title);
        out.put("description", description);
        out.put("generatedAt", DISPLAY_DATE_FMT.format(Instant.now()));
        out.put("kpis", kpis);
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("footer", footer);
        return out;
    }

    public Map<String, Object> resetCounters(String scope, String target) {
        String sc = scope != null ? scope.trim().toUpperCase() : "";
        String tgt = target != null ? target.trim() : "";
        int usersReset = 0;
        int jobsZeroed = 0;

        if ("USER".equals(sc) && !tgt.isBlank()) {
            for (UserEntity u : userRepository.findAll()) {
                if (u.getUsername() != null && u.getUsername().equalsIgnoreCase(tgt)) {
                    u.setPrintCount(0L);
                    u.setCopyCount(0L);
                    u.setScanCount(0L);
                    userRepository.save(u);
                    usersReset++;
                }
            }
        } else if ("DEPARTMENT".equals(sc) && !tgt.isBlank()) {
            for (UserEntity u : userRepository.findAll()) {
                String dept = u.getDepartment() != null ? u.getDepartment() : "Operations";
                if (dept.equalsIgnoreCase(tgt)) {
                    u.setPrintCount(0L);
                    u.setCopyCount(0L);
                    u.setScanCount(0L);
                    userRepository.save(u);
                    usersReset++;
                }
            }
        } else if ("PRINTER".equals(sc) && !tgt.isBlank()) {
            for (PrintJob j : printJobRepository.findAll()) {
                String pName = j.getTargetPrinter() != null ? nvl(j.getTargetPrinter().getName()) : "";
                String pCode = j.getTargetPrinter() != null ? nvl(j.getTargetPrinter().getPrinterCode()) : "";
                String pIp = j.getTargetPrinter() != null ? nvl(j.getTargetPrinter().getIpAddress()) : "";
                if (pName.equalsIgnoreCase(tgt) || pCode.equalsIgnoreCase(tgt) || pIp.equalsIgnoreCase(tgt)) {
                    if (j.getPageCount() != 0) { j.setPageCount(0); printJobRepository.save(j); jobsZeroed++; }
                }
            }
            for (CopyJobEntity c : copyJobRepository.findAll()) {
                String pName = c.getPrinterName() != null ? c.getPrinterName() : (c.getPrinter() != null ? nvl(c.getPrinter().getName()) : "");
                String pIp = c.getPrinterIp() != null ? c.getPrinterIp() : (c.getPrinter() != null ? nvl(c.getPrinter().getIpAddress()) : "");
                if (pName.equalsIgnoreCase(tgt) || pIp.equalsIgnoreCase(tgt)) {
                    if (c.getPages() != 0) { c.setPages(0); c.setMonoPages(0); c.setColorPages(0); copyJobRepository.save(c); jobsZeroed++; }
                }
            }
        } else if ("ALL".equals(sc)) {
            for (UserEntity u : userRepository.findAll()) {
                u.setPrintCount(0L);
                u.setCopyCount(0L);
                u.setScanCount(0L);
                userRepository.save(u);
                usersReset++;
            }
            for (PrintJob j : printJobRepository.findAll()) {
                if (j.getPageCount() != 0) { j.setPageCount(0); printJobRepository.save(j); jobsZeroed++; }
            }
            for (CopyJobEntity c : copyJobRepository.findAll()) {
                if (c.getPages() != 0) { c.setPages(0); c.setMonoPages(0); c.setColorPages(0); copyJobRepository.save(c); jobsZeroed++; }
            }
        } else {
            throw new IllegalArgumentException("Invalid counter reset scope or missing target.");
        }

        if (auditLogRepository != null) {
            com.rr.secureprint.entity.AuditLog logEntry = new com.rr.secureprint.entity.AuditLog();
            logEntry.setEventType("COUNTER_RESET");
            logEntry.setUsername("admin");
            logEntry.setActionDetails("Counter reset scope=" + sc + " target=" + (tgt.isBlank() ? "ALL" : tgt) + " (users=" + usersReset + ", jobs=" + jobsZeroed + ")");
            logEntry.setStatus("SUCCESS");
            logEntry.setTimestamp(Instant.now());
            auditLogRepository.save(logEntry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "OK");
        res.put("scope", sc);
        res.put("target", tgt);
        res.put("usersReset", usersReset);
        res.put("jobsZeroed", jobsZeroed);
        return res;
    }

    public Map<String, Object> buildCustomReport(Map<String, Object> req) {
        String groupBy = String.valueOf(req.getOrDefault("groupBy", "USER_PRINTER")).toUpperCase();
        String from = req.get("from") != null ? String.valueOf(req.get("from")) : "";
        String to = req.get("to") != null ? String.valueOf(req.get("to")) : "";
        String dept = req.get("department") != null ? String.valueOf(req.get("department")) : "";
        String printer = req.get("printer") != null ? String.valueOf(req.get("printer")) : "";
        String reportId = switch (groupBy) {
            case "USER" -> "user-summary";
            case "PRINTER" -> "printer-utilization";
            case "DEPARTMENT" -> "department-summary";
            default -> "user-printer-matrix";
        };
        Map<String, Object> out = generateHubReport(reportId, from, to, dept, printer);
        out.put("code", "CUSTOM");
        out.put("category", "Custom Report Builder");
        return out;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
