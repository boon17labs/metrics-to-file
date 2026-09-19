package io.github.boon17labs.metricstofile.prometheus.internal;

import io.github.boon17labs.metricstofile.internal.file.FilePermissions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Appends Prometheus sample lines to a daily file,
 * {@code <logDir>/<appName>-<yyyy-MM-dd>.prom}. The date in the file name
 * is the rotation: a new day means a new file. Files are restricted to
 * owner read/write on creation. Never lets an I/O failure reach the
 * caller — on error it warns to stderr and the call is otherwise a no-op.
 *
 * <p>Text is written as UTF-8, which is what the Prometheus text format
 * specifies, rather than the platform default charset.
 */
public final class PrometheusFileWriter {

    /** File name suffix of the daily files; the cleanup of old files must use the same one. */
    public static final String SUFFIX = ".prom";

    private final String appName;
    private final File logDir;

    public PrometheusFileWriter(final String appName, final File logDir) {
        this.appName = appName;
        this.logDir = logDir;
    }

    /**
     * Appends all lines, each terminated by a line separator, as one
     * write. {@code synchronized} so that batches from different threads
     * are never interleaved.
     */
    public synchronized void write(final List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try {
            ensureLogDirExists();
            final File file = currentFile();
            if (file.createNewFile()) {
                FilePermissions.restrictToOwner(file);
            }
            // Build the whole batch first so it reaches the file in a single write.
            final StringBuilder batch = new StringBuilder();
            for (final String line : lines) {
                batch.append(line).append(System.lineSeparator());
            }
            // "true" = append to the existing file instead of overwriting it.
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(batch.toString());
            }
        } catch (final IOException e) {
            System.err.println("[metrics-to-file] failed to write prometheus metrics: "
                    + e.getMessage());
        }
    }

    private void ensureLogDirExists() throws IOException {
        if (!logDir.isDirectory() && !logDir.mkdirs() && !logDir.isDirectory()) {
            throw new IOException("could not create log directory: " + logDir);
        }
    }

    private File currentFile() {
        return new File(logDir, appName + "-" + LocalDate.now() + SUFFIX);
    }
}
