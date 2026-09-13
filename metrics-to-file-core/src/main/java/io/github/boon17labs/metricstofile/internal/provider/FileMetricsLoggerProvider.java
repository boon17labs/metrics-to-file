package io.github.boon17labs.metricstofile.internal.provider;

import io.github.boon17labs.metricstofile.FileMetricsLogger;
import io.github.boon17labs.metricstofile.MetricsLogger;

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
