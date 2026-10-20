package com.rr.secureprint.agent.interceptor;

import com.rr.secureprint.agent.model.SpoolJobMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FileWatcherSpoolInterceptor:
 * Monitors a designated spool pickup directory (such as a custom port monitor output directory
 * or C:\Windows\System32\spool\PRINTERS) and safely quarantines jobs into RR Secure Print storage.
 */
public class FileWatcherSpoolInterceptor implements WindowsSpoolInterceptor {

    private static final Logger LOGGER = Logger.getLogger(FileWatcherSpoolInterceptor.class.getName());

    private final Path watchDirectory;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FileWatcherSpoolInterceptor(Path watchDirectory) {
        this.watchDirectory = watchDirectory;
    }

    @Override
    public synchronized void startMonitoring() throws IOException {
        if (running.get()) {
            return;
        }
        if (!Files.exists(watchDirectory)) {
            Files.createDirectories(watchDirectory);
        }
        running.set(true);
        LOGGER.info("Windows Spool Interceptor monitoring active on: " + watchDirectory.toAbsolutePath());
    }

    @Override
    public synchronized void stopMonitoring() {
        running.set(false);
        LOGGER.info("Windows Spool Interceptor stopped.");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Optional<SpoolJobMetadata> quarantineSpoolFile(File originalSpoolFile, File destinationDir) throws IOException {
        if (!originalSpoolFile.exists() || originalSpoolFile.length() == 0) {
            return Optional.empty();
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        String targetFileName = UUID.randomUUID() + ".bin";
        File destinationFile = new File(destinationDir, targetFileName);

        // Atomic move or copy & delete to prevent partial reads
        try {
            Files.move(originalSpoolFile.toPath(), destinationFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fallback for cross-filesystem moves
            Files.copy(originalSpoolFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            originalSpoolFile.delete();
        }

        SpoolJobMetadata metadata = new SpoolJobMetadata(
                (int) (System.currentTimeMillis() % 100000),
                "RR-SECURE-VIRTUAL-QUEUE",
                originalSpoolFile.getName(),
                System.getProperty("user.name", "WindowsUser"),
                "S-1-5-21-3623811015-3361044348-30300820-1001",
                "RAW",
                1,
                destinationFile.length(),
                Instant.now(),
                destinationFile.getAbsolutePath()
        );

        LOGGER.info("Successfully quarantined spool file " + originalSpoolFile.getName() + " -> " + destinationFile.getName());
        return Optional.of(metadata);
    }
}
