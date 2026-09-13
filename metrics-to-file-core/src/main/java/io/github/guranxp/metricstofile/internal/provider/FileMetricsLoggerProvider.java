package io.github.guranxp.metricstofile.internal.provider;

import io.github.guranxp.metricstofile.FileMetricsLogger;
import io.github.guranxp.metricstofile.MetricsLogger;

import java.io.File;

public final class FileMetricsLoggerProvider implements MetricsLoggerProvider {

    @Override
    public String implementationKey() {
        return "file";
    }

    @Override
    public MetricsLogger create(final String appName, final File logDir) {
        return new FileMetricsLogger(appName, logDir);
    }

    @Override
    public DaemonRequirements requirements() {
        return new DaemonRequirements(true, true);
    }
}
