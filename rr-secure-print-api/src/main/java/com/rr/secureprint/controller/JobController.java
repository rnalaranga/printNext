package com.rr.secureprint.controller;

import com.rr.secureprint.dto.JobDto;
import com.rr.secureprint.dto.ReleaseRequest;
import com.rr.secureprint.dto.ReleaseResponse;
import com.rr.secureprint.entity.UserEntity;
import com.rr.secureprint.service.AuthService;
import com.rr.secureprint.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final AuthService authService;

    public JobController(JobService jobService, AuthService authService) {
        this.jobService = jobService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<JobDto>> getPendingJobs(HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<JobDto> jobs = jobService.getPendingJobsForUser(userOpt.get());
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDto> getJobById(@PathVariable("id") UUID id, HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JobDto job = jobService.getJobById(id, userOpt.get());
        return ResponseEntity.ok(job);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<ReleaseResponse> releaseJob(@PathVariable("id") UUID id,
                                                      @RequestBody(required = false) ReleaseRequest request,
                                                      HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String printerCode = (request != null) ? request.printerCode() : null;
        String clientIp = httpRequest.getRemoteAddr();

        ReleaseResponse response = jobService.releaseJob(id, printerCode, userOpt.get(), clientIp);
        if (response.success()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelJob(@PathVariable("id") UUID id, HttpServletRequest httpRequest) {
        Optional<UserEntity> userOpt = authService.getCurrentUser(httpRequest);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String clientIp = httpRequest.getRemoteAddr();
        jobService.cancelJob(id, userOpt.get(), clientIp);
        return ResponseEntity.noContent().build();
    }
}
