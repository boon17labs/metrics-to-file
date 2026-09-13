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

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
