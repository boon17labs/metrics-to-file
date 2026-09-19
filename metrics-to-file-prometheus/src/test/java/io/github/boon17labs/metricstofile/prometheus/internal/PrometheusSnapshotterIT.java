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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusSnapshotterIT {

    private static final long TIMESTAMP = 1724580000000L;

    @Test
    void shouldWriteTimestampedTaggedSampleToDailyFile(@TempDir final File logDir)
            throws IOException {
        // given
        final PrometheusMeterRegistry registry = registryWithQueueSizeGauge();
        final PrometheusSnapshotter snapshotter = snapshotter(registry, logDir, new MutableClock(TIMESTAMP));

        // when
        snapshotter.snapshot();

        // then
        assertEquals(
                Collections.singletonList("queue_size{application=\"order-service\"} 7.0 1724580000000"),
                readLinesOf(promFile(logDir)));
    }

    @Test
    void shouldNotWriteCommentLines(@TempDir final File logDir) throws IOException {
        // given
        final PrometheusMeterRegistry registry = registryWithQueueSizeGauge();
        final PrometheusSnapshotter snapshotter = snapshotter(registry, logDir, new MutableClock(TIMESTAMP));

        // when
        snapshotter.snapshot();

        // then
        for (final String line : readLinesOf(promFile(logDir))) {
            assertFalse(line.startsWith("#"), line);
        }
    }

    @Test
    void shouldAppendEachSnapshotWithItsOwnTimestamp(@TempDir final File logDir)
            throws IOException {
        // given
        final PrometheusMeterRegistry registry = registryWithQueueSizeGauge();
        final MutableClock clock = new MutableClock(1000L);
        final PrometheusSnapshotter snapshotter = snapshotter(registry, logDir, clock);

        // when
        snapshotter.snapshot();
        clock.set(2000L);
        snapshotter.snapshot();

        // then
        assertEquals(
                Arrays.asList(
                        "queue_size{application=\"order-service\"} 7.0 1000",
                        "queue_size{application=\"order-service\"} 7.0 2000"),
                readLinesOf(promFile(logDir)));
    }

    @Test
    void shouldWriteNoFileWhenRegistryHasNoMeters(@TempDir final File logDir) {
        // given
        final PrometheusMeterRegistry emptyRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        final PrometheusSnapshotter snapshotter = snapshotter(emptyRegistry, logDir, new MutableClock(TIMESTAMP));

        // when
        snapshotter.snapshot();

        // then
        assertFalse(promFile(logDir).exists());
    }

    @Test
    void shouldSnapshotTheDefaultJvmMetrics(@TempDir final File logDir) throws IOException {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");
        final PrometheusSnapshotter snapshotter =
                snapshotter(jvmRegistry.registry(), logDir, new MutableClock(TIMESTAMP));

        // when
        try {
            snapshotter.snapshot();
        } finally {
            jvmRegistry.close();
        }

        // then
        final List<String> lines = readLinesOf(promFile(logDir));
        boolean heapLineFound = false;
        for (final String line : lines) {
            assertTrue(line.endsWith(" 1724580000000"), line);
            if (line.startsWith("jvm_memory_used_bytes{application=\"order-service\",area=\"heap\"")) {
                heapLineFound = true;
            }
        }
        assertTrue(heapLineFound);
    }

    @Test
    void shouldNotThrowAndWarnWhenTheScrapeFails(@TempDir final File logDir) {
        // given
        final PrometheusMeterRegistry failingRegistry =
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT) {
                    @Override
                    public String scrape() {
                        throw new IllegalStateException("boom");
                    }
                };
        final PrometheusSnapshotter snapshotter =
                snapshotter(failingRegistry, logDir, new MutableClock(TIMESTAMP));
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));

        // when / then
        try {
            assertDoesNotThrow(snapshotter::snapshot);
        } finally {
            System.setErr(originalErr);
        }
        final String stderr = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(stderr.contains("[metrics-to-file] failed to take prometheus snapshot"));
        assertTrue(stderr.contains("boom"));
        assertFalse(promFile(logDir).exists());
    }

    private static PrometheusMeterRegistry registryWithQueueSizeGauge() {
        final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().commonTags("application", "order-service");
        // Supplier-based gauge: the registry holds the supplier strongly, so the
        // value cannot vanish when a plain object reference is garbage collected.
        Gauge.builder("queue.size", () -> 7).register(registry);
        return registry;
    }

    private static PrometheusSnapshotter snapshotter(final PrometheusMeterRegistry registry,
            final File logDir, final Clock clock) {
        return new PrometheusSnapshotter(
                registry, new PrometheusFileWriter("order-service", logDir), clock);
    }

    private static File promFile(final File logDir) {
        return new File(logDir, "order-service-" + LocalDate.now() + ".prom");
    }

    private static List<String> readLinesOf(final File file) throws IOException {
        return Files.readAllLines(file.toPath());
    }

    /** A clock whose current time the test sets explicitly. */
    private static final class MutableClock extends Clock {

        private long millis;

        MutableClock(final long millis) {
            this.millis = millis;
        }

        void set(final long millis) {
            this.millis = millis;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }
    }
}
