package io.github.boon17labs.metricstofile.prometheus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsShutdownHookIT {

    private static final long CHILD_TIMEOUT_SECONDS = 30L;
    private static final String WRITER_THREAD = "metrics-to-file-prometheus-writer";
    private static final String CLEANUP_THREAD = "metrics-to-file-cleanup";

    /** Whatever a test started, so a failing test never leaks threads or hooks into the next one. */
    private PrometheusMetrics started;

    @AfterEach
    void tearDown() {
        if (started != null) {
            started.stop();
            started = null;
        }
    }

    @Test
    void shouldRegisterAShutdownHookWithTheRuntimeOnStart(@TempDir final File logDir) {
        // given / when
        final PrometheusMetrics metrics = start(logDir);

        // then: removeShutdownHook returns true only if the hook was registered
        assertTrue(Runtime.getRuntime().removeShutdownHook(metrics.shutdownHook()));

        // restore, so stop() and a real JVM exit behave as they would normally
        Runtime.getRuntime().addShutdownHook(metrics.shutdownHook());
    }

    @Test
    void shouldRemoveTheShutdownHookWhenStopped(@TempDir final File logDir) {
        // given
        final PrometheusMetrics metrics = start(logDir);

        // when
        metrics.stop();

        // then: nothing left to remove, so instances don't leak hooks
        assertFalse(Runtime.getRuntime().removeShutdownHook(metrics.shutdownHook()));
    }

    @Test
    void shouldStopViaTheShutdownHookWhenItRuns(@TempDir final File logDir) {
        // given
        final int writersBefore = liveThreadsNamed(WRITER_THREAD);
        final int cleanupsBefore = liveThreadsNamed(CLEANUP_THREAD);
        final PrometheusMetrics metrics = start(logDir);

        // when
        metrics.shutdownHook().run();

        // then
        assertTrue(metrics.registry().isClosed());
        assertEquals(writersBefore, liveThreadsNamed(WRITER_THREAD));
        assertEquals(cleanupsBefore, liveThreadsNamed(CLEANUP_THREAD));
    }

    @Test
    void shouldNotThrowWhenTheHookRunsAfterAnExplicitStop(@TempDir final File logDir) {
        // given
        final PrometheusMetrics metrics = start(logDir);
        metrics.stop();

        // when / then
        assertDoesNotThrow(() -> metrics.shutdownHook().run());
    }

    @Test
    void shouldShutDownCleanlyWhenTheJvmExitsWithoutStop(@TempDir final File dir) throws Exception {
        // given: a child JVM that starts PrometheusMetrics and exits without stopping it
        final File logDir = new File(dir, "metrics");
        final File stderr = new File(dir, "stderr.txt");
        final Process child = new ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-cp", System.getProperty("java.class.path"),
                PrometheusMetricsShutdownMain.class.getName(),
                logDir.getAbsolutePath())
                .redirectOutput(new File(dir, "stdout.txt"))
                .redirectError(stderr)
                .start();

        // when
        final boolean exited = child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!exited) {
            child.destroyForcibly();
        }

        // then: it exited by itself, normally, and the hook ran without complaining. The
        // hook calls stop() while the JVM is shutting down, where Runtime.removeShutdownHook
        // throws IllegalStateException — that must be handled, not printed as a stack trace.
        assertTrue(exited, "the child JVM did not exit");
        final String childStderr = new String(Files.readAllBytes(stderr.toPath()), StandardCharsets.UTF_8);
        assertEquals(0, child.exitValue(), childStderr);
        assertFalse(childStderr.contains("Exception"), childStderr);
        assertFalse(childStderr.contains("[metrics-to-file]"), childStderr);
        assertTrue(new File(logDir, "order-service-" + LocalDate.now() + ".prom").isFile(),
                "the child never wrote a snapshot");
    }

    private PrometheusMetrics start(final File logDir) {
        started = PrometheusMetrics.builder()
                .appName("order-service")
                .logDir(logDir.getAbsolutePath())
                .interval(Duration.ofMillis(20L))
                .start();
        return started;
    }

    private static int liveThreadsNamed(final String name) {
        int count = 0;
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && name.equals(thread.getName())) {
                count++;
            }
        }
        return count;
    }
}
