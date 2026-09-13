package io.github.guranxp.metricstofile.internal.provider;

/**
 * Background daemons a {@link MetricsLoggerProvider}'s logger needs
 * running for it to function as intended.
 */
public final class DaemonRequirements {

    private final boolean collection;
    private final boolean cleanup;

    public DaemonRequirements(final boolean collection, final boolean cleanup) {
        this.collection = collection;
        this.cleanup = cleanup;
    }

    public boolean collection() {
        return collection;
    }

    public boolean cleanup() {
        return cleanup;
    }
}
