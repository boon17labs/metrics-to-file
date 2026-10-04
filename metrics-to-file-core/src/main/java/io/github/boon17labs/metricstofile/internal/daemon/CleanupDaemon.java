package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.internal.file.LogFileCleaner;

import java.io.File;

/**
 * Daemon thread that deletes log files older than {@code keepDays},
 * and (if {@code maxSizeMb} is set) the oldest surviving files beyond
 * that total size, on a fixed interval until {@link #shutdown()} is
 * called. Only files ending in the given suffix (default {@code .log})
 * are considered.
 */
public final class CleanupDaemon extends IntervalDaemon {

    private static final String DEFAULT_SUFFIX = ".log";
    private static final long DEFAULT_MAX_SIZE_MB = 0L;

    private final File logDir;
    private final String appName;
    private final int keepDays;
    private final long maxSizeMb;
    private final String suffix;

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long intervalMillis) {
        this(logDir, appName, keepDays, intervalMillis, DEFAULT_SUFFIX);
    }

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long intervalMillis, final String suffix) {
        this(logDir, appName, keepDays, DEFAULT_MAX_SIZE_MB, intervalMillis, suffix);
    }

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long maxSizeMb, final long intervalMillis) {
        this(logDir, appName, keepDays, maxSizeMb, intervalMillis, DEFAULT_SUFFIX);
    }

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long maxSizeMb, final long intervalMillis, final String suffix) {
        super("metrics-to-file-cleanup", intervalMillis);
        this.logDir = logDir;
        this.appName = appName;
        this.keepDays = keepDays;
        this.maxSizeMb = maxSizeMb;
        this.suffix = suffix;
    }

    @Override
    protected void tick() {
        LogFileCleaner.clean(logDir, appName, keepDays, maxSizeMb, suffix);
    }
}
