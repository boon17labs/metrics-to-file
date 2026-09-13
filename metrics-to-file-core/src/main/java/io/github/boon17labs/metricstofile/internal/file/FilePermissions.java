package io.github.boon17labs.metricstofile.internal.file;

import java.io.File;

/**
 * Restricts a file to owner-only read/write, approximating POSIX 600
 * via {@link java.io.File} (no {@code java.nio.file} dependency).
 * Best-effort — platforms that don't support per-owner permissions
 * simply ignore the calls, since {@code File}'s setters return a
 * boolean rather than throwing.
 */
public final class FilePermissions {

    private FilePermissions() {
    }

    public static void restrictToOwner(final File file) {
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        file.setExecutable(false, false);
    }
}
