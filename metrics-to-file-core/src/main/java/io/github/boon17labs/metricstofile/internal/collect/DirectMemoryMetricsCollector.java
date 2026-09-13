package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads direct (off-heap) buffer usage via {@link java.lang.management}.
 */
public final class DirectMemoryMetricsCollector implements MetricsCollector {

    private static final String POOL_NAME = "direct";

    @Override
    public String type() {
        return "direct";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final BufferPoolMXBean pool = findDirectPool();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", pool == null ? -1L : MemoryUnits.toMb(pool.getMemoryUsed()));
        values.put("count", pool == null ? -1L : pool.getCount());
        return Collections.singletonList(values);
    }

    private static BufferPoolMXBean findDirectPool() {
        for (final BufferPoolMXBean pool
                : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (POOL_NAME.equals(pool.getName())) {
                return pool;
            }
        }
        return null;
    }
}
