package com.rr.secureprint.controller;

import com.rr.secureprint.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ReportService.ReportSummaryDto> getSummary() {
        return ResponseEntity.ok(reportService.getSummary());
    }

    @GetMapping("/departments")
    public ResponseEntity<List<ReportService.DepartmentReportDto>> getDepartmentReports() {
        return ResponseEntity.ok(reportService.getDepartmentReports());
    }

    @GetMapping("/cost-centers")
    public ResponseEntity<List<ReportService.CostCenterReportDto>> getCostCenterReports() {
        return ResponseEntity.ok(reportService.getCostCenterReports());
    }

    @GetMapping("/printers")
    public ResponseEntity<List<ReportService.PrinterReportDto>> getPrinterReports(
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(reportService.getPrinterReports(department, search));
    }

    @GetMapping("/users")
    public ResponseEntity<List<ReportService.UserSummaryDto>> getUserReports(
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "costCenter", required = false) String costCenter,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(reportService.getUserReports(department, costCenter, search));
    }

    @GetMapping("/scans")
    public ResponseEntity<List<ReportService.ScanReportDto>> getScanReports(
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "costCenter", required = false) String costCenter,
            @RequestParam(value = "printer", required = false) String printer,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(reportService.getScanReports(department, costCenter, printer, startDate, endDate, search));
    }

    @GetMapping("/esg")
    public ResponseEntity<ReportService.EsgReportDto> getEsgReport(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return ResponseEntity.ok(reportService.getEsgReport(startDate, endDate));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ReportService.TransactionReportDto>> getTransactionReports(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "costCenter", required = false) String costCenter,
            @RequestParam(value = "printer", required = false) String printer,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(reportService.getTransactionReports(type, department, costCenter, printer, startDate, endDate, search));
    }

    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "type", defaultValue = "departments") String type,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "costCenter", required = false) String costCenter,
            @RequestParam(value = "printer", required = false) String printer,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        String csv = reportService.generateCsv(type, department, costCenter, printer, startDate, endDate);
        byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);

        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = "nexPrint_" + type + "_report_" + dateStr + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
