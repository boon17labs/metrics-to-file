package io.github.guranxp.metricstofile.internal.provider;

import io.github.guranxp.metricstofile.MetricsLogger;
import io.github.guranxp.metricstofile.NoOpMetricsLogger;

import java.io.File;

public final class NoOpMetricsLoggerProvider implements MetricsLoggerProvider {

    @Override
    public String implementationKey() {
        return "noop";
    }

    @Override
    public MetricsLogger create(final String appName, final File logDir) {
        return new NoOpMetricsLogger();
    }

    @Override
    public DaemonRequirements requirements() {
        return new DaemonRequirements(false, false);
    }
}
