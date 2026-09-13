package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuMetricsCollectorTest {

    private final CpuMetricsCollector collector = new CpuMetricsCollector();

    @Test
    void shouldReportCpuAsType() {
        assertEquals("cpu", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectCpuValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(2, values.size());
        assertTrue(values.containsKey("process_load"));
        assertTrue(values.containsKey("system_load"));
    }

    @Test
    void shouldRespectLoadFractionInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final double processLoad = (double) values.get("process_load");
        final double systemLoad = (double) values.get("system_load");

        // then
        assertTrue(processLoad == -1.0 || (processLoad >= 0.0 && processLoad <= 1.0));
        assertTrue(systemLoad == -1.0 || (systemLoad >= 0.0 && systemLoad <= 1.0));
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
