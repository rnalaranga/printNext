package com.rr.secureprint.controller;

import com.rr.secureprint.dto.JobDto;
import com.rr.secureprint.dto.SpoolIngestRequest;
import com.rr.secureprint.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/spool")
public class SpoolController {

    private final SimulationService simulationService;

    public SpoolController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<JobDto> ingestSpoolJob(@RequestBody SpoolIngestRequest request) throws IOException {
        JobDto job = simulationService.ingestWindowsSpoolJob(request);
        return ResponseEntity.ok(job);
    }
}
