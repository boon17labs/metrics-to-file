package io.github.guranxp.metricstofile.internal.collect;

/**
 * Byte-to-megabyte conversion shared by memory-based collectors.
 */
final class MemoryUnits {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private MemoryUnits() {
    }

    static long toMb(final long bytes) {
        return bytes < 0 ? -1L : bytes / BYTES_PER_MB;
    }
}
