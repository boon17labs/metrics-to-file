package io.github.guranxp.metricstofile.internal.file;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricLineFormatterTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-08-27T10:00:00Z");

    @Test
    void shouldFormatLineWithAppTypeAndValuesInInsertionOrder() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", 312);
        values.put("committed_mb", 400);

        // when
        final String line = MetricLineFormatter.format(
                TIMESTAMP, "order-service", "heap", values);

        // then
        assertEquals(
                "2026-08-27T10:00:00Z app=order-service type=heap"
                        + " used_mb=312 committed_mb=400",
                line);
    }

    @Test
    void shouldFormatLineWithoutTrailingSpaceWhenValuesEmpty() {
        // when
        final String line = MetricLineFormatter.format(
                TIMESTAMP, "order-service", "threads", Collections.emptyMap());

        // then
        assertEquals("2026-08-27T10:00:00Z app=order-service type=threads", line);
    }

    @Test
    void shouldFormatNullValueAsLiteralNull() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", null);

        // when
        final String line = MetricLineFormatter.format(
                TIMESTAMP, "order-service", "gc", values);

        // then
        assertEquals(
                "2026-08-27T10:00:00Z app=order-service type=gc name=null", line);
    }

    @Test
    void shouldQuoteStringValueContainingSpace() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "G1 Young");
        values.put("count", 42);

        // when
        final String line = MetricLineFormatter.format(
                TIMESTAMP, "order-service", "gc", values);

        // then
        assertEquals(
                "2026-08-27T10:00:00Z app=order-service type=gc"
                        + " name=\"G1 Young\" count=42",
                line);
    }

    @Test
    void shouldNotQuoteStringValueWithoutSpace() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("collector", "G1");

        // when
        final String line = MetricLineFormatter.format(
                TIMESTAMP, "order-service", "gc", values);

        // then
        assertEquals(
                "2026-08-27T10:00:00Z app=order-service type=gc collector=G1", line);
    }

    @Test
    void shouldTruncateTimestampToSeconds() {
        // given
        final Instant withNanos = Instant.parse("2026-08-27T10:00:00.123456789Z");

        // when
        final String line = MetricLineFormatter.format(
                withNanos, "order-service", "heap", Collections.emptyMap());

        // then
        assertEquals("2026-08-27T10:00:00Z app=order-service type=heap", line);
    }
}
