package io.github.boon17labs.metricstofile;

import io.github.boon17labs.metricstofile.internal.config.BuilderProperties;
import io.github.boon17labs.metricstofile.internal.config.MetricsOptions;
import io.github.boon17labs.metricstofile.internal.daemon.CleanupDaemon;
import io.github.boon17labs.metricstofile.internal.daemon.MetricsCollectionDaemon;
import io.github.boon17labs.metricstofile.internal.provider.MetricsLoggerResolver;
import io.github.boon17labs.metricstofile.internal.provider.ResolvedLogger;

import java.io.File;
import java.time.Duration;
import java.util.Map;

/**
 * Entry point for metrics-to-file. {@code Metrics.start("app-name")} resolves
 * and activates a {@link MetricsLogger} implementation based on the
 * {@code metrics.implementation} system property, then starts whichever
 * daemon threads that implementation actually needs — a collection
 * daemon logging metrics on a fixed interval, and (only for a
 * file-backed logger) a cleanup daemon deleting old log files. The
 * default {@link NoOpMetricsLogger} needs neither, so an unconfigured
 * app runs no background threads at all. {@code Metrics.stop()} shuts
 * down whichever daemons are running — waiting (bounded) for each to
 * actually terminate before returning, so no write is left in flight
 * — and releases the logger. Safe to call repeatedly; never throws to
 * the caller. Use {@link #builder()} to configure the log directory,
 * interval, retention, or opt-in metrics. A JVM shutdown hook calls
 * {@link #stop()} automatically, so an app that never calls it
 * explicitly still shuts down cleanly.
 */
public final class Metrics {

    private static final long SHUTDOWN_JOIN_TIMEOUT_MILLIS = 5_000L;

    static volatile MetricsLogger activeLogger = new NoOpMetricsLogger();
    static volatile MetricsCollectionDaemon collectionDaemon;
    static volatile CleanupDaemon cleanupDaemon;

    static final Thread shutdownHook = new Thread(Metrics::stop, "metrics-to-file-shutdown-hook");

    static {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private Metrics() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void start(final String appName) {
        builder().appName(appName).start();
    }

    /**
     * Logs a custom metric group through whichever {@link MetricsLogger}
     * {@link #start} (or {@link Builder#start()}) activated. A no-op
     * before {@code start()} is called, since the active logger defaults
     * to {@link NoOpMetricsLogger}.
     */
    public static void log(final String type, final Map<String, Object> values) {
        activeLogger.log(type, values);
    }

    public static synchronized void stop() {
        if (collectionDaemon != null) {
            collectionDaemon.shutdown();
            joinQuietly(collectionDaemon);
            collectionDaemon = null;
        }
        if (cleanupDaemon != null) {
            cleanupDaemon.shutdown();
            joinQuietly(cleanupDaemon);
            cleanupDaemon = null;
        }
        activeLogger.close();
        activeLogger = new NoOpMetricsLogger();
    }

    private static void joinQuietly(final Thread thread) {
        try {
            thread.join(SHUTDOWN_JOIN_TIMEOUT_MILLIS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static synchronized void apply(final String appName, final File logDir,
            final Duration interval, final int keepDays, final MetricsOptions options) {
        final ResolvedLogger resolved = MetricsLoggerResolver.resolve(appName, logDir);
        activeLogger = resolved.logger();
        if (resolved.requirements().collection()) {
            collectionDaemon = new MetricsCollectionDaemon(activeLogger, interval.toMillis(), options);
            collectionDaemon.start();
        }
        if (resolved.requirements().cleanup()) {
            cleanupDaemon = new CleanupDaemon(logDir, appName, keepDays, interval.toMillis());
            cleanupDaemon.start();
        }
    }

    /**
     * Configures and starts metrics-to-file. Obtain via {@link Metrics#builder()}.
     */
    public static final class Builder {

        private String appName;
        private File logDir;
        private Duration interval;
        private Integer keepDays;
        private Boolean directMemory;
        private Boolean classLoading;
        private Boolean cpu;
        private Boolean codeCache;

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

        public Builder withDirectMemory() {
            this.directMemory = true;
            return this;
        }

        public Builder withClassLoading() {
            this.classLoading = true;
            return this;
        }

        public Builder withCpu() {
            this.cpu = true;
            return this;
        }

        public Builder withCodeCache() {
            this.codeCache = true;
            return this;
        }

        public void start() {
            if (appName == null) {
                throw new IllegalStateException("appName must be set before calling start()");
            }
            final MetricsOptions options = new MetricsOptions(
                    BuilderProperties.flag(directMemory, "metrics.opt.direct"),
                    BuilderProperties.flag(classLoading, "metrics.opt.classloading"),
                    BuilderProperties.flag(cpu, "metrics.opt.cpu"),
                    BuilderProperties.flag(codeCache, "metrics.opt.codecache"));
            apply(appName, BuilderProperties.logDir(logDir), BuilderProperties.interval(interval),
                    BuilderProperties.keepDays(keepDays), options);
        }
    }
}
