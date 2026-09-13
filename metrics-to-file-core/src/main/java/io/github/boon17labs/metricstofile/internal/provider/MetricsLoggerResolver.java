package io.github.boon17labs.metricstofile.internal.provider;

import io.github.boon17labs.metricstofile.MetricsLogger;
import io.github.boon17labs.metricstofile.NoOpMetricsLogger;

import java.io.File;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves the active {@link MetricsLogger} — bundled with its
 * daemon requirements — from the {@code metrics.implementation}
 * system property, matching it against {@link MetricsLoggerProvider}
 * implementations discovered via {@link ServiceLoader}. Never throws
 * — falls back to {@link NoOpMetricsLogger} (needing no daemons)
 * whenever resolution fails, so the host app is never affected by
 * misconfiguration.
 */
public final class MetricsLoggerResolver {

    private static final String PROPERTY = "metrics.implementation";
    private static final String DEFAULT_KEY = "noop";

    private MetricsLoggerResolver() {
    }

    public static ResolvedLogger resolve(final String appName, final File logDir) {
        return resolve(appName, logDir, System.getProperty(PROPERTY, DEFAULT_KEY));
    }

    static ResolvedLogger resolve(final String appName, final File logDir,
            final String implementationKey) {
        final String key = implementationKey.trim();
        if (DEFAULT_KEY.equalsIgnoreCase(key)) {
            return noOp();
        }
        try {
            for (final MetricsLoggerProvider provider
                    : ServiceLoader.load(MetricsLoggerProvider.class)) {
                if (provider.implementationKey().equalsIgnoreCase(key)) {
                    return new ResolvedLogger(provider.create(appName, logDir),
                            provider.requirements());
                }
            }
        } catch (final RuntimeException | ServiceConfigurationError e) {
            System.err.println("[metrics-to-file] failed to resolve metrics "
                    + "implementation '" + key + "': " + e.getMessage());
            return noOp();
        }
        System.err.println("[metrics-to-file] unknown metrics.implementation '"
                + key + "', falling back to noop");
        return noOp();
    }

    private static ResolvedLogger noOp() {
        return new ResolvedLogger(new NoOpMetricsLogger(), new DaemonRequirements(false, false));
    }
}
