package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaspaceMetricsCollectorTest {

    private final MetaspaceMetricsCollector collector = new MetaspaceMetricsCollector();

    @Test
    void shouldReportMetaspaceAsType() {
        assertEquals("metaspace", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectMetaspaceValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(2, values.size());
        assertTrue(values.containsKey("used_mb"));
        assertTrue(values.containsKey("committed_mb"));
    }

    @Test
    void shouldRespectMemoryUsageInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final long usedMb = (long) values.get("used_mb");
        final long committedMb = (long) values.get("committed_mb");

        // then
        assertTrue(usedMb >= 0);
        assertTrue(committedMb >= usedMb);
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
