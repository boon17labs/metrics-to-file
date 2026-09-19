package io.github.boon17labs.metricstofile.prometheus.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a Prometheus text-format scrape into timestamped sample lines
 * suitable for appending to a daily history file. Comment lines
 * ({@code # HELP}, {@code # TYPE}) and blank lines are dropped, so
 * snapshots appended one after another never repeat metric metadata,
 * and every remaining sample gets the snapshot time appended in epoch
 * milliseconds, as the exposition format allows:
 * {@code name{labels} value timestamp}.
 *
 * <p>Works line by line: every sample in a scrape occupies exactly one
 * line, and the timestamp goes at its very end, so label values
 * containing spaces or braces need no parsing.
 */
public final class PrometheusSnapshotFormatter {

    private PrometheusSnapshotFormatter() {
    }

    public static List<String> format(final String scrape, final long timestampMillis) {
        final List<String> samples = new ArrayList<>();

        // Split the scrape into lines. The regex "\r?\n" (written "\\r?\\n" in Java)
        // means: an optional carriage return followed by a newline, so both Unix
        // ("\n") and Windows ("\r\n") line endings work. A trailing newline at the
        // end of the text adds no extra empty element: String.split drops trailing
        // empty strings.
        for (final String rawLine : scrape.split("\\r?\\n")) {

            // Remove leading and trailing whitespace, so a line containing only spaces
            // counts as blank below.
            final String line = rawLine.trim();

            // Skip blank lines and comment lines. In the Prometheus text format
            // every line starting with '#' is a comment (# HELP / # TYPE).
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // What is left is a sample line: name{labels} value. Append the
            // timestamp at the very end, giving: name{labels} value timestamp.
            // Adding it at the end means the labels never have to be parsed, so
            // label values containing spaces or braces are safe.
            samples.add(line + " " + timestampMillis);
        }
        return samples;
    }
}
