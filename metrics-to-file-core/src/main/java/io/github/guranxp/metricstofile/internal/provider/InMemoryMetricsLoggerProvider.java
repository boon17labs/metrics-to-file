package io.github.guranxp.metricstofile.internal.provider;

import io.github.guranxp.metricstofile.InMemoryMetricsLogger;
import io.github.guranxp.metricstofile.MetricsLogger;

import java.io.File;

public final class InMemoryMetricsLoggerProvider implements MetricsLoggerProvider {

    @Override
    public String implementationKey() {
        return "inmemory";
    }

    @Override
    public MetricsLogger create(final String appName, final File logDir) {
        return new InMemoryMetricsLogger();
    }

    @Override
    public DaemonRequirements requirements() {
        return new DaemonRequirements(true, false);
    }
}
