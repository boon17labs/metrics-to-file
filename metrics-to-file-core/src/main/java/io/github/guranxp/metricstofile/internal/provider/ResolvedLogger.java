package io.github.guranxp.metricstofile.internal.provider;

import io.github.guranxp.metricstofile.MetricsLogger;

/**
 * A resolved {@link MetricsLogger} bundled with the daemon
 * requirements its provider declared for it.
 */
public final class ResolvedLogger {

    private final MetricsLogger logger;
    private final DaemonRequirements requirements;

    public ResolvedLogger(final MetricsLogger logger, final DaemonRequirements requirements) {
        this.logger = logger;
        this.requirements = requirements;
    }

    public MetricsLogger logger() {
        return logger;
    }

    public DaemonRequirements requirements() {
        return requirements;
    }
}
