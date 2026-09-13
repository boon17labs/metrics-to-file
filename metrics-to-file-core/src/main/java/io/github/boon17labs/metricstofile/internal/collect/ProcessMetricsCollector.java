package io.github.boon17labs.metricstofile.internal.collect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads this process's resident set size (RSS) — total memory as the
 * operating system sees it (heap + off-heap + native + everything
 * else) — from {@code /proc/self/status} on Linux. There is no
 * dependency-free, standard Java API for RSS (it isn't exposed via
 * {@code java.lang.management} on any platform), so every other OS
 * (macOS, Windows) falls back to the library's usual {@code -1}
 * not-available sentinel rather than reaching for a native/JNI
 * library, consistent with the no-external-dependencies rule for this
 * module (see CLAUDE.md).
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
        values.put("rss_mb", isLinux() ? readRssMb() : -1L);
        return Collections.singletonList(values);
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static long readRssMb() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_STATUS_FILE))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith(RSS_FIELD_PREFIX)) {
                    return parseKbFieldToMb(line);
                }
                line = reader.readLine();
            }
        } catch (final IOException | NumberFormatException e) {
            // fall through — never let a metrics read affect the host app
        }
        return -1L;
    }

    private static long parseKbFieldToMb(final String vmRssLine) {
        // Kernel format: "VmRSS:\t   12345 kB" — pull out the digits between
        // the label and the trailing unit rather than assuming exact spacing.
        final String digitsOnly = vmRssLine.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) {
            return -1L;
        }
        return Long.parseLong(digitsOnly) / 1024L;
    }
}
