package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapMetricsCollectorTest {

    private final HeapMetricsCollector collector = new HeapMetricsCollector();

    @Test
    void shouldReportHeapAsType() {
        assertEquals("heap", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectHeapValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(3, values.size());
        assertTrue(values.containsKey("used_mb"));
        assertTrue(values.containsKey("committed_mb"));
        assertTrue(values.containsKey("max_mb"));
    }

    @Test
    void shouldRespectMemoryUsageInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final long usedMb = (long) values.get("used_mb");
        final long committedMb = (long) values.get("committed_mb");
        final long maxMb = (long) values.get("max_mb");

        // then
        assertTrue(usedMb >= 0);
        assertTrue(committedMb >= usedMb);
        assertTrue(maxMb == -1 || maxMb >= committedMb);
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
