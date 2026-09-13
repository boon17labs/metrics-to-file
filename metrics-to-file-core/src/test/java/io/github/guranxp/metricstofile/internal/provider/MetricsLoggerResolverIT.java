package io.github.guranxp.metricstofile.internal.provider;

import io.github.guranxp.metricstofile.FileMetricsLogger;
import io.github.guranxp.metricstofile.InMemoryMetricsLogger;
import io.github.guranxp.metricstofile.NoOpMetricsLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsLoggerResolverIT {

    /**
     * Bypasses {@link MetricsLoggerResolver#resolve} entirely and talks to
     * {@link ServiceLoader} directly, so a broken/stale
     * {@code META-INF/services} entry (wrong package after a move, missing
     * a provider, etc.) fails this test loudly instead of being silently
     * swallowed by resolve()'s fallback-to-noop error handling.
     */
    @Test
    void shouldRegisterExactlyTheThreeExpectedProvidersViaServiceLoader() {
        // when
        final Set<String> keys = new HashSet<>();
        for (final MetricsLoggerProvider provider : ServiceLoader.load(MetricsLoggerProvider.class)) {
            keys.add(provider.implementationKey());
        }

        // then
        final Set<String> expected = new HashSet<>();
        expected.add("noop");
        expected.add("inmemory");
        expected.add("file");
        assertEquals(expected, keys);
    }

    @Test
    void shouldResolveNoOpLoggerNeedingNoDaemonsWhenKeyIsNoop(@TempDir final File logDir) {
        // when
        final ResolvedLogger resolved =
                MetricsLoggerResolver.resolve("order-service", logDir, "noop");

        // then
        assertInstanceOf(NoOpMetricsLogger.class, resolved.logger());
        assertFalse(resolved.requirements().collection());
        assertFalse(resolved.requirements().cleanup());
    }

    @Test
    void shouldResolveInMemoryLoggerNeedingOnlyCollectionWhenKeyIsInmemory(
            @TempDir final File logDir) {
        // when
        final ResolvedLogger resolved =
                MetricsLoggerResolver.resolve("order-service", logDir, "inmemory");

        // then
        assertInstanceOf(InMemoryMetricsLogger.class, resolved.logger());
        assertTrue(resolved.requirements().collection());
        assertFalse(resolved.requirements().cleanup());
    }

    @Test
    void shouldResolveFileLoggerNeedingBothDaemonsWhenKeyIsFile(@TempDir final File logDir) {
        // when
        final ResolvedLogger resolved =
                MetricsLoggerResolver.resolve("order-service", logDir, "file");

        // then
        assertInstanceOf(FileMetricsLogger.class, resolved.logger());
        assertTrue(resolved.requirements().collection());
        assertTrue(resolved.requirements().cleanup());
    }

    @Test
    void shouldResolveCaseInsensitively(@TempDir final File logDir) {
        // when
        final ResolvedLogger resolved =
                MetricsLoggerResolver.resolve("order-service", logDir, "INMEMORY");

        // then
        assertInstanceOf(InMemoryMetricsLogger.class, resolved.logger());
    }

    @Test
    void shouldFallBackToNoOpWhenKeyIsUnknown(@TempDir final File logDir) {
        // when
        final ResolvedLogger resolved =
                MetricsLoggerResolver.resolve("order-service", logDir, "does-not-exist");

        // then
        assertInstanceOf(NoOpMetricsLogger.class, resolved.logger());
        assertFalse(resolved.requirements().collection());
        assertFalse(resolved.requirements().cleanup());
    }

    /**
     * The only test in this class touching the real system property —
     * everything else exercises the pure, key-parameterized overload above.
     */
    @Nested
    class SystemPropertyPlumbing {

        private static final String PROPERTY = "metrics.implementation";

        @AfterEach
        void clearProperty() {
            System.clearProperty(PROPERTY);
        }

        @Test
        void shouldReadImplementationKeyFromSystemProperty(@TempDir final File logDir) {
            // given
            System.setProperty(PROPERTY, "inmemory");

            // when
            final ResolvedLogger resolved = MetricsLoggerResolver.resolve("order-service", logDir);

            // then
            assertInstanceOf(InMemoryMetricsLogger.class, resolved.logger());
        }
    }
}
