package io.github.boon17labs.metricstofile.internal.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads this process's memory usage as the operating system sees it.
 *
 * <p>{@code rss_mb} is true resident set size (VmRSS), read from
 * {@code /proc/self/status} — present only on Linux — and is only ever
 * logged when that read succeeds. It is <em>not</em> approximated on other
 * platforms, because the one fallback available without an external
 * dependency, {@code com.sun.management.OperatingSystemMXBean
 * #getCommittedVirtualMemorySize()} (see CLAUDE.md for why this
 * HotSpot-specific extension is allowed here, alongside the CPU opt-in),
 * reports something quantitatively different: total committed virtual
 * address space, not physical footprint. On macOS in particular this
 * routinely comes out 100-1000x larger than actual physical memory used
 * (heap reservations, thread stack reservations, mmap'd regions, the CDS
 * archive, etc. all count), so logging it under the same {@code rss_mb}
 * key would silently mislead anyone using it to compare memory usage
 * across apps. Instead it's logged under its own honestly-named
 * {@code committed_vmem_mb} key on whichever non-Linux platforms have a
 * HotSpot-derived JVM.
 *
 * <p>Trying the Linux source unconditionally rather than branching on
 * {@code os.name} means a Linux JVM without {@code /proc} (unusual, but
 * not impossible in some containers) also degrades to the fallback
 * correctly. When neither source is available (a non-Linux, non-HotSpot
 * JVM), the log line carries neither key — a missing field is less
 * misleading here than the library's usual {@code -1} not-available
 * sentinel would be, since {@code -1} next to a memory-sized field reads
 * as a plausible small measurement, not clearly as "not available".
 */
public final class ProcessMetricsCollector implements MetricsCollector {

    private static final File PROC_STATUS_FILE = new File("/proc/self/status");
    private static final String RSS_FIELD_PREFIX = "VmRSS:";

    @Override
    public String type() {
        return "process";
    }

    @Override
    public List<Map<String, Object>> collect() {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Long rssMb = readLinuxRssMb();
        if (rssMb != null) {
            values.put("rss_mb", rssMb);
        } else {
            final Long committedVmemMb = readCommittedVirtualMemoryMb();
            if (committedVmemMb != null) {
                values.put("committed_vmem_mb", committedVmemMb);
            }
        }
        return Collections.singletonList(values);
    }

    private static Long readLinuxRssMb() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_STATUS_FILE))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith(RSS_FIELD_PREFIX)) {
                    return parseKbFieldToMb(line);
                }
                line = reader.readLine();
            }
        } catch (final IOException | NumberFormatException e) {
            // fall through to the com.sun.management fallback — never let a
            // metrics read affect the host app
        }
        return null;
    }

    private static Long parseKbFieldToMb(final String vmRssLine) {
        // Kernel format: "VmRSS:\t   12345 kB" — pull out the digits between
        // the label and the trailing unit rather than assuming exact spacing.
        final String digitsOnly = vmRssLine.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) {
            return null;
        }
        return Long.parseLong(digitsOnly) / 1024L;
    }

    @SuppressWarnings("deprecation")
    private static Long readCommittedVirtualMemoryMb() {
        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (!(osBean instanceof com.sun.management.OperatingSystemMXBean)) {
            return null;
        }
        final long committedBytes =
                ((com.sun.management.OperatingSystemMXBean) osBean).getCommittedVirtualMemorySize();
        return committedBytes < 0 ? null : MemoryUnits.toMb(committedBytes);
    }
}
