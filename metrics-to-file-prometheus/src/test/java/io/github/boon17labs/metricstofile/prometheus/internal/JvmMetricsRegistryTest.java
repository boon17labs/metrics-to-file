package io.github.boon17labs.metricstofile.prometheus.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmMetricsRegistryTest {

    @Test
    void shouldTagEveryMeterWithApplicationName() {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");

        // when
        final String scrape = jvmRegistry.registry().scrape();

        // then
        int samples = 0;
        for (final String line : scrape.split("\\r?\\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            samples++;
            assertTrue(line.contains("application=\"order-service\""), line);
        }
        assertTrue(samples > 0);
        jvmRegistry.close();
    }

    @Test
    void shouldExposeHeapMemoryMetrics() {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");

        // when
        final String scrape = jvmRegistry.registry().scrape();

        // then
        assertTrue(scrape.contains(
                "jvm_memory_used_bytes{application=\"order-service\",area=\"heap\""));
        jvmRegistry.close();
    }

    @Test
    void shouldExposeThreadMetrics() {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");

        // when
        final String scrape = jvmRegistry.registry().scrape();

        // then
        assertTrue(scrape.contains("jvm_threads_live_threads{application=\"order-service\"}"));
        jvmRegistry.close();
    }

    @Test
    void shouldNotThrowWhenABinderFailsToBind() {
        // given
        final MeterBinder failing = registry -> {
            throw new IllegalStateException("boom");
        };

        // when / then
        assertDoesNotThrow(() ->
                new JvmMetricsRegistry("order-service", Collections.singletonList(failing)).close());
    }

    @Test
    void shouldStillBindOtherBindersWhenOneFailsToBind() {
        // given
        final MeterBinder failing = registry -> {
            throw new IllegalStateException("boom");
        };

        // when
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry(
                "order-service", Arrays.asList(failing, new JvmThreadMetrics()));

        // then
        assertTrue(jvmRegistry.registry().scrape().contains("jvm_threads_live_threads"));
        jvmRegistry.close();
    }

    @Test
    void shouldWarnOnStderrWhenABinderFailsToBind() {
        // given
        final MeterBinder failing = registry -> {
            throw new IllegalStateException("boom");
        };
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));

        // when
        try {
            new JvmMetricsRegistry("order-service", Collections.singletonList(failing)).close();
        } finally {
            System.setErr(originalErr);
        }

        // then
        final String stderr = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(stderr.contains("[metrics-to-file] failed to bind"));
        assertTrue(stderr.contains("boom"));
    }

    @Test
    void shouldCloseCloseableBindersOnClose() {
        // given
        final CountingBinder binder = new CountingBinder();
        final JvmMetricsRegistry jvmRegistry =
                new JvmMetricsRegistry("order-service", Collections.<MeterBinder>singletonList(binder));

        // when
        jvmRegistry.close();

        // then
        assertEquals(1, binder.closeCount);
    }

    @Test
    void shouldCloseTheRegistryOnClose() {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");

        // when
        jvmRegistry.close();

        // then
        assertTrue(jvmRegistry.registry().isClosed());
    }

    @Test
    void shouldCloseBindersOnlyOnceWhenClosedTwice() {
        // given
        final CountingBinder binder = new CountingBinder();
        final JvmMetricsRegistry jvmRegistry =
                new JvmMetricsRegistry("order-service", Collections.<MeterBinder>singletonList(binder));

        // when
        jvmRegistry.close();
        jvmRegistry.close();

        // then
        assertEquals(1, binder.closeCount);
    }

    @Test
    void shouldStillCloseRemainingBindersWhenOneFailsToClose() {
        // given
        final CountingBinder failsToClose = new CountingBinder();
        failsToClose.failOnClose = true;
        final CountingBinder other = new CountingBinder();
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry(
                "order-service", Arrays.<MeterBinder>asList(failsToClose, other));

        // when / then
        assertDoesNotThrow(jvmRegistry::close);
        assertEquals(1, other.closeCount);
        assertTrue(jvmRegistry.registry().isClosed());
    }

    /** A binder that binds nothing and counts how often it is closed. */
    private static final class CountingBinder implements MeterBinder, AutoCloseable {

        int closeCount;
        boolean failOnClose;

        @Override
        public void bindTo(final MeterRegistry registry) {
            // binds nothing
        }

        @Override
        public void close() {
            closeCount++;
            if (failOnClose) {
                throw new IllegalStateException("cannot close");
            }
        }
    }
}
