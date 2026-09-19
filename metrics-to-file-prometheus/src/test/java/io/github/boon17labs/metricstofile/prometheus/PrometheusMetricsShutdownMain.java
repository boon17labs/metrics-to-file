package io.github.boon17labs.metricstofile.prometheus;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Test helper run in a child JVM by {@code PrometheusMetricsShutdownHookIT}.
 * Starts {@link PrometheusMetrics} and then exits without ever calling
 * {@code stop()}, so the only thing that can stop it is its JVM shutdown hook.
 * Exits with status 2 if the first snapshot never appears.
 */
public final class PrometheusMetricsShutdownMain {

    private static final long TIMEOUT_MILLIS = 5_000L;

    private PrometheusMetricsShutdownMain() {
    }

    public static void main(final String[] args) throws InterruptedException {
        final String logDir = args[0];
        PrometheusMetrics.builder()
                .appName("order-service")
                .logDir(logDir)
                .interval(Duration.ofMillis(20L))
                .start();

        // Wait for the first snapshot so the instance is demonstrably running.
        final File file = new File(logDir, "order-service-" + LocalDate.now() + ".prom");
        final long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!file.isFile()) {
            if (System.currentTimeMillis() > deadline) {
                System.exit(2);
            }
            Thread.sleep(5L);
        }
        // Falling off the end of main: the JVM exits and runs its shutdown hooks.
    }
}
