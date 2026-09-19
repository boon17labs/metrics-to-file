package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.internal.file.LogFileCleaner;

import java.io.File;

/**
 * Daemon thread that deletes log files older than {@code keepDays} on
 * a fixed interval until {@link #shutdown()} is called. Only files
 * ending in the given suffix (default {@code .log}) are considered.
 */
public final class CleanupDaemon extends IntervalDaemon {

    private static final String DEFAULT_SUFFIX = ".log";

    private final File logDir;
    private final String appName;
    private final int keepDays;
    private final String suffix;

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long intervalMillis) {
        this(logDir, appName, keepDays, intervalMillis, DEFAULT_SUFFIX);
    }

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long intervalMillis, final String suffix) {
        super("metrics-to-file-cleanup", intervalMillis);
        this.logDir = logDir;
        this.appName = appName;
        this.keepDays = keepDays;
        this.suffix = suffix;
    }

    @Override
    protected void tick() {
        LogFileCleaner.clean(logDir, appName, keepDays, suffix);
    }
}
