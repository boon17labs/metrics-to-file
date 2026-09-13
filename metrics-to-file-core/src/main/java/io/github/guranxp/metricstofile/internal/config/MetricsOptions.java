package io.github.guranxp.metricstofile.internal.config;

/**
 * Which opt-in metrics {@link MetricsCollectionDaemon} should collect,
 * in addition to the default set (heap, threads, metaspace, GC).
 */
public final class MetricsOptions {

    private final boolean directMemory;
    private final boolean classLoading;
    private final boolean cpu;
    private final boolean codeCache;

    public MetricsOptions(final boolean directMemory, final boolean classLoading,
            final boolean cpu, final boolean codeCache) {
        this.directMemory = directMemory;
        this.classLoading = classLoading;
        this.cpu = cpu;
        this.codeCache = codeCache;
    }

    public static MetricsOptions defaults() {
        return new MetricsOptions(false, false, false, false);
    }

    public boolean directMemory() {
        return directMemory;
    }

    public boolean classLoading() {
        return classLoading;
    }

    public boolean cpu() {
        return cpu;
    }

    public boolean codeCache() {
        return codeCache;
    }
}
