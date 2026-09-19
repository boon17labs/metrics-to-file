package io.github.boon17labs.metricstofile.prometheus;

import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsIT {

    private static final Duration SHORT_INTERVAL = Duration.ofMillis(20L);
    private static final long POLL_TIMEOUT_MILLIS = 5_000L;
    private static final String WRITER_THREAD = "metrics-to-file-prometheus-writer";
    private static final String CLEANUP_THREAD = "metrics-to-file-cleanup";

    /** Whatever a test started, so a failing test never leaks threads into the next one. */
    private PrometheusMetrics started;

    @AfterEach
    void tearDown() {
        if (started != null) {
            started.stop();
            started = null;
        }
        System.clearProperty("metrics.log.dir");
        System.clearProperty("metrics.interval");
        System.clearProperty("metrics.keep.days");
    }

    // ---- what start() produces ------------------------------------------------------

    @Test
    void shouldWriteTaggedTimestampedSamplesShortlyAfterStart(@TempDir final File logDir)
            throws Exception {
        // given / when
        final PrometheusMetrics metrics = start(builder(logDir));
        assertTrue(waitUntil(() -> lineCount(promFile(logDir)) >= 1),
                "no snapshot appeared shortly after start");
        metrics.stop();

        // then
        boolean heapLineFound = false;
        for (final String line : readLinesOf(promFile(logDir))) {
            assertTrue(line.contains("application=\"order-service\""), line);
            assertTrue(line.matches(".* \\d{13}"), "no millisecond timestamp: " + line);
            assertFalse(line.startsWith("#"), line);
            if (line.startsWith("jvm_memory_used_bytes{application=\"order-service\",area=\"heap\"")) {
                heapLineFound = true;
            }
        }
        assertTrue(heapLineFound, "no heap memory sample");
    }

    @Test
    void shouldExposeTheRegistryForRegisteringCustomMeters(@TempDir final File logDir)
            throws Exception {
        // given
        final PrometheusMetrics metrics = start(builder(logDir));

        // when: a meter is registered after start; a later snapshot must pick it up
        // (supplier-based gauge: the registry holds the supplier strongly)
        Gauge.builder("queue.size", () -> 7).register(metrics.registry());

        // then
        assertTrue(waitUntil(() -> fileContains(promFile(logDir),
                "queue_size{application=\"order-service\"} 7.0 ")),
                "custom meter never appeared in the file");
    }

    @Test
    void shouldStartFromTheShortcutUsingSystemPropertyLogDir(@TempDir final File logDir)
            throws Exception {
        // given
        System.setProperty("metrics.log.dir", logDir.getAbsolutePath());

        // when
        started = PrometheusMetrics.start("order-service");

        // then
        assertTrue(waitUntil(() -> promFile(logDir).isFile()),
                "no file in the directory from metrics.log.dir");
    }

    @Test
    void shouldPreferBuilderLogDirOverSystemProperty(@TempDir final File builderDir,
            @TempDir final File propertyDir) throws Exception {
        // given
        System.setProperty("metrics.log.dir", propertyDir.getAbsolutePath());

        // when
        start(builder(builderDir));

        // then
        assertTrue(waitUntil(() -> promFile(builderDir).isFile()));
        assertFalse(promFile(propertyDir).exists());
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Test
    void shouldStartWriterAndCleanupAsDaemonThreads(@TempDir final File logDir) {
        // given
        final int writersBefore = liveThreadsNamed(WRITER_THREAD);
        final int cleanupsBefore = liveThreadsNamed(CLEANUP_THREAD);

        // when
        start(builder(logDir));

        // then
        assertEquals(writersBefore + 1, liveThreadsNamed(WRITER_THREAD));
        assertEquals(cleanupsBefore + 1, liveThreadsNamed(CLEANUP_THREAD));
        assertTrue(allLiveThreadsNamed(WRITER_THREAD).stream().allMatch(Thread::isDaemon));
        assertTrue(allLiveThreadsNamed(CLEANUP_THREAD).stream().allMatch(Thread::isDaemon));
    }

    @Test
    void shouldHaveTerminatedBothThreadsBeforeStopReturns(@TempDir final File logDir) {
        // given
        final int writersBefore = liveThreadsNamed(WRITER_THREAD);
        final int cleanupsBefore = liveThreadsNamed(CLEANUP_THREAD);
        final PrometheusMetrics metrics = start(builder(logDir));

        // when
        metrics.stop();

        // then: checked immediately, with no waiting
        assertEquals(writersBefore, liveThreadsNamed(WRITER_THREAD));
        assertEquals(cleanupsBefore, liveThreadsNamed(CLEANUP_THREAD));
    }

    @Test
    void shouldCloseTheRegistryOnStop(@TempDir final File logDir) {
        // given
        final PrometheusMetrics metrics = start(builder(logDir));
        assertFalse(metrics.registry().isClosed());

        // when
        metrics.stop();

        // then
        assertTrue(metrics.registry().isClosed());
    }

    @Test
    void shouldNotThrowWhenStoppedTwice(@TempDir final File logDir) {
        // given
        final PrometheusMetrics metrics = start(builder(logDir));

        // when / then
        assertDoesNotThrow(() -> {
            metrics.stop();
            metrics.stop();
        });
    }

    @Test
    void shouldNotWriteAnythingAfterStopHasReturned(@TempDir final File logDir) throws Exception {
        // given
        final PrometheusMetrics metrics = start(builder(logDir));
        assertTrue(waitUntil(() -> lineCount(promFile(logDir)) >= 1));

        // when
        metrics.stop();
        final int linesAtStop = lineCount(promFile(logDir));
        Thread.sleep(SHORT_INTERVAL.toMillis() * 10);

        // then
        assertEquals(linesAtStop, lineCount(promFile(logDir)));
    }

    // ---- cleanup of old files --------------------------------------------------------

    @Test
    void shouldDeleteOldPromFilesButNotOtherFilesAccordingToBuilderKeepDays(
            @TempDir final File logDir) throws Exception {
        // given: 5 days old is past keepDays(3) but within the default of 7
        final File oldProm = createFile(logDir, "order-service-" + LocalDate.now().minusDays(5) + ".prom");
        final File oldLog = createFile(logDir, "order-service-" + LocalDate.now().minusDays(5) + ".log");

        // when
        start(builder(logDir).keepDays(3));

        // then
        assertTrue(waitUntil(() -> !oldProm.exists()), "old .prom file was not cleaned up");
        assertTrue(oldLog.exists(), "the cleanup must leave files with other suffixes alone");
    }

    @Test
    void shouldUseKeepDaysFromSystemPropertyWhenNotSetOnBuilder(@TempDir final File logDir)
            throws Exception {
        // given: 40 days old is past the property's 30, 10 days old is within it
        // (but would be past the default of 7, so keeping it proves the property applies)
        System.setProperty("metrics.keep.days", "30");
        final File veryOld = createFile(logDir, "order-service-" + LocalDate.now().minusDays(40) + ".prom");
        final File recent = createFile(logDir, "order-service-" + LocalDate.now().minusDays(10) + ".prom");

        // when
        start(builder(logDir));

        // then
        assertTrue(waitUntil(() -> !veryOld.exists()), "cleanup never ran");
        assertTrue(recent.exists(), "metrics.keep.days=30 must keep a 10 day old file");
    }

    // ---- error handling --------------------------------------------------------------

    @Test
    void shouldThrowWhenBuilderStartedWithoutAppName() {
        // when / then
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> PrometheusMetrics.builder().start());
        assertTrue(e.getMessage().contains("appName"));
    }

    @Test
    void shouldNotThrowAndReturnAWorkingHandleWhenLogDirIsUnusable(@TempDir final File tempDir)
            throws Exception {
        // given: a log directory that can never be created, its parent being a regular file
        final File blockingFile = createFile(tempDir, "not-a-directory");
        final File unusableDir = new File(blockingFile, "metrics");
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));

        // when / then
        try {
            final PrometheusMetrics metrics = assertDoesNotThrow(() -> start(builder(unusableDir)));
            assertNotNull(metrics.registry());
            assertTrue(waitUntil(() -> new String(captured.toByteArray(), StandardCharsets.UTF_8)
                    .contains("[metrics-to-file] failed to write prometheus metrics")),
                    "expected a warning on stderr");
            assertFalse(metrics.registry().isClosed(), "the registry must stay usable");
        } finally {
            System.setErr(originalErr);
        }
    }

    // ---- helpers ---------------------------------------------------------------------

    private static PrometheusMetrics.Builder builder(final File logDir) {
        return PrometheusMetrics.builder()
                .appName("order-service")
                .logDir(logDir.getAbsolutePath())
                .interval(SHORT_INTERVAL);
    }

    private PrometheusMetrics start(final PrometheusMetrics.Builder builder) {
        started = builder.start();
        return started;
    }

    private static File promFile(final File logDir) {
        return new File(logDir, "order-service-" + LocalDate.now() + ".prom");
    }

    private static File createFile(final File dir, final String name) throws IOException {
        final File file = new File(dir, name);
        assertTrue(file.createNewFile());
        return file;
    }

    /** Number of lines in the file, or 0 if it is missing or cannot be read yet. */
    private static int lineCount(final File file) {
        try {
            return file.isFile() ? Files.readAllLines(file.toPath()).size() : 0;
        } catch (final IOException e) {
            return 0;
        }
    }

    private static boolean fileContains(final File file, final String text) {
        try {
            return file.isFile() && new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8).contains(text);
        } catch (final IOException e) {
            return false;
        }
    }

    private static List<String> readLinesOf(final File file) throws IOException {
        return Files.readAllLines(file.toPath());
    }

    private static List<Thread> allLiveThreadsNamed(final String name) {
        final List<Thread> matching = new ArrayList<>();
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && name.equals(thread.getName())) {
                matching.add(thread);
            }
        }
        return matching;
    }

    private static int liveThreadsNamed(final String name) {
        return allLiveThreadsNamed(name).size();
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
