package io.github.guranxp.metricstofile.internal.collect;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the current thread counts via {@link java.lang.management}.
 */
public final class ThreadMetricsCollector implements MetricsCollector {

    @Override
    public String type() {
        return "threads";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("live", threadMXBean.getThreadCount());
        values.put("peak", threadMXBean.getPeakThreadCount());
        values.put("deadlocked", deadlockedCount(threadMXBean));
        return Collections.singletonList(values);
    }

    private static int deadlockedCount(final ThreadMXBean threadMXBean) {
        final long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
        return deadlockedIds == null ? 0 : deadlockedIds.length;
    }
}
