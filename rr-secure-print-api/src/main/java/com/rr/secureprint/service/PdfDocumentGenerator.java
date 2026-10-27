package com.rr.secureprint.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java PDF 1.4 generator.
 * Produces valid, compliant PDF documents for scanned jobs with zero external dependencies.
 */
public class PdfDocumentGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static byte[] generateScannedPdf(String title, String username, String printerName,
                                            String colorMode, String resolution, int pageCount) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int pages = Math.max(1, Math.min(pageCount, 50));
            List<Long> offsets = new ArrayList<>();

            // 1. Header
            write(out, "%PDF-1.4\n%âãÏÓ\n");

            // 2. Catalog (Obj 1)
            offsets.add((long) out.size());
            write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // 3. Pages Node (Obj 2)
            // Kids will be objects 3 to 2 + pages
            StringBuilder kids = new StringBuilder("[");
            for (int i = 0; i < pages; i++) {
                kids.append(3 + i * 2).append(" 0 R ");
            }
            kids.append("]");

            offsets.add((long) out.size());
            write(out, "2 0 obj\n<< /Type /Pages /Kids " + kids.toString().trim() + " /Count " + pages + " >>\nendobj\n");

            // Common Font Object (Obj: 2 + pages * 2 + 1)
            int fontObjNum = 2 + pages * 2 + 1;

            String timestamp = LocalDateTime.now().format(DATE_FMT);

            // Generate Page and Content stream objects for each page
            for (int p = 1; p <= pages; p++) {
                int pageObjNum = 3 + (p - 1) * 2;
                int contentObjNum = pageObjNum + 1;

                // Page Object
                offsets.add((long) out.size());
                write(out, pageObjNum + " 0 obj\n" +
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                        "/Resources << /Font << /F1 " + fontObjNum + " 0 R /F2 " + (fontObjNum + 1) + " 0 R >> >> " +
                        "/Contents " + contentObjNum + " 0 R >>\nendobj\n");

                // Content Stream Object
                String stream = buildPageContentStream(title, username, printerName, colorMode, resolution, p, pages, timestamp);
                byte[] streamBytes = stream.getBytes(StandardCharsets.ISO_8859_1);

                offsets.add((long) out.size());
                write(out, contentObjNum + " 0 obj\n" +
                        "<< /Length " + streamBytes.length + " >>\nstream\n");
                out.write(streamBytes);
                write(out, "\nendstream\nendobj\n");
            }

            // Standard Fonts (Helvetica and Helvetica-Bold)
            offsets.add((long) out.size());
            write(out, fontObjNum + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

            offsets.add((long) out.size());
            write(out, (fontObjNum + 1) + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            // Cross-Reference Table
            long xrefOffset = out.size();
            int totalObjects = (fontObjNum + 1);
            write(out, "xref\n0 " + (totalObjects + 1) + "\n");
            write(out, "0000000000 65535 f \n");
            for (Long offset : offsets) {
                write(out, String.format("%010d 00000 n \n", offset));
            }

            // Trailer
            write(out, "trailer\n<< /Size " + (totalObjects + 1) + " /Root 1 0 R >>\n");
            write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF document", e);
        }
    }

    private static String buildPageContentStream(String title, String username, String printerName,
                                                 String colorMode, String resolution,
                                                 int pageNum, int totalPages, String timestamp) {
        StringBuilder sb = new StringBuilder();

        // Background / Border styling
        sb.append("0.11 0.31 0.85 rg\n"); // #1D4ED8 blue bar at top
        sb.append("36 786 523 20 re f\n");

        sb.append("0.85 0.90 0.96 rg\n"); // light blue fill for metadata box
        sb.append("36 676 523 98 re f\n");

        sb.append("0.75 0.80 0.88 RG 1 w\n"); // border for metadata box
        sb.append("36 676 523 98 re S\n");

        // Top Banner Text
        sb.append("BT\n");
        sb.append("/F2 11 Tf 1 1 1 rg\n");
        sb.append("46 792 Td (RR SECURE PRINT  -  TOSHIBA e-STUDIO MULTIFUNCTION SCANNER) Tj\n");
        sb.append("ET\n");

        // Metadata Header
        sb.append("BT\n");
        sb.append("/F2 14 Tf 0.06 0.09 0.16 rg\n");
        sb.append("50 754 Td (Scanned Document: ").append(escapePdf(title)).append(") Tj\n");

        sb.append("/F1 10 Tf 0.20 0.25 0.33 rg\n");
        sb.append("50 734 Td (User Account: ").append(escapePdf(username != null ? username : "Walk-up User"))
                .append("   |   Station: ").append(escapePdf(printerName != null ? printerName : "Toshiba MFP")).append(") Tj\n");

        sb.append("50 716 Td (Scanned At: ").append(escapePdf(timestamp))
                .append("   |   Color: ").append(escapePdf(colorMode != null ? colorMode : "Color"))
                .append("   |   Resolution: ").append(escapePdf(resolution != null ? resolution : "300 DPI")).append(") Tj\n");

        sb.append("50 696 Td (Security Status: Certified & Encrypted Transmission   |   Job Page: ")
                .append(pageNum).append(" of ").append(totalPages).append(") Tj\n");
        sb.append("ET\n");

        // Document Body simulation area
        sb.append("0.96 0.97 0.98 rg\n");
        sb.append("36 60 523 600 re f\n");
        sb.append("0.85 0.88 0.92 RG 1 w\n");
        sb.append("36 60 523 600 re S\n");

        // Watermark / Body Notice
        sb.append("BT\n");
        sb.append("/F2 18 Tf 0.55 0.60 0.68 rg\n");
        sb.append("140 450 Td (TOSHIBA e-STUDIO 3518A SCANNER) Tj\n");
        sb.append("/F1 12 Tf 0.40 0.45 0.55 rg\n");
        sb.append("150 420 Td (High-Resolution Optical Scan Stream) Tj\n");
        sb.append("110 395 Td (Original document processed through Toshiba RADF / Dual-Scan Platen) Tj\n");
        sb.append("180 370 Td (Delivered via nexPrint Secure Pull-Print) Tj\n");
        sb.append("ET\n");

        // Footer
        sb.append("BT\n");
        sb.append("/F1 9 Tf 0.45 0.50 0.58 rg\n");
        sb.append("36 42 Td (Document transmitted directly to authenticated employee email via secure SMTP.) Tj\n");
        sb.append("480 42 Td (Page ").append(pageNum).append(" of ").append(totalPages).append(") Tj\n");
        sb.append("ET\n");

        return sb.toString();
    }

    private static String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
