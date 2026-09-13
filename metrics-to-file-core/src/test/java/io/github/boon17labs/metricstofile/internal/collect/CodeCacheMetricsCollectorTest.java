package io.github.boon17labs.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeCacheMetricsCollectorTest {

    private final CodeCacheMetricsCollector collector = new CodeCacheMetricsCollector();

    @Test
    void shouldReportCodecacheAsType() {
        assertEquals("codecache", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectCodeCacheValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(1, values.size());
        assertTrue(values.containsKey("used_mb"));
    }

    @Test
    void shouldRespectAvailabilityInvariant() {
        // when
        final long usedMb = (long) onlyGroup().get("used_mb");

        // then
        assertTrue(usedMb == -1L || usedMb >= 0L);
    }

    @Test
    void shouldReportRealUsageOnAModernTieredCompilationJvm() {
        // Every JVM this library targets (JDK 9+) runs with tiered compilation by
        // default, which splits code cache across several "CodeHeap '...'" pools
        // rather than exposing a single "Code Cache" pool. On such a JVM the
        // collector must find and sum those pools instead of falling back to -1.

        // when
        final long usedMb = (long) onlyGroup().get("used_mb");

        // then
        assertTrue(usedMb >= 0L, "expected real code cache usage, but got the not-found sentinel (-1)");
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
