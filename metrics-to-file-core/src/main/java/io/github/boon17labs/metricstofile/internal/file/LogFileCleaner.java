package io.github.boon17labs.metricstofile.internal.file;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deletes log files older than the configured retention period, and
 * (if a maximum total size is set) the oldest surviving files beyond
 * that size. File names are expected in the form
 * {@code <appName>-<yyyy-MM-dd><suffix>}, where the suffix defaults to
 * {@code .log}, matching what {@code FileMetricsLogger} writes. Files
 * with any other suffix or an unparseable date are left alone. Never
 * throws — a missing directory or an unexpected file name is silently
 * skipped.
 */
public final class LogFileCleaner {

    private static final String DEFAULT_SUFFIX = ".log";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private LogFileCleaner() {
    }

    public static void clean(final File logDir, final String appName, final int keepDays) {
        clean(logDir, appName, keepDays, DEFAULT_SUFFIX);
    }

    public static void clean(final File logDir, final String appName, final int keepDays,
            final String suffix) {
        clean(logDir, appName, keepDays, 0L, suffix);
    }

    /**
     * @param maxSizeMb maximum total size, in MB, of the surviving files
     *                  (after age-based deletion); {@code 0} disables
     *                  this check
     */
    public static void clean(final File logDir, final String appName, final int keepDays,
            final long maxSizeMb, final String suffix) {
        final File[] files = logDir.listFiles();
        if (files == null) {
            return;
        }
        final String prefix = appName + "-";
        final LocalDate cutoff = LocalDate.now().minusDays(keepDays);
        final List<DatedFile> survivors = new ArrayList<>();
        for (final File file : files) {
            final LocalDate fileDate = parseDate(file.getName(), prefix, suffix);
            if (fileDate == null) {
                continue;
            }
            if (fileDate.isBefore(cutoff)) {
                file.delete();
            } else {
                survivors.add(new DatedFile(file, fileDate));
            }
        }
        if (maxSizeMb > 0) {
            deleteOldestUntilUnderCap(survivors, maxSizeMb * BYTES_PER_MB);
        }
    }

    private static void deleteOldestUntilUnderCap(final List<DatedFile> files, final long maxBytes) {
        files.sort(Comparator.comparing(DatedFile::date));
        long totalBytes = 0L;
        for (final DatedFile file : files) {
            totalBytes += file.file().length();
        }
        for (final DatedFile file : files) {
            if (totalBytes <= maxBytes) {
                return;
            }
            final long size = file.file().length();
            if (file.file().delete()) {
                totalBytes -= size;
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

    private static final class DatedFile {

        private final File file;
        private final LocalDate date;

        DatedFile(final File file, final LocalDate date) {
            this.file = file;
            this.date = date;
        }

        File file() {
            return file;
        }

        LocalDate date() {
            return date;
        }
    }
}
