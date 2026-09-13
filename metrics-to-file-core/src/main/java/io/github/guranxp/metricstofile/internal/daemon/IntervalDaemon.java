package io.github.guranxp.metricstofile.internal.daemon;

/**
 * Daemon thread that repeats {@link #tick()} on a fixed interval until
 * {@link #shutdown()} is called, interrupting an in-progress sleep so
 * shutdown is prompt even with a long interval.
 */
abstract class IntervalDaemon extends Thread {

    private final long intervalMillis;
    private volatile boolean running = true;

    IntervalDaemon(final String name, final long intervalMillis) {
        super(name);
        this.intervalMillis = intervalMillis;
        setDaemon(true);
    }

    @Override
    public final void run() {
        while (running) {
            tick();
            sleepQuietly();
        }
    }

    abstract void tick();

    public final void shutdown() {
        running = false;
        interrupt();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(intervalMillis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
