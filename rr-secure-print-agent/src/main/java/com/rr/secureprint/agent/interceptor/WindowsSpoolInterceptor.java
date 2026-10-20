package com.rr.secureprint.agent.interceptor;

import com.rr.secureprint.agent.model.SpoolJobMetadata;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Contract for Windows Print Spooler interceptors.
 * Intercepts printer-ready spool files (.SPL) and metadata (.SHD)
 * before jobs reach physical output devices.
 */
public interface WindowsSpoolInterceptor {

    /**
     * Start monitoring the Windows print spool directory or print queue notifications.
     */
    void startMonitoring() throws IOException;

    /**
     * Stop the interceptor service cleanly.
     */
    void stopMonitoring();

    /**
     * Check if the interceptor is currently active and healthy.
     */
    boolean isRunning();

    /**
     * Safely quarantine and extract a spool file from the spool directory
     * into the secure holding storage.
     *
     * @param originalSpoolFile The original .SPL file in Windows spool directory
     * @param destinationDir The secure holding directory
     * @return Extracted metadata and moved file reference
     */
    Optional<SpoolJobMetadata> quarantineSpoolFile(File originalSpoolFile, File destinationDir) throws IOException;
}
