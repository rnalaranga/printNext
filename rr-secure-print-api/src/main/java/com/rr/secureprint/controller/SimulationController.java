package com.rr.secureprint.controller;

import com.rr.secureprint.dto.JobDto;
import com.rr.secureprint.dto.SimulationJobRequest;
import com.rr.secureprint.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/submit-job")
    public ResponseEntity<JobDto> submitSimulatedJob(@RequestBody SimulationJobRequest request) throws IOException {
        JobDto job = simulationService.submitSimulatedJob(request);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<Map<String, String>> seedDemoJobs() {
        simulationService.seedInitialDemoJobs();
        return ResponseEntity.ok(Map.of("message", "Demonstration jobs seeded successfully into queue."));
    }

    @PostMapping("/expire-stale")
    public ResponseEntity<Map<String, String>> expireStaleJobs() {
        simulationService.cleanupExpiredJobs();
        return ResponseEntity.ok(Map.of("message", "Job expiration cleanup sweep completed."));
    }
}
