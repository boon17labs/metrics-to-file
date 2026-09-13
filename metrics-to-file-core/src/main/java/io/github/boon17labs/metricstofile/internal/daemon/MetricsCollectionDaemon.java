package io.github.boon17labs.metricstofile.internal.daemon;

import io.github.boon17labs.metricstofile.MetricsLogger;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Daemon thread that logs each default and opt-in metric group on a
 * fixed interval until {@link #shutdown()} is called.
 */
public final class MetricsCollectionDaemon extends IntervalDaemon {

    private final MetricsLogger logger;
    private final List<MetricsCollector> collectors;

    public MetricsCollectionDaemon(final MetricsLogger logger, final long intervalMillis,
            final MetricsOptions options) {
        super("metrics-to-file-collector", intervalMillis);
        this.logger = logger;
        this.collectors = buildCollectors(options);
    }

    @Override
    void tick() {
        for (final MetricsCollector collector : collectors) {
            for (final Map<String, Object> values : collector.collect()) {
                logger.log(collector.type(), values);
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
