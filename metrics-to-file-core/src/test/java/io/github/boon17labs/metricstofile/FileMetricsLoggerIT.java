package io.github.boon17labs.metricstofile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMetricsLoggerIT {

    private static final String TIMESTAMP_PATTERN =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    @Test
    void shouldWriteFormattedLineToFileWhenLogging(@TempDir final File logDir)
            throws IOException {
        // given
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", 312);
        values.put("committed_mb", 400);

        // when
        logger.log("heap", values);

        // then
        final List<String> lines = readLinesOf(logFile(logDir, "order-service"));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(
                TIMESTAMP_PATTERN
                        + " app=order-service type=heap"
                        + " used_mb=312 committed_mb=400"));
    }

    @Test
    void shouldAppendMultipleLogCallsToSameFile(@TempDir final File logDir)
            throws IOException {
        // given
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);

        // when
        logger.log("heap", new LinkedHashMap<>());
        logger.log("threads", new LinkedHashMap<>());

        // then
        final List<String> lines = readLinesOf(logFile(logDir, "order-service"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("type=heap"));
        assertTrue(lines.get(1).contains("type=threads"));
    }

    @Test
    void shouldCreateLogDirectoryWhenMissing(@TempDir final File tempDir)
            throws IOException {
        // given
        final File missingDir = new File(tempDir, "nested/metrics");
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", missingDir);

        // when
        logger.log("heap", new LinkedHashMap<>());

        // then
        assertTrue(missingDir.isDirectory());
        assertEquals(1, readLinesOf(logFile(missingDir, "order-service")).size());
    }

    @Test
    void shouldNotThrowWhenLogDirectoryCannotBeCreated(@TempDir final File tempDir)
            throws IOException {
        // given
        final File blockingFile = new File(tempDir, "not-a-directory");
        assertTrue(blockingFile.createNewFile());
        final File logDir = new File(blockingFile, "metrics");
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);

        // when / then
        assertDoesNotThrow(() -> logger.log("heap", new LinkedHashMap<>()));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void shouldRestrictFilePermissionsToOwnerOnly(@TempDir final File logDir)
            throws IOException {
        // given
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);

        // when
        logger.log("heap", new LinkedHashMap<>());

        // then
        final Set<PosixFilePermission> permissions =
                Files.getPosixFilePermissions(logFile(logDir, "order-service").toPath());
        assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions);
    }

    @Test
    void shouldWriteWellFormedLinesUnderConcurrentLogging(@TempDir final File logDir)
            throws InterruptedException, IOException {
        // given
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);
        final int threadCount = 8;
        final int callsPerThread = 50;
        final Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int call = 0; call < callsPerThread; call++) {
                    logger.log("heap", new LinkedHashMap<>());
                }
            });
        }

        // when
        for (final Thread thread : threads) {
            thread.start();
        }
        for (final Thread thread : threads) {
            thread.join();
        }

        // then
        final List<String> lines = readLinesOf(logFile(logDir, "order-service"));
        assertEquals(threadCount * callsPerThread, lines.size());
        for (final String line : lines) {
            assertTrue(line.matches(TIMESTAMP_PATTERN + " app=order-service type=heap"));
        }
    }

    @Test
    void shouldNotThrowWhenClosing(@TempDir final File logDir) {
        // given
        final FileMetricsLogger logger = new FileMetricsLogger("order-service", logDir);

        // when / then
        assertDoesNotThrow(logger::close);
    }

    private static File logFile(final File logDir, final String appName) {
        return new File(logDir, appName + "-" + LocalDate.now() + ".log");
    }

    private static List<String> readLinesOf(final File file) throws IOException {
        return Files.readAllLines(file.toPath());
    }
}
