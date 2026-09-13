package io.github.guranxp.metricstofile.internal.file;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Deletes log files older than the configured retention period. File
 * names are expected in the form {@code <appName>-<yyyy-MM-dd>.log},
 * matching what {@code FileMetricsLogger} writes. Never throws —
 * a missing directory or an unexpected file name is silently skipped.
 */
public final class LogFileCleaner {

    private static final String SUFFIX = ".log";

    private LogFileCleaner() {
    }

    public static void clean(final File logDir, final String appName, final int keepDays) {
        final File[] files = logDir.listFiles();
        if (files == null) {
            return;
        }
        final String prefix = appName + "-";
        final LocalDate cutoff = LocalDate.now().minusDays(keepDays);
        for (final File file : files) {
            final LocalDate fileDate = parseDate(file.getName(), prefix);
            if (fileDate != null && fileDate.isBefore(cutoff)) {
                file.delete();
            }
        }
    }

    private static LocalDate parseDate(final String fileName, final String prefix) {
        if (!fileName.startsWith(prefix) || !fileName.endsWith(SUFFIX)) {
            return null;
        }
        final String datePart =
                fileName.substring(prefix.length(), fileName.length() - SUFFIX.length());
        try {
            return LocalDate.parse(datePart);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
