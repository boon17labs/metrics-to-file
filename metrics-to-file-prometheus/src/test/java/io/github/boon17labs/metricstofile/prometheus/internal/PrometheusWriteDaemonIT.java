package io.github.boon17labs.metricstofile.prometheus.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusWriteDaemonIT {

    private static final long SHORT_INTERVAL_MILLIS = 20L;
    private static final long LONG_INTERVAL_MILLIS = 60_000L;
    private static final long POLL_TIMEOUT_MILLIS = 5_000L;
    private static final String SAMPLE_PATTERN =
            "queue_size\\{application=\"order-service\"\\} 7\\.0 \\d+";

    @Test
    void shouldWriteFirstSnapshotImmediatelyInsteadOfAfterTheFirstInterval(
            @TempDir final File logDir) throws Exception {
        // given: an interval so long that only an immediate snapshot can appear in time
        final PrometheusWriteDaemon daemon =
                daemon(registryWithQueueSizeGauge(), logDir, LONG_INTERVAL_MILLIS);

        // when
        daemon.start();
        try {
            assertTrue(waitUntil(() -> lineCount(promFile(logDir)) >= 1),
                    "no snapshot appeared shortly after start");
        } finally {
            stop(daemon);
        }

        // then
        final List<String> lines = readLinesOf(promFile(logDir));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(SAMPLE_PATTERN), lines.get(0));
    }

    @Test
    void shouldSnapshotAgainOnEveryInterval(@TempDir final File logDir) throws Exception {
        // given
        final PrometheusWriteDaemon daemon =
                daemon(registryWithQueueSizeGauge(), logDir, SHORT_INTERVAL_MILLIS);

        // when
        daemon.start();
        try {
            assertTrue(waitUntil(() -> lineCount(promFile(logDir)) >= 3),
                    "fewer than 3 snapshots appeared");
        } finally {
            stop(daemon);
        }

        // then
        final List<String> lines = readLinesOf(promFile(logDir));
        assertTrue(lines.size() >= 3);
        for (final String line : lines) {
            assertTrue(line.matches(SAMPLE_PATTERN), line);
        }
    }

    @Test
    void shouldRunAsDaemonThread(@TempDir final File logDir) {
        // given
        final PrometheusWriteDaemon daemon =
                daemon(registryWithQueueSizeGauge(), logDir, SHORT_INTERVAL_MILLIS);

        // then
        assertTrue(daemon.isDaemon());
    }

    @Test
    void shouldStopAfterShutdownEvenDuringALongInterval(@TempDir final File logDir)
            throws InterruptedException {
        // given
        final PrometheusWriteDaemon daemon =
                daemon(registryWithQueueSizeGauge(), logDir, LONG_INTERVAL_MILLIS);
        daemon.start();

        // when
        daemon.shutdown();
        daemon.join(POLL_TIMEOUT_MILLIS);

        // then
        assertFalse(daemon.isAlive());
    }

    @Test
    void shouldNotWriteAnythingAfterShutdownHasReturned(@TempDir final File logDir)
            throws Exception {
        // given
        final PrometheusWriteDaemon daemon =
                daemon(registryWithQueueSizeGauge(), logDir, SHORT_INTERVAL_MILLIS);
        daemon.start();
        assertTrue(waitUntil(() -> lineCount(promFile(logDir)) >= 1));

        // when
        stop(daemon);
        final int linesAtStop = lineCount(promFile(logDir));
        Thread.sleep(SHORT_INTERVAL_MILLIS * 10);

        // then
        assertEquals(linesAtStop, lineCount(promFile(logDir)));
    }

    @Test
    void shouldKeepRunningWhenSnapshotsFail(@TempDir final File logDir) throws Exception {
        // given
        final PrometheusMeterRegistry failingRegistry =
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT) {
                    @Override
                    public String scrape() {
                        throw new IllegalStateException("boom");
                    }
                };
        final PrometheusWriteDaemon daemon = daemon(failingRegistry, logDir, SHORT_INTERVAL_MILLIS);
        final PrintStream originalErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true));

        // when: several failing ticks go by
        final boolean stillAlive;
        daemon.start();
        try {
            Thread.sleep(SHORT_INTERVAL_MILLIS * 10);
            stillAlive = daemon.isAlive();
        } finally {
            stop(daemon);
            System.setErr(originalErr);
        }

        // then
        assertTrue(stillAlive);
        assertFalse(promFile(logDir).exists());
    }

    private static PrometheusWriteDaemon daemon(final PrometheusMeterRegistry registry,
            final File logDir, final long intervalMillis) {
        final PrometheusSnapshotter snapshotter = new PrometheusSnapshotter(
                registry, new PrometheusFileWriter("order-service", logDir));
        return new PrometheusWriteDaemon(snapshotter, intervalMillis);
    }

    private static PrometheusMeterRegistry registryWithQueueSizeGauge() {
        final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().commonTags("application", "order-service");
        // Supplier-based gauge: the registry holds the supplier strongly.
        Gauge.builder("queue.size", () -> 7).register(registry);
        return registry;
    }

    /** Requests shutdown and waits for the thread to actually finish. */
    private static void stop(final PrometheusWriteDaemon daemon) throws InterruptedException {
        daemon.shutdown();
        daemon.join(POLL_TIMEOUT_MILLIS);
    }

    private static File promFile(final File logDir) {
        return new File(logDir, "order-service-" + LocalDate.now() + ".prom");
    }

    /** Number of lines in the file, or 0 if it is missing or cannot be read yet. */
    private static int lineCount(final File file) {
        try {
            return file.isFile() ? Files.readAllLines(file.toPath()).size() : 0;
        } catch (final IOException e) {
            return 0;
        }
    }

    private static List<String> readLinesOf(final File file) throws IOException {
        return Files.readAllLines(file.toPath());
    }

    private static boolean waitUntil(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(5L);
        }
        return true;
    }
}
