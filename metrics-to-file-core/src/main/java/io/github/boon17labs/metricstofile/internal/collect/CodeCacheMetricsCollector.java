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
 *
 * <p>Pre-JDK 9, or with tiered compilation disabled, the JVM exposes a single
 * pool named {@code "Code Cache"}. With tiered compilation enabled (the
 * default since JDK 9), HotSpot splits it into three separate pools instead
 * ({@code "CodeHeap 'non-nmethods'"}, {@code "CodeHeap 'profiled nmethods'"},
 * {@code "CodeHeap 'non-profiled nmethods'"}), none of which is named
 * {@code "Code Cache"}. This collector matches both naming schemes and sums
 * across however many pools are present, so it reports real usage in either
 * configuration instead of always falling back to {@code -1}.
 */
public final class CodeCacheMetricsCollector implements MetricsCollector {

    private static final String LEGACY_POOL_NAME = "Code Cache";
    private static final String TIERED_POOL_PREFIX = "CodeHeap";

    @Override
    public String type() {
        return "codecache";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final long usedBytes = findCodeCacheUsedBytes();
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", usedBytes < 0 ? -1L : MemoryUnits.toMb(usedBytes));
        return Collections.singletonList(values);
    }

    private static long findCodeCacheUsedBytes() {
        long totalUsed = -1L;
        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (!isCodeCachePool(pool.getName())) {
                continue;
            }
            final MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            totalUsed = Math.max(totalUsed, 0) + usage.getUsed();
        }
        return totalUsed;
    }

    private static boolean isCodeCachePool(final String poolName) {
        return LEGACY_POOL_NAME.equals(poolName) || poolName.startsWith(TIERED_POOL_PREFIX);
    }
}
