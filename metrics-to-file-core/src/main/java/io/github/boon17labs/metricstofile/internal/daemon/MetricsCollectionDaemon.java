package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.FileMetricsLogger;
import io.github.boon17labs.metricstofile.MetricsLogger;
import io.github.boon17labs.metricstofile.internal.buffer.TimestampedSample;
import io.github.boon17labs.metricstofile.internal.collect.ClassLoadingMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.CodeCacheMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.CpuMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.DirectMemoryMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.GcMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.HeapMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.MetaspaceMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.MetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.ProcessMetricsCollector;
import io.github.boon17labs.metricstofile.internal.collect.ThreadMetricsCollector;
import io.github.boon17labs.metricstofile.internal.config.MetricsOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Daemon thread that samples each default and opt-in metric group on a
 * fixed sample interval, buffering the raw, timestamped readings in
 * memory until they are written out on a (typically longer) write
 * interval, on {@link #shutdown()} or on demand via
 * {@link #sampleAndFlushNow()} — this is what lets an app sample every
 * second while still only touching the disk every few minutes.
 *
 * <p>The buffer is capped so a write interval set too long (or a logger
 * that stops accepting writes) cannot grow memory use without bound; it
 * is flushed early if that cap is reached.
 */
public final class MetricsCollectionDaemon extends IntervalDaemon {

    // A rough estimate of a few hundred bytes per buffered sample keeps this
    // within the "a few MB" the design notes call for.
    private static final int MAX_BUFFERED_SAMPLES = 5_000;

    private final MetricsLogger logger;
    private final List<MetricsCollector> collectors;
    private final long writeIntervalMillis;
    private final List<TimestampedSample> buffer = new ArrayList<>();
    private long lastFlushMillis;

    public MetricsCollectionDaemon(final MetricsLogger logger, final long intervalMillis,
            final MetricsOptions options) {
        this(logger, intervalMillis, intervalMillis, options);
    }

    public MetricsCollectionDaemon(final MetricsLogger logger, final long sampleIntervalMillis,
            final long writeIntervalMillis, final MetricsOptions options) {
        super("metrics-to-file-collector", sampleIntervalMillis);
        this.logger = logger;
        this.writeIntervalMillis = writeIntervalMillis;
        this.collectors = buildCollectors(options);
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

    /** Takes one sample right now and flushes it immediately. Used by {@code Metrics.snapshot()}. */
    public synchronized void sampleAndFlushNow() {
        sample();
        flush();
    }

    /**
     * Writes out whatever is currently buffered, with no new sample taken.
     * Used for the final snapshot on {@code Metrics.stop()} and the JVM
     * shutdown hook, so a clean shutdown never loses buffered samples.
     */
    public synchronized void flushNow() {
        flush();
    }

    private synchronized void sample() {
        final Instant now = Instant.now();
        for (final MetricsCollector collector : collectors) {
            for (final Map<String, Object> values : collector.collect()) {
                buffer.add(new TimestampedSample(now, collector.type(), values));
                if (buffer.size() >= MAX_BUFFERED_SAMPLES) {
                    flush();
                }
            }
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
        final List<TimestampedSample> drained = new ArrayList<>(buffer);
        buffer.clear();
        if (logger instanceof FileMetricsLogger) {
            ((FileMetricsLogger) logger).logBatch(drained);
        } else {
            for (final TimestampedSample sample : drained) {
                logger.log(sample.type(), sample.values());
            }
        }
    }

    private static List<MetricsCollector> buildCollectors(final MetricsOptions options) {
        final List<MetricsCollector> collectors = new ArrayList<>();
        collectors.add(new HeapMetricsCollector());
        collectors.add(new ThreadMetricsCollector());
        collectors.add(new MetaspaceMetricsCollector());
        collectors.add(new GcMetricsCollector());
        if (options.directMemory()) {
            collectors.add(new DirectMemoryMetricsCollector());
        }
        if (options.classLoading()) {
            collectors.add(new ClassLoadingMetricsCollector());
        }
        if (options.cpu()) {
            collectors.add(new CpuMetricsCollector());
        }
        if (options.codeCache()) {
            collectors.add(new CodeCacheMetricsCollector());
        }
        if (options.processMemory()) {
            collectors.add(new ProcessMetricsCollector());
        }
        return collectors;
    }
}
