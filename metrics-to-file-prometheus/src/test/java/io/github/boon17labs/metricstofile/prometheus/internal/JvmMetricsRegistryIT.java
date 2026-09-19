package io.github.boon17labs.metricstofile.prometheus.internal;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmMetricsRegistryIT {

    private static final long POLL_TIMEOUT_MILLIS = 5_000L;

    @Test
    void shouldExposeGcPauseMetricsAfterAGarbageCollection() throws InterruptedException {
        // given
        final JvmMetricsRegistry jvmRegistry = new JvmMetricsRegistry("order-service");

        // when: GC notifications arrive asynchronously on a JMX thread, so poll for them
        try {
            System.gc();
            final boolean seen = waitUntil(() -> jvmRegistry.registry().scrape().contains(
                    "jvm_gc_pause_seconds_count{action="));

            // then
            assertTrue(seen, "no jvm_gc_pause_seconds_count sample appeared after System.gc()");
        } finally {
            jvmRegistry.close();
        }
    }

    private static boolean waitUntil(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(10L);
        }
        return true;
    }
}
