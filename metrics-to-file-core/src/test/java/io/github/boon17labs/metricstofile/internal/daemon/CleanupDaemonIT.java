package io.github.boon17labs.metricstofile.internal.daemon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupDaemonIT {

    private static final long SHORT_INTERVAL_MILLIS = 20L;
    private static final long POLL_TIMEOUT_MILLIS = 2_000L;

    @Test
    void shouldDeleteOldFilesShortlyAfterStarting(@TempDir final File logDir) throws Exception {
        // given
        final File oldFile = new File(logDir, "order-service-" + LocalDate.now().minusDays(30) + ".log");
        assertTrue(oldFile.createNewFile());
        final CleanupDaemon daemon =
                new CleanupDaemon(logDir, "order-service", 7, SHORT_INTERVAL_MILLIS);

        // when
        daemon.start();
        try {
            waitUntil(() -> !oldFile.exists());
        } finally {
            daemon.shutdown();
        }

        // then
        assertFalse(oldFile.exists());
    }

    @Test
    void shouldDeleteOldFilesWithGivenSuffixShortlyAfterStarting(@TempDir final File logDir)
            throws Exception {
        // given
        final File oldFile = new File(logDir, "order-service-" + LocalDate.now().minusDays(30) + ".prom");
        assertTrue(oldFile.createNewFile());
        final CleanupDaemon daemon =
                new CleanupDaemon(logDir, "order-service", 7, SHORT_INTERVAL_MILLIS, ".prom");

        // when
        daemon.start();
        try {
            waitUntil(() -> !oldFile.exists());
        } finally {
            daemon.shutdown();
        }

        // then
        assertFalse(oldFile.exists());
    }

    @Test
    void shouldDeleteOldestFileOverSizeCapShortlyAfterStarting(@TempDir final File logDir)
            throws Exception {
        // given: two 700 KB files (1.4 MB total); a 1 MB cap can only hold one
        final File older = fileOfSize(logDir, LocalDate.now().minusDays(1), 700_000);
        final File newer = fileOfSize(logDir, LocalDate.now(), 700_000);
        final CleanupDaemon daemon =
                new CleanupDaemon(logDir, "order-service", 7, 1L, SHORT_INTERVAL_MILLIS);

        // when
        daemon.start();
        try {
            waitUntil(() -> !older.exists());
        } finally {
            daemon.shutdown();
        }

        // then
        assertFalse(older.exists());
        assertTrue(newer.exists());
    }

    @Test
    void shouldRunAsDaemonThread(@TempDir final File logDir) {
        // given
        final CleanupDaemon daemon =
                new CleanupDaemon(logDir, "order-service", 7, SHORT_INTERVAL_MILLIS);

        // then
        assertTrue(daemon.isDaemon());
    }

    @Test
    void shouldStopAfterShutdown(@TempDir final File logDir) throws InterruptedException {
        // given
        final CleanupDaemon daemon =
                new CleanupDaemon(logDir, "order-service", 7, SHORT_INTERVAL_MILLIS);
        daemon.start();

        // when
        daemon.shutdown();
        daemon.join(POLL_TIMEOUT_MILLIS);

        // then
        assertFalse(daemon.isAlive());
    }

    @Test
    void shouldNotThrowWhenLogDirDoesNotExist(@TempDir final File tempDir) {
        // given
        final File missingDir = new File(tempDir, "does-not-exist");
        final CleanupDaemon daemon =
                new CleanupDaemon(missingDir, "order-service", 7, SHORT_INTERVAL_MILLIS);

        // when / then
        assertDoesNotThrow(() -> {
            daemon.start();
            Thread.sleep(SHORT_INTERVAL_MILLIS * 5);
            daemon.shutdown();
            daemon.join(POLL_TIMEOUT_MILLIS);
        });
    }

    private static File fileOfSize(final File logDir, final LocalDate date, final int sizeBytes)
            throws IOException {
        final File file = new File(logDir, "order-service-" + date + ".log");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[sizeBytes]);
        }
        return file;
    }

    private static void waitUntil(final Condition condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (!condition.isMet()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within timeout");
            }
            Thread.sleep(5L);
        }
    }

    private interface Condition {
        boolean isMet();
    }
}
