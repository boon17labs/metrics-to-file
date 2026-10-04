package io.github.boon17labs.metricstofile.prometheus.internal;

import io.github.boon17labs.metricstofile.internal.daemon.IntervalDaemon;

import java.util.ArrayList;
import java.util.List;

/**
 * Daemon thread that takes a {@link PrometheusSnapshotter#sample()
 * sample} on a fixed sample interval, buffering the lines in memory
 * until they are written out on a (typically longer) write interval,
 * on {@link #shutdown()} or on demand via {@link #sampleAndFlushNow()}
 * — this is what lets an app sample every second while still only
 * touching the disk every few minutes. The first sample is taken
 * immediately at start, then one per sample interval — the same
 * rhythm as the daemons in {@code metrics-to-file-core}, whose
 * {@link IntervalDaemon} this extends.
 *
 * <p>The buffer is capped so a write interval set too long cannot grow
 * memory use without bound; it is flushed early if that cap is reached.
 * Sampling never throws, so a failing sample is warned about on stderr
 * (by {@link PrometheusSnapshotter}) and the daemon carries on with the
 * next tick.
 */
public final class PrometheusWriteDaemon extends IntervalDaemon {

    // A rough estimate of a few hundred bytes per buffered line keeps this
    // within the "a few MB" the design notes call for.
    private static final int MAX_BUFFERED_LINES = 5_000;

    private final PrometheusSnapshotter snapshotter;
    private final long writeIntervalMillis;
    private final List<String> buffer = new ArrayList<>();
    private long lastFlushMillis;

    public PrometheusWriteDaemon(final PrometheusSnapshotter snapshotter,
            final long intervalMillis) {
        this(snapshotter, intervalMillis, intervalMillis);
    }

    public PrometheusWriteDaemon(final PrometheusSnapshotter snapshotter,
            final long sampleIntervalMillis, final long writeIntervalMillis) {
        super("metrics-to-file-prometheus-writer", sampleIntervalMillis);
        this.snapshotter = snapshotter;
        this.writeIntervalMillis = writeIntervalMillis;
        // So the very first tick always flushes immediately, matching the
        // existing "first sample written right away" behaviour, rather than
        // waiting a full write interval before the first write.
        this.lastFlushMillis = System.currentTimeMillis() - writeIntervalMillis;
    }

    @Override
    protected void tick() {
        sample();
        maybeFlush();
    }

    /** Takes one sample right now and flushes it immediately. Used by {@code PrometheusMetrics.snapshot()}. */
    public synchronized void sampleAndFlushNow() {
        sample();
        flush();
    }

    /**
     * Writes out whatever is currently buffered, with no new sample taken.
     * Used for the final snapshot on {@code PrometheusMetrics.stop()} and
     * the JVM shutdown hook, so a clean shutdown never loses buffered lines.
     */
    public synchronized void flushNow() {
        flush();
    }

    private synchronized void sample() {
        buffer.addAll(snapshotter.sample());
        if (buffer.size() >= MAX_BUFFERED_LINES) {
            flush();
        }
    }

    private synchronized void maybeFlush() {
        if (System.currentTimeMillis() - lastFlushMillis >= writeIntervalMillis) {
            flush();
        }
    }

    private synchronized void flush() {
        lastFlushMillis = System.currentTimeMillis();
        if (buffer.isEmpty()) {
            return;
        }
        final List<String> drained = new ArrayList<>(buffer);
        buffer.clear();
        snapshotter.flush(drained);
    }
}
