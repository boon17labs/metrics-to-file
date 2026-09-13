package io.github.boon17labs.metricstofile.internal.file;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Formats one metric group into the key-value log line described in
 * CLAUDE.md, e.g. {@code 2026-08-27T10:00:00Z app=order-service
 * type=heap used_mb=312 committed_mb=400}.
 */
public final class MetricLineFormatter {

    private MetricLineFormatter() {
    }

    public static String format(final Instant timestamp, final String appName,
            final String type, final Map<String, Object> values) {
        final StringBuilder line = new StringBuilder();
        line.append(timestamp.truncatedTo(ChronoUnit.SECONDS));
        line.append(" app=").append(appName);
        line.append(" type=").append(type);
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            line.append(' ').append(entry.getKey()).append('=');
            line.append(formatValue(entry.getValue()));
        }
        return line.toString();
    }

    private static String formatValue(final Object value) {
        if (value instanceof String && ((String) value).indexOf(' ') >= 0) {
            return '"' + (String) value + '"';
        }
        return String.valueOf(value);
    }
}
