package io.github.boon17labs.metricstofile.prometheus.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusFileWriterIT {

    @Test
    void shouldWriteEachLineOnItsOwnLine(@TempDir final File logDir) throws IOException {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);

        // when
        writer.write(Arrays.asList("up 1.0 1724580000000", "down 0.0 1724580000000"));

        // then
        assertEquals(
                Arrays.asList("up 1.0 1724580000000", "down 0.0 1724580000000"),
                readLinesOf(promFile(logDir, "order-service")));
    }

    @Test
    void shouldNameFileAfterAppNameAndTodaysDate(@TempDir final File logDir) {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);

        // when
        writer.write(Collections.singletonList("up 1.0 1724580000000"));

        // then
        assertTrue(new File(logDir, "order-service-" + LocalDate.now() + ".prom").isFile());
    }

    @Test
    void shouldAppendMultipleWritesToSameFile(@TempDir final File logDir) throws IOException {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);

        // when
        writer.write(Collections.singletonList("first 1.0 1"));
        writer.write(Collections.singletonList("second 2.0 2"));

        // then
        assertEquals(
                Arrays.asList("first 1.0 1", "second 2.0 2"),
                readLinesOf(promFile(logDir, "order-service")));
    }

    @Test
    void shouldCreateLogDirectoryWhenMissing(@TempDir final File tempDir) throws IOException {
        // given
        final File missingDir = new File(tempDir, "nested/metrics");
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", missingDir);

        // when
        writer.write(Collections.singletonList("up 1.0 1"));

        // then
        assertTrue(missingDir.isDirectory());
        assertEquals(1, readLinesOf(promFile(missingDir, "order-service")).size());
    }

    @Test
    void shouldNotCreateFileWhenThereAreNoLines(@TempDir final File logDir) {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);

        // when
        writer.write(Collections.<String>emptyList());

        // then
        assertFalse(promFile(logDir, "order-service").exists());
    }

    @Test
    void shouldNotThrowWhenLogDirectoryCannotBeCreated(@TempDir final File tempDir)
            throws IOException {
        // given
        final PrometheusFileWriter writer =
                new PrometheusFileWriter("order-service", unusableLogDir(tempDir));

        // when / then
        assertDoesNotThrow(() -> writer.write(Collections.singletonList("up 1.0 1")));
    }

    @Test
    void shouldWarnOnStderrWhenLogDirectoryCannotBeCreated(@TempDir final File tempDir)
            throws IOException {
        // given
        final PrometheusFileWriter writer =
                new PrometheusFileWriter("order-service", unusableLogDir(tempDir));
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));

        // when
        try {
            writer.write(Collections.singletonList("up 1.0 1"));
        } finally {
            System.setErr(originalErr);
        }

        // then
        final String stderr = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(stderr.contains("[metrics-to-file] failed to write prometheus metrics"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void shouldRestrictFilePermissionsToOwnerOnly(@TempDir final File logDir)
            throws IOException {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);

        // when
        writer.write(Collections.singletonList("up 1.0 1"));

        // then
        final Set<PosixFilePermission> permissions =
                Files.getPosixFilePermissions(promFile(logDir, "order-service").toPath());
        assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions);
    }

    @Test
    void shouldWriteNonAsciiTextAsUtf8(@TempDir final File logDir) throws IOException {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);
        final String line = "up{name=\"Göteborg\"} 1.0 1";

        // when
        writer.write(Collections.singletonList(line));

        // then
        final byte[] expected = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, Files.readAllBytes(promFile(logDir, "order-service").toPath()));
    }

    @Test
    void shouldKeepEachBatchContiguousUnderConcurrentWriting(@TempDir final File logDir)
            throws InterruptedException, IOException {
        // given
        final PrometheusFileWriter writer = new PrometheusFileWriter("order-service", logDir);
        final int threadCount = 8;
        final int writesPerThread = 50;
        final Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int call = 0; call < writesPerThread; call++) {
                    writer.write(Arrays.asList(
                            "first_" + threadId + "_" + call + " 1.0 1",
                            "second_" + threadId + "_" + call + " 2.0 1"));
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

        // then: every two-line batch sits together, none interleaved with another's
        final List<String> lines = readLinesOf(promFile(logDir, "order-service"));
        assertEquals(threadCount * writesPerThread * 2, lines.size());
        for (int i = 0; i < lines.size(); i += 2) {
            final String id = lines.get(i).substring("first_".length(), lines.get(i).indexOf(' '));
            assertEquals("first_" + id + " 1.0 1", lines.get(i));
            assertEquals("second_" + id + " 2.0 1", lines.get(i + 1));
        }
    }

    /** A log directory that can never be created, because its parent is a regular file. */
    private static File unusableLogDir(final File tempDir) throws IOException {
        final File blockingFile = new File(tempDir, "not-a-directory");
        assertTrue(blockingFile.createNewFile());
        return new File(blockingFile, "metrics");
    }

    private static File promFile(final File logDir, final String appName) {
        return new File(logDir, appName + "-" + LocalDate.now() + ".prom");
    }

    private static List<String> readLinesOf(final File file) throws IOException {
        return Files.readAllLines(file.toPath());
    }
}
