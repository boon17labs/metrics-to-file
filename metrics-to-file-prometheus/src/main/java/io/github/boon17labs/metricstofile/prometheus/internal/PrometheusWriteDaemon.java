package io.github.boon17labs.metricstofile.prometheus.internal;

import io.github.boon17labs.metricstofile.internal.daemon.IntervalDaemon;

/**
 * Daemon thread that takes a {@link PrometheusSnapshotter#snapshot()
 * snapshot} on a fixed interval until {@link #shutdown()} is called. The
 * first snapshot is taken immediately at start, then one per interval —
 * the same rhythm as the daemons in {@code metrics-to-file-core}, whose
 * {@link IntervalDaemon} this extends.
 *
 * <p>The snapshotter never throws, so a failing snapshot is warned about
 * on stderr and the daemon carries on with the next tick.
 */
public final class PrometheusWriteDaemon extends IntervalDaemon {

    private final PrometheusSnapshotter snapshotter;

    public PrometheusWriteDaemon(final PrometheusSnapshotter snapshotter,
            final long intervalMillis) {
        super("metrics-to-file-prometheus-writer", intervalMillis);
        this.snapshotter = snapshotter;
    }

    @Override
    protected void tick() {
        snapshotter.snapshot();
    }
}
