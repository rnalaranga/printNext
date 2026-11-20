package com.rr.secureprint.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public byte[] buildWorkbook(Map<String, Object> req) throws Exception {
        String title = str(req.get("title"), "nexPrint Report");
        String subtitle = str(req.get("subtitle"),
                "Generated: " + ISO_FMT.format(Instant.now()) + " (ISO 8601) | nexPrint Enterprise Secure Print Management");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kpis = req.get("kpis") instanceof List ? (List<Map<String, Object>>) req.get("kpis") : List.of();
        @SuppressWarnings("unchecked")
        List<Object> columns = req.get("columns") instanceof List ? (List<Object>) req.get("columns") : List.of();
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = req.get("rows") instanceof List ? (List<List<Object>>) req.get("rows") : List.of();
        @SuppressWarnings("unchecked")
        List<Object> footer = req.get("footer") instanceof List ? (List<Object>) req.get("footer") : List.of();

        int colCount = Math.max(1, columns.size());

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Report");
            sheet.setDisplayGridlines(true);

            // Colors matching nexPrint Authority Blue & Enterprise Gold theme
            XSSFColor darkBlue = new XSSFColor(new Color(30, 58, 138), null);    // #1E3A8A
            XSSFColor authBlue = new XSSFColor(new Color(29, 78, 216), null);    // #1D4ED8
            XSSFColor goldColor = new XSSFColor(new Color(245, 158, 11), null);  // #F59E0B
            XSSFColor softBlue = new XSSFColor(new Color(239, 246, 255), null);  // #EFF6FF
            XSSFColor zebraBg = new XSSFColor(new Color(248, 250, 252), null);   // #F8FAFC
            XSSFColor borderClr = new XSSFColor(new Color(203, 213, 225), null); // #CBD5E1
            XSSFColor whiteClr = new XSSFColor(new Color(255, 255, 255), null);

            // Banner style (Crisp White background so nexPrint logo blends cleanly)
            XSSFCellStyle bannerStyle = wb.createCellStyle();
            bannerStyle.setFillForegroundColor(whiteClr);
            bannerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bannerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bannerStyle.setAlignment(HorizontalAlignment.LEFT);
            XSSFFont bannerFont = wb.createFont();
            bannerFont.setBold(true);
            bannerFont.setFontHeightInPoints((short) 14);
            bannerFont.setColor(darkBlue);
            bannerFont.setFontName("Calibri");
            bannerStyle.setFont(bannerFont);

            // Gold accent bar
            XSSFCellStyle goldStyle = wb.createCellStyle();
            goldStyle.setFillForegroundColor(goldColor);
            goldStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Subtitle style
            XSSFCellStyle subStyle = wb.createCellStyle();
            subStyle.setFillForegroundColor(softBlue);
            subStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            subStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont subFont = wb.createFont();
            subFont.setBold(true);
            subFont.setFontHeightInPoints((short) 10);
            subFont.setColor(darkBlue);
            subFont.setFontName("Calibri");
            subStyle.setFont(subFont);
            setThinBorders(subStyle, borderClr);

            // Header style
            XSSFCellStyle thStyle = wb.createCellStyle();
            thStyle.setFillForegroundColor(authBlue);
            thStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            thStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            thStyle.setAlignment(HorizontalAlignment.LEFT);
            XSSFFont thFont = wb.createFont();
            thFont.setBold(true);
            thFont.setFontHeightInPoints((short) 10);
            thFont.setColor(whiteClr);
            thFont.setFontName("Calibri");
            thStyle.setFont(thFont);
            setThinBorders(thStyle, darkBlue);

            // Data styles
            XSSFCellStyle tdEven = createDataStyle(wb, whiteClr, borderClr, false, null);
            XSSFCellStyle tdOdd = createDataStyle(wb, zebraBg, borderClr, false, null);
            XSSFCellStyle tdNumIntEven = createDataStyle(wb, whiteClr, borderClr, true, "#,##0");
            XSSFCellStyle tdNumIntOdd = createDataStyle(wb, zebraBg, borderClr, true, "#,##0");
            XSSFCellStyle tdNumDecEven = createDataStyle(wb, whiteClr, borderClr, true, "#,##0.00");
            XSSFCellStyle tdNumDecOdd = createDataStyle(wb, zebraBg, borderClr, true, "#,##0.00");

            // Footer styles
            XSSFCellStyle footTextStyle = wb.createCellStyle();
            footTextStyle.setFillForegroundColor(softBlue);
            footTextStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            footTextStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont footFont = wb.createFont();
            footFont.setBold(true);
            footFont.setFontHeightInPoints((short) 10);
            footFont.setColor(darkBlue);
            footFont.setFontName("Calibri");
            footTextStyle.setFont(footFont);
            setThinBorders(footTextStyle, borderClr);
            footTextStyle.setBorderTop(BorderStyle.MEDIUM);
            footTextStyle.setTopBorderColor(authBlue);

            XSSFCellStyle footNumIntStyle = wb.createCellStyle();
            footNumIntStyle.cloneStyleFrom(footTextStyle);
            footNumIntStyle.setAlignment(HorizontalAlignment.RIGHT);
            footNumIntStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            XSSFCellStyle footNumDecStyle = wb.createCellStyle();
            footNumDecStyle.cloneStyleFrom(footTextStyle);
            footNumDecStyle.setAlignment(HorizontalAlignment.RIGHT);
            footNumDecStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int rowIdx = 0;

            // Row 0: Executive White Title Row (Logo in Col 0, Title in Col 1..N)
            Row bannerRow = sheet.createRow(rowIdx);
            bannerRow.setHeightInPoints(30);
            for (int c = 0; c < colCount; c++) {
                bannerRow.createCell(c).setCellStyle(bannerStyle);
            }
            if (colCount > 1) {
                bannerRow.getCell(1).setCellValue(title);
                if (colCount > 2) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 1, colCount - 1));
                }
            } else {
                bannerRow.getCell(0).setCellValue(title);
            }
            rowIdx++;

            // Row 1: Clean Subtitle / ISO 8601 Timestamp Row (Col 1..N under Title)
            XSSFCellStyle cleanSubStyle = wb.createCellStyle();
            cleanSubStyle.setFillForegroundColor(whiteClr);
            cleanSubStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cleanSubStyle.setVerticalAlignment(VerticalAlignment.TOP);
            XSSFFont cleanSubFont = wb.createFont();
            cleanSubFont.setFontHeightInPoints((short) 9);
            cleanSubFont.setColor(new XSSFColor(new Color(100, 116, 139), null));
            cleanSubFont.setFontName("Calibri");
            cleanSubStyle.setFont(cleanSubFont);

            Row subRow = sheet.createRow(rowIdx);
            subRow.setHeightInPoints(18);
            for (int c = 0; c < colCount; c++) {
                subRow.createCell(c).setCellStyle(cleanSubStyle);
            }
            if (colCount > 1) {
                subRow.getCell(1).setCellValue(subtitle);
                if (colCount > 2) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 1, colCount - 1));
                }
            } else {
                subRow.getCell(0).setCellValue(subtitle);
            }
            embedLogo(wb, sheet);
            rowIdx++;

            // Row 2: Crisp Enterprise Gold Accent Line
            Row goldRow = sheet.createRow(rowIdx);
            goldRow.setHeightInPoints(3);
            for (int c = 0; c < colCount; c++) {
                goldRow.createCell(c).setCellStyle(goldStyle);
            }
            if (colCount > 1) {
                sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, colCount - 1));
            }
            rowIdx++;

            // Optional KPI Strip
            if (!kpis.isEmpty()) {
                StringBuilder kpiTxt = new StringBuilder();
                for (int i = 0; i < kpis.size(); i++) {
                    Map<String, Object> k = kpis.get(i);
                    if (i > 0) kpiTxt.append("   |   ");
                    kpiTxt.append(str(k.get("label"), "")).append(": ").append(k.get("value") != null ? k.get("value") : "-");
                }
                Row kpiRow = sheet.createRow(rowIdx);
                kpiRow.setHeightInPoints(20);
                for (int c = 0; c < colCount; c++) {
                    kpiRow.createCell(c).setCellStyle(tdOdd);
                }
                kpiRow.getCell(0).setCellValue(kpiTxt.toString());
                if (colCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, colCount - 1));
                }
                rowIdx++;
            }

            // Spacer row
            sheet.createRow(rowIdx++).setHeightInPoints(8);

            // Table Header Row
            int headerRowIdx = rowIdx;
            Row headRow = sheet.createRow(rowIdx++);
            headRow.setHeightInPoints(24);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headRow.createCell(c);
                cell.setCellValue(str(columns.get(c), ""));
                cell.setCellStyle(thStyle);
            }
            sheet.createFreezePane(0, headerRowIdx + 1);

            // Data Rows
            for (int r = 0; r < rows.size(); r++) {
                boolean isOdd = (r % 2 == 1);
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.setHeightInPoints(19);
                List<Object> rowVals = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    Object val = c < rowVals.size() ? rowVals.get(c) : "";
                    Cell cell = dataRow.createCell(c);
                    if (val instanceof Number num) {
                        double d = num.doubleValue();
                        cell.setCellValue(d);
                        boolean isDec = (d % 1 != 0);
                        cell.setCellStyle(isDec ? (isOdd ? tdNumDecOdd : tdNumDecEven) : (isOdd ? tdNumIntOdd : tdNumIntEven));
                    } else {
                        cell.setCellValue(val != null ? String.valueOf(val) : "");
                        cell.setCellStyle(isOdd ? tdOdd : tdEven);
                    }
                }
            }

            // Footer Row
            if (!footer.isEmpty()) {
                Row footRow = sheet.createRow(rowIdx);
                footRow.setHeightInPoints(22);
                for (int c = 0; c < columns.size(); c++) {
                    Object fv = c < footer.size() ? footer.get(c) : "";
                    Cell cell = footRow.createCell(c);
                    if (fv instanceof Number num) {
                        double d = num.doubleValue();
                        cell.setCellValue(d);
                        cell.setCellStyle((d % 1 != 0) ? footNumDecStyle : footNumIntStyle);
                    } else {
                        cell.setCellValue(fv != null ? String.valueOf(fv) : "");
                        cell.setCellStyle(footTextStyle);
                    }
                }
            }

            // Auto-size columns cleanly (ensure Col 0 fits embedded logo)
            for (int c = 0; c < colCount; c++) {
                sheet.autoSizeColumn(c);
                int w = sheet.getColumnWidth(c) + 600;
                int minW = (c == 0) ? 5200 : 3400;
                sheet.setColumnWidth(c, Math.min(Math.max(w, minW), 12000));
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void embedLogo(XSSFWorkbook wb, XSSFSheet sheet) {
        try {
            ClassPathResource res = new ClassPathResource("static/img/nexprint-logo.png");
            if (!res.exists()) return;
            byte[] bytes;
            try (InputStream is = res.getInputStream()) {
                bytes = is.readAllBytes();
            }
            int picIdx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = wb.getCreationHelper().createClientAnchor();
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            anchor.setCol1(0);
            anchor.setRow1(0);
            anchor.setDx1(4 * 9525);
            anchor.setDy1(4 * 9525);
            anchor.setCol2(1);
            anchor.setRow2(2);
            anchor.setDx2(-4 * 9525);
            anchor.setDy2(-4 * 9525);
            drawing.createPicture(anchor, picIdx);
        } catch (Exception ignored) {}
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb, XSSFColor bg, XSSFColor borderClr, boolean numeric, String numFmt) {
        XSSFCellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(bg);
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setAlignment(numeric ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Calibri");
        st.setFont(font);
        setThinBorders(st, borderClr);
        if (numFmt != null) {
            st.setDataFormat(wb.createDataFormat().getFormat(numFmt));
        }
        return st;
    }

    private void setThinBorders(XSSFCellStyle st, XSSFColor clr) {
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        st.setTopBorderColor(clr);
        st.setBottomBorderColor(clr);
        st.setLeftBorderColor(clr);
        st.setRightBorderColor(clr);
    }

    private String str(Object o, String def) {
        return o != null ? String.valueOf(o) : def;
    }
}
