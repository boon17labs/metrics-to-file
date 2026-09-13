package io.github.guranxp.metricstofile;

import java.util.Map;

/**
 * SPI for persisting a metric group. Implementations are selected via
 * ServiceLoader based on the {@code metrics.implementation} system property.
 */
public interface MetricsLogger {

    /**
     * Logs one metric group, e.g. type="heap", values={used_mb=312, committed_mb=400}.
     */
    void log(String type, Map<String, Object> values);

    /**
     * Releases any resources held by this logger. Called symmetrically with start.
     */
    void close();
}
