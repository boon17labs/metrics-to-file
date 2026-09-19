package io.github.boon17labs.metricstofile.prometheus.internal;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Owns a {@link PrometheusMeterRegistry} whose every meter carries an
 * {@code application=<appName>} label, pre-populated with Micrometer's
 * default JVM binders: memory, threads and GC. Closing it releases what
 * the binders hold (the GC binder listens for JMX notifications) and
 * closes the registry.
 *
 * <p>A binder that fails to bind is skipped with a warning on stderr —
 * the other binders still bind and nothing is thrown to the caller, since
 * a metrics problem must never affect the host application.
 */
public final class JvmMetricsRegistry {

    private final PrometheusMeterRegistry registry;
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private boolean closed;

    public JvmMetricsRegistry(final String appName) {
        this(appName, defaultBinders());
    }

    // Package-private so tests can supply their own binders.
    JvmMetricsRegistry(final String appName, final List<MeterBinder> binders) {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        // Common tags must be set before the first meter is registered.
        registry.config().commonTags("application", appName);
        for (final MeterBinder binder : binders) {
            // Remember closeable binders before binding, so one that fails
            // half way through binding is still cleaned up on close().
            if (binder instanceof AutoCloseable) {
                closeables.add((AutoCloseable) binder);
            }
            bind(binder);
        }
    }

    public PrometheusMeterRegistry registry() {
        return registry;
    }

    /** Closes the binders and then the registry. Safe to call more than once. */
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (final AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (final Exception e) {
                System.err.println("[metrics-to-file] failed to close "
                        + closeable.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        registry.close();
    }

    private void bind(final MeterBinder binder) {
        try {
            binder.bindTo(registry);
        } catch (final Exception | LinkageError e) {
            System.err.println("[metrics-to-file] failed to bind "
                    + binder.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static List<MeterBinder> defaultBinders() {
        return Arrays.asList(new JvmMemoryMetrics(), new JvmThreadMetrics(), new JvmGcMetrics());
    }
}
