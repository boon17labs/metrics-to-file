package io.github.boon17labs.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void shouldNeverReportBothKeysAtOnce() {
        // rss_mb (true RSS, Linux only) and committed_vmem_mb (the non-Linux
        // fallback) name quantitatively different things, so exactly one of
        // them, or neither, may be present — never both.
        final Map<String, Object> values = onlyGroup();

        assertFalse(values.containsKey("rss_mb") && values.containsKey("committed_vmem_mb"));
        assertTrue(values.isEmpty() || values.size() == 1);
    }

    @Test
    void shouldRespectAvailabilityInvariant() {
        // when
        final Map<String, Object> values = onlyGroup();

        // then — whichever key is present (if any) is a real non-negative
        // reading; the library's usual -1 not-available sentinel is
        // deliberately not used here (see the class javadoc), so an
        // unavailable reading means the key is simply absent instead
        for (final Object value : values.values()) {
            assertTrue((long) value >= 0L);
        }
    }

    @Test
    void shouldReportTrueRssOnLinux() {
        // This dev/CI environment isn't guaranteed to be Linux, but when it
        // is, /proc/self/status is always present, so rss_mb must be
        // reported (never committed_vmem_mb) — a running JVM always has at
        // least a few MB resident.
        org.junit.jupiter.api.Assumptions.assumeTrue(isLinux(), "only meaningful on Linux");

        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertTrue(values.containsKey("rss_mb"), "expected rss_mb to be present on Linux");
        assertFalse(values.containsKey("committed_vmem_mb"),
                "rss_mb was available, so the fallback field should not also be logged");
        assertTrue((long) values.get("rss_mb") > 0L,
                "expected real RSS usage on Linux, but got " + values.get("rss_mb"));
    }

    @Test
    void shouldReportCommittedVirtualMemoryFallbackOnNonLinux() {
        // Never rss_mb here — that name is reserved for true RSS. Skips on a
        // non-HotSpot JVM, where neither source is available and the line
        // correctly carries neither key (covered by
        // shouldNeverReportBothKeysAtOnce above).
        org.junit.jupiter.api.Assumptions.assumeTrue(!isLinux(), "only meaningful off Linux");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                ManagementFactory.getOperatingSystemMXBean()
                        instanceof com.sun.management.OperatingSystemMXBean,
                "only meaningful on a HotSpot-derived JVM");

        // when
        final Map<String, Object> values = onlyGroup();

        // then
        assertTrue(values.containsKey("committed_vmem_mb"),
                "expected the HotSpot fallback to supply committed_vmem_mb");
        assertFalse(values.containsKey("rss_mb"),
                "rss_mb must never be logged off Linux");
        assertTrue((long) values.get("committed_vmem_mb") > 0L,
                "expected real committed-virtual-memory usage, but got "
                        + values.get("committed_vmem_mb"));
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private Map<String, Object> onlyGroup() {
        final List<Map<String, Object>> groups = collector.collect();
        return groups.get(0);
    }
}
