package io.github.guranxp.metricstofile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsIT {

    private static final long POLL_TIMEOUT_MILLIS = 2_000L;

    @AfterEach
    void stopAndClearProperty() {
        Metrics.stop();
        System.clearProperty("metrics.implementation");
        System.clearProperty("metrics.log.dir");
        System.clearProperty("metrics.interval");
        System.clearProperty("metrics.keep.days");
        System.clearProperty("metrics.opt.direct");
        System.clearProperty("metrics.opt.classloading");
        System.clearProperty("metrics.opt.cpu");
        System.clearProperty("metrics.opt.codecache");
    }

    @Test
    void shouldNotThrowWhenStartingAndStopping() {
        assertDoesNotThrow(() -> {
            Metrics.start("order-service");
            Metrics.stop();
        });
    }

    @Test
    void shouldNotThrowWhenStoppingWithoutStarting() {
        assertDoesNotThrow(Metrics::stop);
    }

    @Test
    void shouldNotThrowWhenStartingWithInMemoryImplementation() {
        // given
        System.setProperty("metrics.implementation", "inmemory");

        // when / then
        assertDoesNotThrow(() -> Metrics.start("order-service"));
    }

    @Test
    void shouldStartCollectionDaemonAsDaemonThreadWhenStarting() {
        // given
        System.setProperty("metrics.implementation", "inmemory");

        // when
        Metrics.start("order-service");

        // then
        assertNotNull(Metrics.collectionDaemon);
        assertTrue(Metrics.collectionDaemon.isAlive());
        assertTrue(Metrics.collectionDaemon.isDaemon());
    }

    @Test
    void shouldStopCollectionDaemonWhenStopping() throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");
        Metrics.start("order-service");
        final Thread daemon = Metrics.collectionDaemon;

        // when
        Metrics.stop();
        daemon.join(POLL_TIMEOUT_MILLIS);

        // then
        assertFalse(daemon.isAlive());
    }

    @Test
    void shouldNotStartAnyDaemonWhenLoggerIsNoOp() {
        // when
        Metrics.start("order-service");

        // then
        assertNull(Metrics.collectionDaemon);
        assertNull(Metrics.cleanupDaemon);
    }

    @Test
    void shouldStartCleanupDaemonAsDaemonThreadWhenStarting(@TempDir final File logDir) {
        // given
        System.setProperty("metrics.implementation", "file");

        // when
        Metrics.builder().appName("order-service").logDir(logDir.getAbsolutePath()).start();

        // then
        assertNotNull(Metrics.cleanupDaemon);
        assertTrue(Metrics.cleanupDaemon.isAlive());
        assertTrue(Metrics.cleanupDaemon.isDaemon());
    }

    @Test
    void shouldStopCleanupDaemonWhenStopping(@TempDir final File logDir)
            throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "file");
        Metrics.builder().appName("order-service").logDir(logDir.getAbsolutePath()).start();
        final Thread daemon = Metrics.cleanupDaemon;

        // when
        Metrics.stop();
        daemon.join(POLL_TIMEOUT_MILLIS);

        // then
        assertFalse(daemon.isAlive());
    }

    @Test
    void shouldHaveTerminatedBothDaemonsBeforeStopReturns(@TempDir final File logDir) {
        // given
        System.setProperty("metrics.implementation", "file");
        Metrics.builder().appName("order-service").logDir(logDir.getAbsolutePath()).start();
        final Thread collection = Metrics.collectionDaemon;
        final Thread cleanup = Metrics.cleanupDaemon;

        // when
        Metrics.stop();

        // then — no join() here: stop() itself must already guarantee this
        assertFalse(collection.isAlive());
        assertFalse(cleanup.isAlive());
    }

    @Test
    void shouldUseCustomKeepDaysFromBuilder(@TempDir final File logDir) throws IOException,
            InterruptedException {
        // given
        System.setProperty("metrics.implementation", "file");
        final File oldFile =
                new File(logDir, "order-service-" + LocalDate.now().minusDays(10) + ".log");
        assertTrue(oldFile.createNewFile());

        // when
        Metrics.builder()
                .appName("order-service")
                .logDir(logDir.getAbsolutePath())
                .interval(Duration.ofMillis(20L))
                .keepDays(7)
                .start();

        // then
        waitUntil(() -> !oldFile.exists());
    }

    @Test
    void shouldRegisterShutdownHookWithRuntime() {
        // when / then
        assertTrue(Runtime.getRuntime().removeShutdownHook(Metrics.shutdownHook));

        // restore, so the rest of the suite (and a real JVM exit) still
        // gets the hook
        Runtime.getRuntime().addShutdownHook(Metrics.shutdownHook);
    }

    @Test
    void shouldStopViaShutdownHookWhenInvoked() throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");
        Metrics.start("order-service");
        final Thread daemon = Metrics.collectionDaemon;

        // when
        Metrics.shutdownHook.run();
        daemon.join(POLL_TIMEOUT_MILLIS);

        // then
        assertFalse(daemon.isAlive());
    }

    @Test
    void shouldLogCustomMetricThroughActiveLogger() {
        // given
        System.setProperty("metrics.implementation", "inmemory");
        Metrics.start("order-service");
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("hits", 42);
        values.put("misses", 3);

        // when
        Metrics.log("cache", values);

        // then
        final InMemoryMetricsLogger logger = (InMemoryMetricsLogger) Metrics.activeLogger;
        final InMemoryMetricsLogger.LoggedMetric entry = logger.entries().stream()
                .filter(e -> "cache".equals(e.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no 'cache' entry logged"));
        assertEquals(values, entry.values());
    }

    @Test
    void shouldNotThrowWhenLoggingCustomMetricBeforeStarting() {
        assertDoesNotThrow(() -> Metrics.log("cache", new LinkedHashMap<>()));
    }

    @Test
    void shouldThrowWhenBuilderStartedWithoutAppName() {
        assertThrows(IllegalStateException.class, () -> Metrics.builder().start());
    }

    @Test
    void shouldUseCustomIntervalFromBuilder() throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");

        // when
        Metrics.builder()
                .appName("order-service")
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(() -> entryCount() >= 2);
    }

    @Test
    void shouldUseCustomLogDirFromBuilder(@TempDir final File logDir) throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "file");
        final File expectedFile = new File(logDir, "order-service-" + LocalDate.now() + ".log");

        // when
        Metrics.builder()
                .appName("order-service")
                .logDir(logDir.getAbsolutePath())
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(expectedFile::exists);
    }

    @Test
    void shouldNotCollectOptInMetricsByDefault() throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");

        // when
        Metrics.builder()
                .appName("order-service")
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(() -> entryCount() >= 2);
        assertEquals(0, entriesOfType("direct"));
        assertEquals(0, entriesOfType("classloading"));
        assertEquals(0, entriesOfType("cpu"));
        assertEquals(0, entriesOfType("codecache"));
    }

    @Test
    void shouldCollectOptInMetricsWhenEnabledOnBuilder() throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");

        // when
        Metrics.builder()
                .appName("order-service")
                .interval(Duration.ofMillis(20L))
                .withDirectMemory()
                .withClassLoading()
                .withCpu()
                .withCodeCache()
                .start();

        // then
        waitUntil(() -> entriesOfType("codecache") >= 2);
        assertTrue(entriesOfType("direct") >= 2);
        assertTrue(entriesOfType("classloading") >= 2);
        assertTrue(entriesOfType("cpu") >= 2);
        assertTrue(entriesOfType("codecache") >= 2);
    }

    @Test
    void shouldUseLogDirFromSystemPropertyWhenNotSetOnBuilder(@TempDir final File logDir)
            throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "file");
        System.setProperty("metrics.log.dir", logDir.getAbsolutePath());
        final File expectedFile = new File(logDir, "order-service-" + LocalDate.now() + ".log");

        // when
        Metrics.builder()
                .appName("order-service")
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(expectedFile::exists);
    }

    @Test
    void shouldUseKeepDaysFromSystemPropertyWhenNotSetOnBuilder(@TempDir final File logDir)
            throws IOException, InterruptedException {
        // given
        System.setProperty("metrics.implementation", "file");
        System.setProperty("metrics.keep.days", "7");
        final File oldFile =
                new File(logDir, "order-service-" + LocalDate.now().minusDays(10) + ".log");
        assertTrue(oldFile.createNewFile());

        // when
        Metrics.builder()
                .appName("order-service")
                .logDir(logDir.getAbsolutePath())
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(() -> !oldFile.exists());
    }

    @Test
    void shouldEnableOptInMetricsFromSystemPropertiesWhenNotSetOnBuilder()
            throws InterruptedException {
        // given
        System.setProperty("metrics.implementation", "inmemory");
        System.setProperty("metrics.opt.direct", "true");
        System.setProperty("metrics.opt.classloading", "true");
        System.setProperty("metrics.opt.cpu", "true");
        System.setProperty("metrics.opt.codecache", "true");

        // when
        Metrics.builder()
                .appName("order-service")
                .interval(Duration.ofMillis(20L))
                .start();

        // then
        waitUntil(() -> entriesOfType("codecache") >= 2);
        assertTrue(entriesOfType("direct") >= 2);
        assertTrue(entriesOfType("classloading") >= 2);
        assertTrue(entriesOfType("cpu") >= 2);
        assertTrue(entriesOfType("codecache") >= 2);
    }

    private static long entryCount() {
        return ((InMemoryMetricsLogger) Metrics.activeLogger).entries().size();
    }

    private static long entriesOfType(final String type) {
        return ((InMemoryMetricsLogger) Metrics.activeLogger).entries().stream()
                .filter(entry -> type.equals(entry.type()))
                .count();
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
