package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the current Metaspace pool usage via {@link java.lang.management}.
 */
public final class MetaspaceMetricsCollector implements MetricsCollector {

    private static final String POOL_NAME = "Metaspace";

    @Override
    public String type() {
        return "metaspace";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final MemoryUsage usage = findMetaspaceUsage();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", usage == null ? -1L : MemoryUnits.toMb(usage.getUsed()));
        values.put("committed_mb", usage == null ? -1L : MemoryUnits.toMb(usage.getCommitted()));
        return Collections.singletonList(values);
    }

    private static MemoryUsage findMetaspaceUsage() {
        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (POOL_NAME.equals(pool.getName())) {
                return pool.getUsage();
            }
        }
        return null;
    }
}
