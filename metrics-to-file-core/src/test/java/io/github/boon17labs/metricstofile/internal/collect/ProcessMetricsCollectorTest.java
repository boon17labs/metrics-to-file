package io.github.boon17labs.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessMetricsCollectorTest {

    private final ProcessMetricsCollector collector = new ProcessMetricsCollector();

    @Test
    void shouldReportProcessAsType() {
        assertEquals("process", collector.type());
    }

    @Test
    void shouldCollectExactlyOneValuesGroup() {
        assertEquals(1, collector.collect().size());
    }

    @Test
    void shouldCollectProcessValuesWithExpectedKeys() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertEquals(1, values.size());
        assertTrue(values.containsKey("rss_mb"));
    }

    @Test
    void shouldRespectAvailabilityInvariant() {
        // when
        final long rssMb = (long) onlyGroup().get("rss_mb");

        // then
        assertTrue(rssMb == -1L || rssMb >= 0L);
    }

    @Test
    void shouldReportRealUsageOnLinux() {
        // This suite (and CI) runs on Linux, where /proc/self/status is always
        // present, so the -1 not-available branch should never trigger here —
        // that branch is exercised by shouldRespectAvailabilityInvariant above
        // instead, since it holds on every OS.
        org.junit.jupiter.api.Assumptions.assumeTrue(isLinux(), "only meaningful on Linux");

        // when
        final long rssMb = (long) onlyGroup().get("rss_mb");

        // then — a running JVM always has at least a few MB resident
        assertTrue(rssMb > 0L, "expected real RSS usage on Linux, but got " + rssMb);
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
