package com.rr.secureprint.service;

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
}
