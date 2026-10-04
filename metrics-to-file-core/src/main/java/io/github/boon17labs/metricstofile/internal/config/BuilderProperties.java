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
    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(DEFAULT_INTERVAL_MINUTES);
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
            if (!isPositive(explicit)) {
                System.err.println("[metrics-to-file] invalid interval '" + explicit
                        + "', must be positive, using default");
                return DEFAULT_INTERVAL;
            }
            return explicit;
        }
        final String value = System.getProperty(INTERVAL_PROPERTY);
        if (value == null) {
            return DEFAULT_INTERVAL;
        }
        try {
            final Duration parsed = parseInterval(value.trim());
            if (!isPositive(parsed)) {
                System.err.println("[metrics-to-file] invalid " + INTERVAL_PROPERTY + " '"
                        + value + "', must be positive, using default");
                return DEFAULT_INTERVAL;
            }
            return parsed;
        } catch (final NumberFormatException e) {
            System.err.println("[metrics-to-file] invalid " + INTERVAL_PROPERTY + " '"
                    + value + "', using default");
            return DEFAULT_INTERVAL;
        }
    }

    /**
     * Parses a plain number as whole minutes (backward compatible), or a
     * number suffixed with {@code ms}, {@code s} or {@code m} as
     * milliseconds, seconds or minutes respectively, e.g. {@code "500ms"},
     * {@code "1s"}, {@code "2m"}.
     */
    private static Duration parseInterval(final String value) {
        if (value.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.ofMinutes(Long.parseLong(value));
    }

    private static boolean isPositive(final Duration duration) {
        return !duration.isZero() && !duration.isNegative();
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
