package io.github.boon17labs.metricstofile;

import java.util.Map;

/**
 * Default implementation. Discards everything. Used when metrics are not
 * explicitly enabled, so the host application is never affected.
 */
public class NoOpMetricsLogger implements MetricsLogger {

    @Override
    public void log(final String type, final Map<String, Object> values) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
