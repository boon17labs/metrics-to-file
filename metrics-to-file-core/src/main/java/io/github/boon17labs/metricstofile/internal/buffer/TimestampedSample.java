package io.github.boon17labs.metricstofile.internal.buffer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One metric group read by the sampler at {@code timestamp}, held in
 * memory until the next write. The timestamp is captured at sample
 * time so it survives being buffered — the file must show when the
 * value was actually read, not when it happened to be flushed.
 */
public final class TimestampedSample {

    private final Instant timestamp;
    private final String type;
    private final Map<String, Object> values;

    public TimestampedSample(final Instant timestamp, final String type,
            final Map<String, Object> values) {
        this.timestamp = timestamp;
        this.type = type;
        this.values = new LinkedHashMap<>(values);
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String type() {
        return type;
    }

    public Map<String, Object> values() {
        return values;
    }
}
