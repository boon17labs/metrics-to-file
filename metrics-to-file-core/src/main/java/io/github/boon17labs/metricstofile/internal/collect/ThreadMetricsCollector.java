package io.github.boon17labs.metricstofile.internal.collect;

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

    // The JVM doesn't expose actual per-thread stack memory via any public
    // API (each thread's real reserved size depends on -Xss, the platform
    // default, and how much of it is actually committed), so stack_mb is a
    // deliberate approximation: live thread count times HotSpot's common
    // default stack size on 64-bit platforms. Treat it as an order-of-magnitude
    // figure for cross-app comparison, not a measured value.
    private static final long DEFAULT_STACK_SIZE_BYTES = 512L * 1024L;

    @Override
    public String type() {
        return "threads";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final int live = threadMXBean.getThreadCount();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("live", live);
        values.put("peak", threadMXBean.getPeakThreadCount());
        values.put("deadlocked", deadlockedCount(threadMXBean));
        values.put("stack_mb", MemoryUnits.toMb(live * DEFAULT_STACK_SIZE_BYTES));
        return Collections.singletonList(values);
    }

    private static int deadlockedCount(final ThreadMXBean threadMXBean) {
        final long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
        return deadlockedIds == null ? 0 : deadlockedIds.length;
    }
}
