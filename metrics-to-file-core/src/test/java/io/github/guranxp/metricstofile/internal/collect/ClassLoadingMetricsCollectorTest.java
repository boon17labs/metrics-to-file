package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoadingMetricsCollectorTest {

    private final ClassLoadingMetricsCollector collector = new ClassLoadingMetricsCollector();

    @Test
    void shouldReportClassloadingAsType() {
        assertEquals("classloading", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectClassLoadingValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(2, values.size());
        assertTrue(values.containsKey("loaded"));
        assertTrue(values.containsKey("unloaded"));
    }

    @Test
    void shouldRespectClassCountInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final int loaded = (int) values.get("loaded");
        final long unloaded = (long) values.get("unloaded");

        // then
        assertTrue(loaded > 0);
        assertTrue(unloaded >= 0L);
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
