package io.github.boon17labs.metricstofile.prometheus.internal;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.time.Clock;
import java.util.List;

/**
 * Takes one snapshot of a {@link PrometheusMeterRegistry}: scrapes it,
 * turns the scrape into timestamped sample lines
 * ({@link PrometheusSnapshotFormatter}) and appends them to the daily
 * file ({@link PrometheusFileWriter}).
 *
 * <p>Depends only on a {@code PrometheusMeterRegistry}, not on how it was
 * created, so it works equally with a registry this library owns or one
 * supplied from outside (for example by Spring). Never throws — a failure
 * is warned on stderr, since a metrics problem must never affect the host
 * application.
 */
public final class PrometheusSnapshotter {

    private final PrometheusMeterRegistry registry;
    private final PrometheusFileWriter fileWriter;
    private final Clock clock;

    public PrometheusSnapshotter(final PrometheusMeterRegistry registry,
            final PrometheusFileWriter fileWriter) {
        this(registry, fileWriter, Clock.systemUTC());
    }

    // Package-private so tests can control the timestamp.
    PrometheusSnapshotter(final PrometheusMeterRegistry registry,
            final PrometheusFileWriter fileWriter, final Clock clock) {
        this.registry = registry;
        this.fileWriter = fileWriter;
        this.clock = clock;
    }

    public void snapshot() {
        try {
            // Read the time just before scraping, so the timestamp is as close as
            // possible to the moment the values are read.
            final long timestampMillis = clock.millis();
            final String scrape = registry.scrape();
            final List<String> lines = PrometheusSnapshotFormatter.format(scrape, timestampMillis);
            fileWriter.write(lines);
        } catch (final RuntimeException e) {
            System.err.println("[metrics-to-file] failed to take prometheus snapshot: "
                    + e.getMessage());
        }
    }
}
