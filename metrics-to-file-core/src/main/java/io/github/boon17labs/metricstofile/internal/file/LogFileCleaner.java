package io.github.boon17labs.metricstofile.internal.file;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Deletes log files older than the configured retention period. File
 * names are expected in the form {@code <appName>-<yyyy-MM-dd><suffix>},
 * where the suffix defaults to {@code .log}, matching what
 * {@code FileMetricsLogger} writes. Files with any other suffix are left
 * alone. Never throws — a missing directory or an unexpected file name
 * is silently skipped.
 */
public final class LogFileCleaner {

    private static final String DEFAULT_SUFFIX = ".log";

    private LogFileCleaner() {
    }

    public static void clean(final File logDir, final String appName, final int keepDays) {
        clean(logDir, appName, keepDays, DEFAULT_SUFFIX);
    }

    public static void clean(final File logDir, final String appName, final int keepDays,
            final String suffix) {
        final File[] files = logDir.listFiles();
        if (files == null) {
            return;
        }
        final String prefix = appName + "-";
        final LocalDate cutoff = LocalDate.now().minusDays(keepDays);
        for (final File file : files) {
            final LocalDate fileDate = parseDate(file.getName(), prefix, suffix);
            if (fileDate != null && fileDate.isBefore(cutoff)) {
                file.delete();
            }
        }
    }

    private static LocalDate parseDate(final String fileName, final String prefix,
            final String suffix) {
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return null;
        }
        final String datePart =
                fileName.substring(prefix.length(), fileName.length() - suffix.length());
        try {
            return LocalDate.parse(datePart);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
