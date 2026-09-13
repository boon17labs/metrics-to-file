package io.github.boon17labs.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcMetricsCollectorTest {

    private final GcMetricsCollector collector = new GcMetricsCollector();

    @Test
    void shouldReportGcAsType() {
        assertEquals("gc", collector.type());
    }

    @Test
    void shouldCollectOneGroupPerGarbageCollectorBean() {
        // when
        final List<Map<String, Object>> groups = collector.collect();

        // then
        assertFalse(groups.isEmpty());
        assertEquals(ManagementFactory.getGarbageCollectorMXBeans().size(), groups.size());
    }

    @Test
    void shouldCollectGcValuesWithExpectedKeysForEachGroup() {
        // when
        final List<Map<String, Object>> groups = collector.collect();

        // then
        for (final Map<String, Object> values : groups) {
            assertEquals(3, values.size());
            assertTrue(values.containsKey("name"));
            assertTrue(values.containsKey("count"));
            assertTrue(values.containsKey("time_ms"));
        }
    }

    @Test
    void shouldRespectCountAndTimeInvariants() {
        // when
        final List<Map<String, Object>> groups = collector.collect();

        // then
        for (final Map<String, Object> values : groups) {
            assertTrue((long) values.get("count") >= -1L);
            assertTrue((long) values.get("time_ms") >= -1L);
        }
    }
}
