package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads collection count and time for every garbage collector bean via
 * {@link java.lang.management}, one metric group per collector.
 */
public final class GcMetricsCollector implements MetricsCollector {

    @Override
    public String type() {
        return "gc";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final List<Map<String, Object>> groups = new ArrayList<>();
        for (final GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            final Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", gcBean.getName());
            values.put("count", gcBean.getCollectionCount());
            values.put("time_ms", gcBean.getCollectionTime());
            groups.add(values);
        }
        return groups;
    }
}
