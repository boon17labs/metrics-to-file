package io.github.boon17labs.metricstofile.prometheus.internal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusSnapshotFormatterTest {

    private static final long TIMESTAMP = 1724580000000L;

    @Test
    void shouldAppendTimestampToSampleLine() {
        // given
        final String scrape = "jvm_threads_live_threads{application=\"order-service\"} 4.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(
                Collections.singletonList(
                        "jvm_threads_live_threads{application=\"order-service\"} 4.0 1724580000000"),
                lines);
    }

    @Test
    void shouldAppendTimestampToSampleWithoutLabels() {
        // given
        final String scrape = "up 1.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(Collections.singletonList("up 1.0 1724580000000"), lines);
    }

    @Test
    void shouldDropHelpAndTypeLines() {
        // given
        final String scrape = "# HELP jvm_threads_live_threads The current number of live threads\n"
                + "# TYPE jvm_threads_live_threads gauge\n"
                + "jvm_threads_live_threads 4.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(Collections.singletonList("jvm_threads_live_threads 4.0 1724580000000"), lines);
    }

    @Test
    void shouldDropBlankLines() {
        // given
        final String scrape = "\nup 1.0\n\n   \nup 2.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(Arrays.asList("up 1.0 1724580000000", "up 2.0 1724580000000"), lines);
    }

    @Test
    void shouldPreserveSampleOrder() {
        // given
        final String scrape = "b_metric 2.0\na_metric 1.0\nc_metric 3.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(
                Arrays.asList(
                        "b_metric 2.0 1724580000000",
                        "a_metric 1.0 1724580000000",
                        "c_metric 3.0 1724580000000"),
                lines);
    }

    @Test
    void shouldAppendTimestampAfterLabelValuesContainingSpacesAndBraces() {
        // given
        final String scrape = "jvm_memory_used_bytes{area=\"nonheap\",id=\"CodeHeap 'non-nmethods'\"} 1315712.0\n"
                + "jvm_gc_pause_seconds_count{action=\"end of major GC\",gc=\"a } b\"} 1\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(
                Arrays.asList(
                        "jvm_memory_used_bytes{area=\"nonheap\",id=\"CodeHeap 'non-nmethods'\"} 1315712.0 1724580000000",
                        "jvm_gc_pause_seconds_count{action=\"end of major GC\",gc=\"a } b\"} 1 1724580000000"),
                lines);
    }

    @Test
    void shouldKeepValuesInScientificNotation() {
        // given
        final String scrape = "jvm_memory_max_bytes{id=\"PS Old Gen\"} 3.221225472E9\n"
                + "jvm_memory_max_bytes{id=\"Metaspace\"} -1.0\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(
                Arrays.asList(
                        "jvm_memory_max_bytes{id=\"PS Old Gen\"} 3.221225472E9 1724580000000",
                        "jvm_memory_max_bytes{id=\"Metaspace\"} -1.0 1724580000000"),
                lines);
    }

    @Test
    void shouldHandleWindowsLineEndings() {
        // given
        final String scrape = "# TYPE up gauge\r\nup 1.0\r\nup 2.0\r\n";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(Arrays.asList("up 1.0 1724580000000", "up 2.0 1724580000000"), lines);
    }

    @Test
    void shouldHandleInputWithoutTrailingNewline() {
        // given
        final String scrape = "up 1.0";

        // when
        final List<String> lines = PrometheusSnapshotFormatter.format(scrape, TIMESTAMP);

        // then
        assertEquals(Collections.singletonList("up 1.0 1724580000000"), lines);
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {
        assertTrue(PrometheusSnapshotFormatter.format("", TIMESTAMP).isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenInputHasOnlyComments() {
        // given
        final String scrape = "# HELP up Whether the target is up\n# TYPE up gauge\n";

        // when / then
        assertTrue(PrometheusSnapshotFormatter.format(scrape, TIMESTAMP).isEmpty());
    }
}
