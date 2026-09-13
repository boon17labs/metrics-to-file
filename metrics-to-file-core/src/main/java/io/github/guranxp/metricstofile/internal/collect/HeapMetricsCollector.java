package io.github.guranxp.metricstofile.internal.collect;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the current heap memory usage via {@link java.lang.management}.
 */
public final class HeapMetricsCollector implements MetricsCollector {

    @Override
    public String type() {
        return "heap";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", MemoryUnits.toMb(usage.getUsed()));
        values.put("committed_mb", MemoryUnits.toMb(usage.getCommitted()));
        values.put("max_mb", MemoryUnits.toMb(usage.getMax()));
        return Collections.singletonList(values);
    }
}
