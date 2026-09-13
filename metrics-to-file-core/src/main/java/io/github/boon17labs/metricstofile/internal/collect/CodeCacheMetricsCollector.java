package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the current Code Cache pool usage via {@link java.lang.management}.
 */
public final class CodeCacheMetricsCollector implements MetricsCollector {

    private static final String POOL_NAME = "Code Cache";

    @Override
    public String type() {
        return "codecache";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final MemoryUsage usage = findCodeCacheUsage();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", usage == null ? -1L : MemoryUnits.toMb(usage.getUsed()));
        return Collections.singletonList(values);
    }

    private static MemoryUsage findCodeCacheUsage() {
        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (POOL_NAME.equals(pool.getName())) {
                return pool.getUsage();
            }
        }
        return null;
    }
}
