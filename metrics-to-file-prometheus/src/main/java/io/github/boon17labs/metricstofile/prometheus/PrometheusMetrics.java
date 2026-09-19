package io.github.boon17labs.metricstofile.prometheus;

import io.github.boon17labs.metricstofile.internal.config.BuilderProperties;
import io.github.boon17labs.metricstofile.internal.daemon.CleanupDaemon;
import io.github.boon17labs.metricstofile.prometheus.internal.JvmMetricsRegistry;
import io.github.boon17labs.metricstofile.prometheus.internal.PrometheusFileWriter;
import io.github.boon17labs.metricstofile.prometheus.internal.PrometheusSnapshotter;
import io.github.boon17labs.metricstofile.prometheus.internal.PrometheusWriteDaemon;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.File;
import java.time.Duration;

/**
 * Entry point for the Prometheus-format side of metrics-to-file.
 * {@code PrometheusMetrics.start("app-name")} creates a Micrometer registry
 * pre-populated with the default JVM metrics (memory, threads, GC), every
 * meter tagged {@code application=<appName>}, and appends a timestamped
 * snapshot of it to a daily file, {@code <logDir>/<appName>-<yyyy-MM-dd>.prom},
 * once at start and then once per interval. Files older than {@code keepDays}
 * are deleted automatically. Both jobs run on daemon threads.
 *
 * <p>The registry is exposed by {@link #registry()}, so an application (or
 * another metrics-to-file module) can register its own meters on it and have
 * them written to the same file.
 *
 * <p>Configure with {@link #builder()}. Anything left unset falls back to the
 * same system properties as {@code Metrics} in {@code metrics-to-file-core}
 * ({@code metrics.log.dir}, {@code metrics.interval} in minutes,
 * {@code metrics.keep.days}), then to the defaults: {@code ./metrics}, 60
 * minutes, 7 days.
 *
 * <p>A JVM shutdown hook calls {@link #stop()} automatically, so an app that
 * never calls it explicitly still shuts down cleanly. The hook is removed
 * again by an explicit {@code stop()}.
 *
 * <p>A metrics problem never affects the host application: failures are
 * warned about on stderr and never thrown, the one exception being a missing
 * app name, which is a programming error. Each call to {@code start} creates
 * an independent instance with its own registry and threads, so start only
 * one per app name — two would append to the same daily file.
 */
public final class PrometheusMetrics {

    private static final long SHUTDOWN_JOIN_TIMEOUT_MILLIS = 5_000L;

    private final JvmMetricsRegistry jvmRegistry;
    private final PrometheusWriteDaemon writeDaemon;
    private final CleanupDaemon cleanupDaemon;
    private final Thread shutdownHook =
            new Thread(this::stop, "metrics-to-file-prometheus-shutdown-hook");
    private boolean stopped;

    private PrometheusMetrics(final JvmMetricsRegistry jvmRegistry,
            final PrometheusWriteDaemon writeDaemon, final CleanupDaemon cleanupDaemon) {
        this.jvmRegistry = jvmRegistry;
        this.writeDaemon = writeDaemon;
        this.cleanupDaemon = cleanupDaemon;
    }

    /** Starts with the defaults, or the {@code metrics.*} system properties where set. */
    public static PrometheusMetrics start(final String appName) {
        return builder().appName(appName).start();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The registry holding the JVM metrics. Register your own meters on it to
     * have them written too. After {@link #stop()} it is closed.
     */
    public MeterRegistry registry() {
        return jvmRegistry.registry();
    }

    /**
     * Stops both background threads, waiting (bounded) for each to actually
     * finish so no write is left in flight when this returns, then closes the
     * registry. Safe to call more than once; never throws.
     */
    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        removeShutdownHook();
        // Stop the threads before closing the registry, so no snapshot is
        // ever taken from a closed registry.
        writeDaemon.shutdown();
        cleanupDaemon.shutdown();
        joinQuietly(writeDaemon);
        joinQuietly(cleanupDaemon);
        jvmRegistry.close();
    }

    // Package-private so tests can reach the hook; not public API.
    Thread shutdownHook() {
        return shutdownHook;
    }

    private void registerShutdownHook() {
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (final IllegalStateException e) {
            // The JVM is already shutting down, so there is nothing to hook into.
        }
    }

    private void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (final IllegalStateException e) {
            // Hooks cannot be removed once the JVM is shutting down. That is exactly
            // when the hook itself calls stop(), so this is expected and harmless.
        }
    }

    private static void joinQuietly(final Thread thread) {
        try {
            thread.join(SHUTDOWN_JOIN_TIMEOUT_MILLIS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PrometheusMetrics launch(final String appName, final File logDir,
            final Duration interval, final int keepDays) {
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry(appName);
        final PrometheusSnapshotter snapshotter = new PrometheusSnapshotter(
                jvmRegistry.registry(), new PrometheusFileWriter(appName, logDir));
        final PrometheusWriteDaemon writeDaemon =
                new PrometheusWriteDaemon(snapshotter, interval.toMillis());
        // Cleanup runs on the same interval as the writing, like in core, and only
        // touches files with the same suffix the writer produces.
        final CleanupDaemon cleanupDaemon = new CleanupDaemon(
                logDir, appName, keepDays, interval.toMillis(), PrometheusFileWriter.SUFFIX);
        writeDaemon.start();
        cleanupDaemon.start();
        final PrometheusMetrics metrics = new PrometheusMetrics(jvmRegistry, writeDaemon, cleanupDaemon);
        metrics.registerShutdownHook();
        return metrics;
    }

    /**
     * Configures and starts {@link PrometheusMetrics}. Obtain via
     * {@link PrometheusMetrics#builder()}.
     */
    public static final class Builder {

        private String appName;
        private File logDir;
        private Duration interval;
        private Integer keepDays;

        private Builder() {
        }

        public Builder appName(final String appName) {
            this.appName = appName;
            return this;
        }

        public Builder logDir(final String logDir) {
            this.logDir = new File(logDir);
            return this;
        }

        public Builder interval(final Duration interval) {
            this.interval = interval;
            return this;
        }

        public Builder keepDays(final int keepDays) {
            this.keepDays = keepDays;
            return this;
        }

        /**
         * @throws IllegalStateException if no app name was set
         */
        public PrometheusMetrics start() {
            if (appName == null) {
                throw new IllegalStateException("appName must be set before calling start()");
            }
            return launch(appName, BuilderProperties.logDir(logDir),
                    BuilderProperties.interval(interval), BuilderProperties.keepDays(keepDays));
        }
    }
}
