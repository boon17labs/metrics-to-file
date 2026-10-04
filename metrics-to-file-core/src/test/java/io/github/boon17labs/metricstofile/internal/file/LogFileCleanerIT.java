package io.github.boon17labs.metricstofile.internal.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
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
    void shouldDeleteOldestSurvivingFilesWhenOverSizeCap(@TempDir final File logDir)
            throws IOException {
        // given: three 400 KB files; a 1 MB cap can only hold two
        final File oldest =
                fileOfSize(logDir, "order-service", LocalDate.now().minusDays(2), 400_000);
        final File middle =
                fileOfSize(logDir, "order-service", LocalDate.now().minusDays(1), 400_000);
        final File newest = fileOfSize(logDir, "order-service", LocalDate.now(), 400_000);

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, 1L, ".log");

        // then
        assertFalse(oldest.exists());
        assertTrue(middle.exists());
        assertTrue(newest.exists());
    }

    @Test
    void shouldKeepFilesWhenUnderSizeCap(@TempDir final File logDir) throws IOException {
        // given
        final File file = fileOfSize(logDir, "order-service", LocalDate.now(), 100);

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, 1L, ".log");

        // then
        assertTrue(file.exists());
    }

    @Test
    void shouldNotApplySizeCapWhenDisabled(@TempDir final File logDir) throws IOException {
        // given: well over any reasonable cap, but maxSizeMb=0 means disabled
        final File oldest =
                fileOfSize(logDir, "order-service", LocalDate.now().minusDays(2), 2_000_000);
        final File newest = fileOfSize(logDir, "order-service", LocalDate.now(), 2_000_000);

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, 0L, ".log");

        // then
        assertTrue(oldest.exists());
        assertTrue(newest.exists());
    }

    @Test
    void shouldNotCountOtherAppNamesOrSuffixesAgainstTheSizeCap(@TempDir final File logDir)
            throws IOException {
        // given
        final File otherAppLargeFile =
                fileOfSize(logDir, "other-service", LocalDate.now().minusDays(2), 2_000_000);
        final File otherSuffixLargeFile =
                fileOfSize(logDir, "order-service", LocalDate.now().minusDays(2), 2_000_000, ".prom");
        final File thisAppSmallFile = fileOfSize(logDir, "order-service", LocalDate.now(), 100);

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, 1L, ".log");

        // then
        assertTrue(otherAppLargeFile.exists());
        assertTrue(otherSuffixLargeFile.exists());
        assertTrue(thisAppSmallFile.exists());
    }

    @Test
    void shouldApplyAgeBasedDeletionBeforeTheSizeCap(@TempDir final File logDir)
            throws IOException {
        // given: an old file that age-based cleanup removes on its own, well within the cap
        final File oldFile =
                fileOfSize(logDir, "order-service", LocalDate.now().minusDays(10), 100);
        final File recentFile = fileOfSize(logDir, "order-service", LocalDate.now(), 100);

        // when
        LogFileCleaner.clean(logDir, "order-service", KEEP_DAYS, 1L, ".log");

        // then
        assertFalse(oldFile.exists());
        assertTrue(recentFile.exists());
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

    private static File fileOfSize(final File logDir, final String appName, final LocalDate date,
            final int sizeBytes) throws IOException {
        return fileOfSize(logDir, appName, date, sizeBytes, ".log");
    }

    private static File fileOfSize(final File logDir, final String appName, final LocalDate date,
            final int sizeBytes, final String suffix) throws IOException {
        final File file = new File(logDir, appName + "-" + date + suffix);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[sizeBytes]);
        }
        return file;
    }
}
