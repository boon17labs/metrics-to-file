package io.github.boon17labs.metricstofile.internal.daemon;

/**
 * Daemon thread that repeats {@link #tick()} on a fixed interval until
 * {@link #shutdown()} is called, interrupting an in-progress sleep so
 * shutdown is prompt even with a long interval.
 *
 * <p>Public so other metrics-to-file modules can extend it. Like the
 * rest of {@code internal}, it is not public API.
 */
public abstract class IntervalDaemon extends Thread {

    private final long intervalMillis;
    private volatile boolean running = true;

    protected IntervalDaemon(final String name, final long intervalMillis) {
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

    protected abstract void tick();

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
