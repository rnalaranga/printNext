package com.rr.secureprint.repository;

import com.rr.secureprint.entity.JobStatus;
import com.rr.secureprint.entity.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    List<PrintJob> findByUserSidAndStatusOrderBySubmittedAtDesc(String userSid, JobStatus status);

    List<PrintJob> findByUserSidAndStatusInOrderBySubmittedAtDesc(String userSid, Collection<JobStatus> statuses);

    List<PrintJob> findByStatus(JobStatus status);

    List<PrintJob> findByStatusOrderBySubmittedAtDesc(JobStatus status);

    long countByUserSidAndStatus(String userSid, JobStatus status);

    List<PrintJob> findAllByOrderBySubmittedAtDesc();

    List<PrintJob> findByStatusAndExpiresAtBefore(JobStatus status, Instant expirationThreshold);

    @Modifying
    @Query("UPDATE PrintJob j SET j.targetPrinter = null WHERE j.targetPrinter.id = :printerId")
    int detachPrinterFromJobs(@Param("printerId") Long printerId);

    /**
     * Concurrency-safe atomic state transition using database-level Compare-And-Swap (CAS).
     * Prevents race conditions where two simultaneous requests attempt to release or cancel the same job.
     * Returns 1 if transition succeeded, 0 if another thread/request already altered the state.
     */
    @Modifying
    @Query("UPDATE PrintJob j SET j.status = :newStatus, j.version = j.version + 1 WHERE j.id = :id AND j.status = :expectedStatus")
    int atomicTransitionStatus(@Param("id") UUID id,
                               @Param("expectedStatus") JobStatus expectedStatus,
                               @Param("newStatus") JobStatus newStatus);
}
