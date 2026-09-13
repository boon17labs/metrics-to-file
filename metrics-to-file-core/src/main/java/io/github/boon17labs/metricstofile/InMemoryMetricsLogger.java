package io.github.boon17labs.metricstofile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps every logged metric group in memory instead of writing it
 * anywhere, so tests can inspect exactly what was logged.
 */
public final class InMemoryMetricsLogger implements MetricsLogger {

    private final List<LoggedMetric> entries = new ArrayList<>();

    @Override
    public void log(final String type, final Map<String, Object> values) {
        entries.add(new LoggedMetric(type, new LinkedHashMap<>(values)));
    }

    @Override
    public void close() {
        // no-op — entries remain inspectable after close
    }

    /**
     * Snapshot of every metric group logged so far, in call order.
     */
    public List<LoggedMetric> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static final class LoggedMetric {

        private final String type;
        private final Map<String, Object> values;

        private LoggedMetric(final String type, final Map<String, Object> values) {
            this.type = type;
            this.values = values;
        }

        public String type() {
            return type;
        }

        public Map<String, Object> values() {
            return Collections.unmodifiableMap(values);
        }
    }
}
