package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads class loading counts via {@link java.lang.management}.
 */
public final class ClassLoadingMetricsCollector implements MetricsCollector {

    @Override
    public String type() {
        return "classloading";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final ClassLoadingMXBean bean = ManagementFactory.getClassLoadingMXBean();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("loaded", bean.getLoadedClassCount());
        values.put("unloaded", bean.getUnloadedClassCount());
        return Collections.singletonList(values);
    }
}
