package io.github.boon17labs.metricstofile;

import io.github.boon17labs.metricstofile.internal.buffer.TimestampedSample;
import io.github.boon17labs.metricstofile.internal.file.FilePermissions;
import io.github.boon17labs.metricstofile.internal.file.MetricLineFormatter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Writes each metric group as one line to a daily file under the
 * configured log directory. Never lets an I/O failure reach the caller —
 * on error it warns to stderr and the call is otherwise a no-op.
 */
public final class FileMetricsLogger implements MetricsLogger {

    private final String appName;
    private final File logDir;

    public FileMetricsLogger(final String appName, final File logDir) {
        this.appName = appName;
        this.logDir = logDir;
    }

    @Override
    public synchronized void log(final String type, final Map<String, Object> values) {
        try {
            final File file = prepareLogFile();
            final String line = MetricLineFormatter.format(
                    Instant.now(), appName, type, values);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (final IOException e) {
            System.err.println("[metrics-to-file] failed to write metrics: " + e.getMessage());
        }
    }

    /**
     * Writes every sample in one file open/write/close, each with its own
     * buffered timestamp rather than the moment of this call. Used by
     * {@code MetricsCollectionDaemon} to flush its buffer in a single write,
     * instead of one file open per sample.
     */
    public synchronized void logBatch(final List<TimestampedSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        try {
            final File file = prepareLogFile();
            try (FileWriter writer = new FileWriter(file, true)) {
                for (final TimestampedSample sample : samples) {
                    writer.write(MetricLineFormatter.format(
                            sample.timestamp(), appName, sample.type(), sample.values()));
                    writer.write(System.lineSeparator());
                }
            }
        } catch (final IOException e) {
            System.err.println("[metrics-to-file] failed to write metrics: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        // no-op — nothing to release yet
    }

    private File prepareLogFile() throws IOException {
        ensureLogDirExists();
        final File file = currentLogFile();
        if (file.createNewFile()) {
            FilePermissions.restrictToOwner(file);
        }
        return file;
    }

    private void ensureLogDirExists() throws IOException {
        if (!logDir.isDirectory() && !logDir.mkdirs() && !logDir.isDirectory()) {
            throw new IOException("could not create log directory: " + logDir);
        }
    }

    private File currentLogFile() {
        return new File(logDir, appName + "-" + LocalDate.now() + ".log");
    }
}
