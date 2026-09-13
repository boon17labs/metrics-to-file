package io.github.boon17labs.metricstofile;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMetricsLoggerTest {

    private final InMemoryMetricsLogger logger = new InMemoryMetricsLogger();

    @Test
    void shouldReturnEmptyListWhenNothingLogged() {
        // when
        final List<InMemoryMetricsLogger.LoggedMetric> entries = logger.entries();

        // then
        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldRecordLoggedMetricWhenLogging() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", 312);
        values.put("committed_mb", 400);

        // when
        logger.log("heap", values);

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> entries = logger.entries();
        assertEquals(1, entries.size());
        assertEquals("heap", entries.get(0).type());
        assertEquals(values, entries.get(0).values());
    }

    @Test
    void shouldRecordMultipleLogCallsInOrder() {
        // when
        logger.log("heap", new LinkedHashMap<>());
        logger.log("threads", new LinkedHashMap<>());

        // then
        final List<InMemoryMetricsLogger.LoggedMetric> entries = logger.entries();
        assertEquals(2, entries.size());
        assertEquals("heap", entries.get(0).type());
        assertEquals("threads", entries.get(1).type());
    }

    @Test
    void shouldNotThrowWhenLoggingWithEmptyMap() {
        assertDoesNotThrow(() -> logger.log("heap", new HashMap<>()));
    }

    @Test
    void shouldRecordNullValueInMap() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", null);

        // when
        logger.log("gc", values);

        // then
        assertTrue(logger.entries().get(0).values().containsKey("name"));
        assertEquals(null, logger.entries().get(0).values().get("name"));
    }

    @Test
    void shouldKeepIndependentCopyWhenValuesMapMutatedAfterLogging() {
        // given
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("used_mb", 312);

        // when
        logger.log("heap", values);
        values.put("used_mb", 999);

        // then
        assertEquals(312, logger.entries().get(0).values().get("used_mb"));
    }

    @Test
    void shouldReturnUnmodifiableEntriesList() {
        // given
        final List<InMemoryMetricsLogger.LoggedMetric> entries = logger.entries();

        // when / then
        assertThrows(UnsupportedOperationException.class,
                () -> entries.add(null));
    }

    @Test
    void shouldReturnUnmodifiableValuesMap() {
        // given
        logger.log("heap", new LinkedHashMap<>());
        final Map<String, Object> values = logger.entries().get(0).values();

        // when / then
        assertThrows(UnsupportedOperationException.class,
                () -> values.put("used_mb", 1));
    }

    @Test
    void shouldNotThrowWhenClosing() {
        assertDoesNotThrow(logger::close);
    }

    @Test
    void shouldStillExposeEntriesAfterClosing() {
        // given
        logger.log("heap", new LinkedHashMap<>());

        // when
        logger.close();

        // then
        assertEquals(1, logger.entries().size());
    }
}
