package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.InMemoryMetricsLogger;
import io.github.boon17labs.metricstofile.internal.config.MetricsOptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsCollectionDaemonIT {

    private static final long SHORT_INTERVAL_MILLIS = 20L;
    private static final long POLL_TIMEOUT_MILLIS = 2_000L;

    @Test
    void shouldCollectHeapMetricsPeriodically() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "heap") >= 2);
        } finally {
            daemon.shutdown();
        }

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> entries = logger.entries();
        assertTrue(countOfType(logger, "heap") >= 2);
        assertEquals("heap", entries.get(0).type());
        assertTrue(entries.get(0).values().containsKey("used_mb"));
    }

    @Test
    void shouldCollectThreadMetricsPeriodically() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "threads") >= 2);
        } finally {
            daemon.shutdown();
        }

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> threadEntries = logger.entries().stream()
                .filter(entry -> "threads".equals(entry.type()))
                .collect(Collectors.toList());
        assertTrue(threadEntries.size() >= 2);
        assertTrue(threadEntries.get(0).values().containsKey("live"));
    }

    @Test
    void shouldCollectMetaspaceMetricsPeriodically() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "metaspace") >= 2);
        } finally {
            daemon.shutdown();
        }

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> metaspaceEntries = logger.entries().stream()
                .filter(entry -> "metaspace".equals(entry.type()))
                .collect(Collectors.toList());
        assertTrue(metaspaceEntries.size() >= 2);
        assertTrue(metaspaceEntries.get(0).values().containsKey("used_mb"));
    }

    @Test
    void shouldCollectGcMetricsForEachCollectorBean() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());
        final long gcBeanCount = ManagementFactory.getGarbageCollectorMXBeans().size();

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "gc") >= gcBeanCount);
        } finally {
            daemon.shutdown();
        }

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> gcEntries = logger.entries().stream()
                .filter(entry -> "gc".equals(entry.type()))
                .collect(Collectors.toList());
        assertTrue(gcEntries.size() >= gcBeanCount);
        for (final InMemoryMetricsLogger.LoggedMetric entry : gcEntries) {
            assertTrue(entry.values().containsKey("name"));
            assertTrue(entry.values().containsKey("count"));
            assertTrue(entry.values().containsKey("time_ms"));
        }
    }

    @Test
    void shouldNotCollectOptInMetricsByDefault() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "heap") >= 2);
        } finally {
            daemon.shutdown();
        }

        // then
        assertEquals(0, countOfType(logger, "direct"));
        assertEquals(0, countOfType(logger, "classloading"));
        assertEquals(0, countOfType(logger, "cpu"));
        assertEquals(0, countOfType(logger, "codecache"));
        assertEquals(0, countOfType(logger, "process"));
    }

    @Test
    void shouldCollectOptInMetricsWhenEnabled() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsOptions options = new MetricsOptions(true, true, true, true, true);
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, options);

        // when
        daemon.start();
        try {
            waitUntil(() -> countOfType(logger, "process") >= 2);
        } finally {
            daemon.shutdown();
        }

        // then
        assertTrue(countOfType(logger, "direct") >= 2);
        assertTrue(countOfType(logger, "classloading") >= 2);
        assertTrue(countOfType(logger, "cpu") >= 2);
        assertTrue(countOfType(logger, "codecache") >= 2);
        assertTrue(countOfType(logger, "process") >= 2);
    }

    private static long countOfType(final InMemoryMetricsLogger logger, final String type) {
        return logger.entries().stream().filter(entry -> type.equals(entry.type())).count();
    }

    @Test
    void shouldRunAsDaemonThread() {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());

        // then
        assertTrue(daemon.isDaemon());
    }

    @Test
    void shouldStopCollectingAfterShutdown() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, SHORT_INTERVAL_MILLIS, MetricsOptions.defaults());
        daemon.start();
        waitUntil(() -> !logger.entries().isEmpty());

        // when
        daemon.shutdown();
        daemon.join(POLL_TIMEOUT_MILLIS);
        final int countAtShutdown = logger.entries().size();
        Thread.sleep(SHORT_INTERVAL_MILLIS * 10);

        // then
        assertFalse(daemon.isAlive());
        assertEquals(countAtShutdown, logger.entries().size());
    }

    @Test
    void shouldTerminatePromptlyEvenWhileSleeping() throws InterruptedException {
        // given
        final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();
        final long longInterval = 5_000L;
        final MetricsCollectionDaemon daemon =
                new MetricsCollectionDaemon(logger, longInterval, MetricsOptions.defaults());
        daemon.start();
        waitUntil(() -> !logger.entries().isEmpty());

        // when
        final long before = System.currentTimeMillis();
        daemon.shutdown();
        daemon.join(POLL_TIMEOUT_MILLIS);
        final long elapsed = System.currentTimeMillis() - before;

        // then
        assertFalse(daemon.isAlive());
        assertTrue(elapsed < longInterval);
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
