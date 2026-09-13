package io.github.boon17labs.metricstofile;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoOpMetricsLoggerTest {

    private final MetricsLogger logger = new NoOpMetricsLogger();

    @Test
    void shouldNotThrowWhenLoggingWithValues() {
        // given
        final Map<String, Object> values = new HashMap<>();
        values.put("used_mb", 312);
        values.put("committed_mb", 400);

        // when / then
        assertDoesNotThrow(() -> logger.log("heap", values));
    }

    @Test
    void shouldNotThrowWhenLoggingWithEmptyMap() {
        // given
        final Map<String, Object> values = Collections.emptyMap();

        // when / then
        assertDoesNotThrow(() -> logger.log("heap", values));
    }

    @Test
    void shouldNotThrowWhenLoggingWithNullValueInMap() {
        // given
        final Map<String, Object> values = new HashMap<>();
        values.put("name", null);

        // when / then
        assertDoesNotThrow(() -> logger.log("gc", values));
    }

    @Test
    void shouldNotThrowWhenClosing() {
        // when / then
        assertDoesNotThrow(logger::close);
    }

    @Test
    void shouldNotThrowWhenClosingMultipleTimes() {
        // given
        logger.close();

        // when / then
        assertDoesNotThrow(logger::close);
    }
}
