package io.github.boon17labs.metricstofile.internal.provider;

import io.github.boon17labs.metricstofile.InMemoryMetricsLogger;
import io.github.boon17labs.metricstofile.MetricsLogger;

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
