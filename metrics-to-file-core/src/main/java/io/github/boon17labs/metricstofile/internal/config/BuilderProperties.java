package io.github.boon17labs.metricstofile.internal.config;

import java.io.File;
import java.time.Duration;

/**
 * Resolves {@code Metrics.Builder} fields: an explicit value (set via
 * the fluent builder) always wins; otherwise falls back to the
 * matching {@code metrics.*} system property; otherwise the
 * documented default. Never throws — an invalid property value is
 * warned to stderr and the default wins.
 */
public final class BuilderProperties {

    private static final String LOG_DIR_PROPERTY = "metrics.log.dir";
    private static final String INTERVAL_PROPERTY = "metrics.interval";
    private static final String KEEP_DAYS_PROPERTY = "metrics.keep.days";

    private static final String DEFAULT_LOG_DIR = "./metrics";
    private static final long DEFAULT_INTERVAL_MINUTES = 60L;
    private static final int DEFAULT_KEEP_DAYS = 7;

    private BuilderProperties() {
    }

    public static File logDir(final File explicit) {
        if (explicit != null) {
            return explicit;
        }
        final String value = System.getProperty(LOG_DIR_PROPERTY);
        return value == null ? new File(DEFAULT_LOG_DIR) : new File(value);
    }

    public static Duration interval(final Duration explicit) {
        if (explicit != null) {
            return explicit;
        }
        final String value = System.getProperty(INTERVAL_PROPERTY);
        if (value == null) {
            return Duration.ofMinutes(DEFAULT_INTERVAL_MINUTES);
        }
        try {
            return Duration.ofMinutes(Long.parseLong(value.trim()));
        } catch (final NumberFormatException e) {
            System.err.println("[metrics-to-file] invalid " + INTERVAL_PROPERTY + " '"
                    + value + "', using default");
            return Duration.ofMinutes(DEFAULT_INTERVAL_MINUTES);
        }
    }

    public static int keepDays(final Integer explicit) {
        if (explicit != null) {
            return explicit;
        }
        final String value = System.getProperty(KEEP_DAYS_PROPERTY);
        if (value == null) {
            return DEFAULT_KEEP_DAYS;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            System.err.println("[metrics-to-file] invalid " + KEEP_DAYS_PROPERTY + " '"
                    + value + "', using default");
            return DEFAULT_KEEP_DAYS;
        }
    }

    public static boolean flag(final Boolean explicit, final String propertyName) {
        if (explicit != null) {
            return explicit;
        }
        return Boolean.parseBoolean(System.getProperty(propertyName));
    }
}
