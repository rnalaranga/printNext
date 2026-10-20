package com.rr.secureprint.dto;

public record SimulationJobRequest(
        String username,
        String documentName,
        int pageCount,
        boolean color,
        boolean duplex,
        int copies,
        String clientMachine
) {
    public SimulationJobRequest {
        if (username == null || username.isBlank()) {
            username = "jdoe";
        }
        if (documentName == null || documentName.isBlank()) {
            documentName = "Structural-Drawings-Rev2.pdf";
        }
        if (pageCount <= 0) pageCount = 4;
        if (copies <= 0) copies = 1;
        if (clientMachine == null || clientMachine.isBlank()) {
            clientMachine = "ENG-CAD-WS04";
        }
    }
}
