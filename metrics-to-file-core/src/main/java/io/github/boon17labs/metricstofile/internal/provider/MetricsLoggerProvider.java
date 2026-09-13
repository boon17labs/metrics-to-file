package io.github.boon17labs.metricstofile.internal.provider;

import io.github.boon17labs.metricstofile.MetricsLogger;

import java.io.File;

/**
 * SPI for plugging a {@link MetricsLogger} implementation into
 * {@code Metrics}. Providers are discovered via {@link java.util.ServiceLoader}
 * and selected by matching {@link #implementationKey()} against the
 * {@code metrics.implementation} system property.
 */
public interface MetricsLoggerProvider {

    String implementationKey();

    MetricsLogger create(String appName, File logDir);

    /**
     * Which background daemons this provider's logger needs running
     * for it to function as intended — e.g. a file-backed logger
     * needs both collection and cleanup, an in-memory one only needs
     * collection, a no-op one needs neither.
     */
    DaemonRequirements requirements();
}
