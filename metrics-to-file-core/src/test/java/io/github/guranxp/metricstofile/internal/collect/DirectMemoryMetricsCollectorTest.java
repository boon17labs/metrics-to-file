package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectMemoryMetricsCollectorTest {

    private final DirectMemoryMetricsCollector collector = new DirectMemoryMetricsCollector();

    @Test
    void shouldReportDirectAsType() {
        assertEquals("direct", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectDirectMemoryValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(2, values.size());
        assertTrue(values.containsKey("used_mb"));
        assertTrue(values.containsKey("count"));
    }

    @Test
    void shouldRespectAvailabilityInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final long usedMb = (long) values.get("used_mb");
        final long count = (long) values.get("count");

        // then
        assertTrue(usedMb == -1L || usedMb >= 0L);
        assertTrue(count == -1L || count >= 0L);
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
