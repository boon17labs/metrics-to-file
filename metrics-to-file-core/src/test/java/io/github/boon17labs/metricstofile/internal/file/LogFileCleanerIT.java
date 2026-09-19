package io.github.boon17labs.metricstofile.internal.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileCleanerIT {

    private static final int KEEP_DAYS = 7;

    @Test
    void shouldDeleteFilesOlderThanKeepDays(@TempDir final File logDir) throws IOException {
        // given
        final File oldFile = fileFor(logDir, "order-service", LocalDate.now().minusDays(10));

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS);

        // then
        assertFalse(oldFile.exists());
    }

    @Test
    void shouldKeepFilesWithinKeepDays(@TempDir final File logDir) throws IOException {
        // given
        final File recentFile = fileFor(logDir, "order-service", LocalDate.now().minusDays(3));

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS);

        // then
        assertTrue(recentFile.exists());
    }

    @Test
    void shouldKeepFilesBelongingToOtherAppNames(@TempDir final File logDir) throws IOException {
        // given
        final File otherAppOldFile =
                fileFor(logDir, "other-service", LocalDate.now().minusDays(30));

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS);

        // then
        assertTrue(otherAppOldFile.exists());
    }

    @Test
    void shouldIgnoreFilesNotMatchingNamingPattern(@TempDir final File logDir) throws IOException {
        // given
        final File unrelatedFile = new File(logDir, "readme.txt");
        assertTrue(unrelatedFile.createNewFile());

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS);

        // then
        assertTrue(unrelatedFile.exists());
    }

    @Test
    void shouldDeleteOldFilesWithGivenSuffix(@TempDir final File logDir) throws IOException {
        // given
        final File oldFile =
                fileFor(logDir, "order-service", LocalDate.now().minusDays(10), ".prom");

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, ".prom");

        // then
        assertFalse(oldFile.exists());
    }

    @Test
    void shouldKeepOldFilesWithOtherSuffixWhenSuffixIsGiven(@TempDir final File logDir)
            throws IOException {
        // given
        final File oldLogFile = fileFor(logDir, "order-service", LocalDate.now().minusDays(10));

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, ".prom");

        // then
        assertTrue(oldLogFile.exists());
    }

    @Test
    void shouldKeepOldFilesWithOtherSuffixWhenSuffixIsDefaulted(@TempDir final File logDir)
            throws IOException {
        // given
        final File oldPromFile =
                fileFor(logDir, "order-service", LocalDate.now().minusDays(10), ".prom");

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS);

        // then
        assertTrue(oldPromFile.exists());
    }

    @Test
    void shouldNotThrowWhenDirectoryDoesNotExist(@TempDir final File tempDir) {
        // given
        final File missingDir = new File(tempDir, "does-not-exist");

        // when / then
        assertDoesNotThrow(() -> LogFileCleaner.clean(missingDir, "order-service", KEEP_DAYS));
    }

    @Test
    void shouldNotThrowWhenDirectoryIsEmpty(@TempDir final File logDir) {
        assertDoesNotThrow(() -> LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS));
    }

    private static File fileFor(final File logDir, final String appName, final LocalDate date)
            throws IOException {
        return fileFor(logDir, appName, date, ".log");
    }

    private static File fileFor(final File logDir, final String appName, final LocalDate date,
            final String suffix) throws IOException {
        final File file = new File(logDir, appName + "-" + date + suffix);
        assertTrue(file.createNewFile());
        return file;
    }
}
