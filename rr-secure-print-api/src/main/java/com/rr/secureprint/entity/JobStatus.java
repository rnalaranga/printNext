package com.rr.secureprint.entity;

/**
 * Lifecycle states for secure pull-print jobs.
 * Enforces explicit transition safety across all spooling and release stages.
 */
public enum JobStatus {
    /** Job received by spooler / ingestion interceptor */
    RECEIVED,
    /** Spool data validated, SHA-256 verified, held securely awaiting user badge/PIN release */
    HELD,
    /** Undergoing concurrency-locked release sequence; prevents duplicate release */
    RELEASING,
    /** Transmitted to physical printer raw socket / LPR port */
    SUBMITTED,
    /** Print completed successfully */
    COMPLETED,
    /** Cancelled by user or administrator; spool data purged */
    CANCELLED,
    /** Job retention window elapsed without release; spool data purged */
    EXPIRED,
    /** Network failure, communication error, or hardware fault during release */
    FAILED
}
