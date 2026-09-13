package io.github.boon17labs.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadMetricsCollectorTest {

    private final ThreadMetricsCollector collector = new ThreadMetricsCollector();

    @Test
    void shouldReportThreadsAsType() {
        assertEquals("threads", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectThreadValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(4, values.size());
        assertTrue(values.containsKey("live"));
        assertTrue(values.containsKey("peak"));
        assertTrue(values.containsKey("deadlocked"));
        assertTrue(values.containsKey("stack_mb"));
    }

    @Test
    void shouldRespectThreadCountInvariants() {
        // when
        final Map<String, Object> values = onlyGroup();
        final int live = (int) values.get("live");
        final int peak = (int) values.get("peak");
        final int deadlocked = (int) values.get("deadlocked");

        // then
        assertTrue(live >= 1);
        assertTrue(peak >= live);
        assertTrue(deadlocked >= 0);
    }

    @Test
    void shouldApproximateStackMemoryAsLiveThreadsTimesDefaultStackSize() {
        // when
        final Map<String, Object> values = onlyGroup();
        final int live = (int) values.get("live");
        final long stackMb = (long) values.get("stack_mb");

        // then — 512KB per live thread, converted to MB
        assertEquals(live * 512L / 1024L, stackMb);
    }

    @Test
    void shouldReportZeroDeadlockedThreadsInHealthyState() {
        assertEquals(0, onlyGroup().get("deadlocked"));
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
