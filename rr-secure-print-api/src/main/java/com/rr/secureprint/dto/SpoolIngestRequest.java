package com.rr.secureprint.dto;

public record SpoolIngestRequest(
        String username,
        String userSid,
        String documentName,
        int pageCount,
        boolean color,
        boolean duplex,
        long sizeBytes,
        String clientMachine,
        Integer windowsJobId
) {
    public SpoolIngestRequest {
        if (username == null || username.isBlank()) {
            username = "RR";
        }
        if (documentName == null || documentName.isBlank()) {
            documentName = "Windows-Print-Document.pdf";
        }
        if (pageCount <= 0) pageCount = 1;
        if (clientMachine == null || clientMachine.isBlank()) {
            clientMachine = "LOCAL-PC";
        }
    }
}
