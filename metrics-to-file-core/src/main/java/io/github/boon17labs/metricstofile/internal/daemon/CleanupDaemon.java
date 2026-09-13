package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.internal.file.LogFileCleaner;

import java.io.File;

/**
 * Daemon thread that deletes log files older than {@code keepDays} on
 * a fixed interval until {@link #shutdown()} is called.
 */
public final class CleanupDaemon extends IntervalDaemon {

    private final File logDir;
    private final String appName;
    private final int keepDays;

    public CleanupDaemon(final File logDir, final String appName, final int keepDays,
            final long intervalMillis) {
        super("metrics-to-file-cleanup", intervalMillis);
        this.logDir = logDir;
        this.appName = appName;
        this.keepDays = keepDays;
    }

    @Override
    void tick() {
        LogFileCleaner.clean(logDir, appName, keepDays);
    }
}
