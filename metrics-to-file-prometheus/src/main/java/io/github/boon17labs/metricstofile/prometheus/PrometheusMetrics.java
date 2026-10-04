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
 * meter tagged {@code application=<appName>}, and samples it on a fixed
 * sample interval into an in-memory buffer, which is appended as timestamped
 * lines to a daily file, {@code <logDir>/<appName>-<yyyy-MM-dd>.prom}, on a
 * (typically longer) write interval — once at start, then on every write
 * interval, on {@link #stop()}, or on demand via {@link #snapshot()}. Files
 * older than {@code keepDays} — and, if a maximum total size is set, the
 * oldest surviving files beyond that size — are deleted on the same cadence
 * as the write. Both jobs run on daemon threads.
 *
 * <p>The registry is exposed by {@link #registry()}, so an application (or
 * another metrics-to-file module) can register its own meters on it and have
 * them written to the same file.
 *
 * <p>Configure with {@link #builder()}. Anything left unset falls back to the
 * same system properties as {@code Metrics} in {@code metrics-to-file-core}
 * ({@code metrics.log.dir}, {@code metrics.sample.interval} and
 * {@code metrics.write.interval} in minutes or with a unit suffix
 * ({@code 500ms}, {@code 30s}, {@code 2m}), {@code metrics.keep.days}), then
 * to the defaults: {@code ./metrics}, 60 minutes, 60 minutes, 7 days.
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
     * Takes one sample right now and writes it immediately, without waiting
     * for the next scheduled write. Useful for marking a test phase.
     */
    public synchronized void snapshot() {
        if (!stopped) {
            writeDaemon.sampleAndFlushNow();
        }
    }

    /**
     * Stops both background threads — flushing any buffered samples first —
     * waiting (bounded) for each to actually finish so no write is left in
     * flight when this returns, then closes the registry. Safe to call more
     * than once; never throws.
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
        writeDaemon.flushNow();
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
            final Duration sampleInterval, final Duration writeInterval, final int keepDays,
            final long maxSizeMb) {
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry(appName);
        final PrometheusSnapshotter snapshotter = new PrometheusSnapshotter(
                jvmRegistry.registry(), new PrometheusFileWriter(appName, logDir));
        final PrometheusWriteDaemon writeDaemon = new PrometheusWriteDaemon(
                snapshotter, sampleInterval.toMillis(), writeInterval.toMillis());
        // Cleanup runs on the same cadence as the writing, like in core, and only
        // touches files with the same suffix the writer produces.
        final CleanupDaemon cleanupDaemon = new CleanupDaemon(logDir, appName, keepDays,
                maxSizeMb, writeInterval.toMillis(), PrometheusFileWriter.SUFFIX);
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
        private Duration sampleInterval;
        private Duration writeInterval;
        private Integer keepDays;
        private Long maxSizeMb;

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

        /** How often metrics are sampled into the in-memory buffer. Defaults to {@link #writeInterval}. */
        public Builder sampleInterval(final Duration sampleInterval) {
            this.sampleInterval = sampleInterval;
            return this;
        }

        /** How often the in-memory buffer is written to file. */
        public Builder writeInterval(final Duration writeInterval) {
            this.writeInterval = writeInterval;
            return this;
        }

        public Builder keepDays(final int keepDays) {
            this.keepDays = keepDays;
            return this;
        }

        /**
         * Maximum total size, in MB, of this app's own {@code .prom} files
         * before the oldest are deleted to make room, independently of
         * {@link #keepDays}. {@code 0} (the default) disables this check.
         */
        public Builder maxSizeMb(final long maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
            return this;
        }

        /**
         * @throws IllegalStateException if no app name was set
         */
        public PrometheusMetrics start() {
            if (appName == null) {
                throw new IllegalStateException("appName must be set before calling start()");
            }
            final Duration resolvedWriteInterval = BuilderProperties.writeInterval(writeInterval);
            final Duration resolvedSampleInterval =
                    BuilderProperties.sampleInterval(sampleInterval, resolvedWriteInterval);
            return launch(appName, BuilderProperties.logDir(logDir), resolvedSampleInterval,
                    resolvedWriteInterval, BuilderProperties.keepDays(keepDays),
                    BuilderProperties.maxSizeMb(maxSizeMb));
        }
    }
}
