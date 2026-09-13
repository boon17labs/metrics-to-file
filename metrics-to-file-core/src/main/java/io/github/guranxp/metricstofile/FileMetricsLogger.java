package io.github.guranxp.metricstofile;

import io.github.guranxp.metricstofile.internal.file.FilePermissions;
import io.github.guranxp.metricstofile.internal.file.MetricLineFormatter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
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
            ensureLogDirExists();
            final File file = currentLogFile();
            if (file.createNewFile()) {
                FilePermissions.restrictToOwner(file);
            }
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

    @Override
    public void close() {
        // no-op — nothing to release yet
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
