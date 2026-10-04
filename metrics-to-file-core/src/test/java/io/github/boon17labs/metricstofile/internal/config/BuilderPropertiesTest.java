package io.github.boon17labs.metricstofile.internal.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderPropertiesTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("metrics.log.dir");
        System.clearProperty("metrics.write.interval");
        System.clearProperty("metrics.sample.interval");
        System.clearProperty("metrics.keep.days");
        System.clearProperty("metrics.opt.direct");
    }

    @Test
    void shouldReturnExplicitLogDirWhenSet() {
        assertEquals(new File("/explicit"), BuilderProperties.logDir(new File("/explicit")));
    }

    @Test
    void shouldReturnPropertyLogDirWhenExplicitNotSet() {
        System.setProperty("metrics.log.dir", "/from-property");
        assertEquals(new File("/from-property"), BuilderProperties.logDir(null));
    }

    @Test
    void shouldReturnDefaultLogDirWhenNeitherSet() {
        assertEquals(new File("./metrics"), BuilderProperties.logDir(null));
    }

    @Test
    void shouldReturnExplicitWriteIntervalWhenSet() {
        final Duration explicit = Duration.ofMillis(20L);
        assertEquals(explicit, BuilderProperties.writeInterval(explicit));
    }

    @Test
    void shouldReturnPropertyWriteIntervalWhenExplicitNotSet() {
        System.setProperty("metrics.write.interval", "5");
        assertEquals(Duration.ofMinutes(5L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenNeitherSet() {
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenPropertyIsNotANumber() {
        System.setProperty("metrics.write.interval", "not-a-number");
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldParseMillisecondsSuffixForWriteInterval() {
        System.setProperty("metrics.write.interval", "500ms");
        assertEquals(Duration.ofMillis(500L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldParseSecondsSuffixForWriteInterval() {
        System.setProperty("metrics.write.interval", "1s");
        assertEquals(Duration.ofSeconds(1L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldParseMinutesSuffixForWriteInterval() {
        System.setProperty("metrics.write.interval", "2m");
        assertEquals(Duration.ofMinutes(2L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenPropertyIsZero() {
        System.setProperty("metrics.write.interval", "0");
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenPropertyIsNegative() {
        System.setProperty("metrics.write.interval", "-1s");
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(null));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenExplicitIsZero() {
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(Duration.ZERO));
    }

    @Test
    void shouldReturnDefaultWriteIntervalWhenExplicitIsNegative() {
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.writeInterval(Duration.ofSeconds(-1L)));
    }

    @Test
    void shouldReturnExplicitSampleIntervalWhenSet() {
        final Duration explicit = Duration.ofMillis(5L);
        assertEquals(explicit, BuilderProperties.sampleInterval(explicit, Duration.ofMinutes(5L)));
    }

    @Test
    void shouldReturnPropertySampleIntervalWhenExplicitNotSet() {
        System.setProperty("metrics.sample.interval", "5s");
        assertEquals(Duration.ofSeconds(5L),
                BuilderProperties.sampleInterval(null, Duration.ofMinutes(5L)));
    }

    @Test
    void shouldFallBackToWriteIntervalWhenSampleIntervalNeitherSet() {
        assertEquals(Duration.ofMinutes(5L),
                BuilderProperties.sampleInterval(null, Duration.ofMinutes(5L)));
    }

    @Test
    void shouldFallBackToWriteIntervalWhenSamplePropertyIsInvalid() {
        System.setProperty("metrics.sample.interval", "not-a-number");
        assertEquals(Duration.ofMinutes(5L),
                BuilderProperties.sampleInterval(null, Duration.ofMinutes(5L)));
    }

    @Test
    void shouldFallBackToWriteIntervalWhenSamplePropertyIsZero() {
        System.setProperty("metrics.sample.interval", "0");
        assertEquals(Duration.ofMinutes(5L),
                BuilderProperties.sampleInterval(null, Duration.ofMinutes(5L)));
    }

    @Test
    void shouldFallBackToWriteIntervalWhenExplicitSampleIntervalIsNegative() {
        assertEquals(Duration.ofMinutes(5L),
                BuilderProperties.sampleInterval(Duration.ofSeconds(-1L), Duration.ofMinutes(5L)));
    }

    @Test
    void shouldReturnExplicitKeepDaysWhenSet() {
        assertEquals(14, BuilderProperties.keepDays(14));
    }

    @Test
    void shouldReturnPropertyKeepDaysWhenExplicitNotSet() {
        System.setProperty("metrics.keep.days", "30");
        assertEquals(30, BuilderProperties.keepDays(null));
    }

    @Test
    void shouldReturnDefaultKeepDaysWhenNeitherSet() {
        assertEquals(7, BuilderProperties.keepDays(null));
    }

    @Test
    void shouldReturnDefaultKeepDaysWhenPropertyIsNotANumber() {
        System.setProperty("metrics.keep.days", "not-a-number");
        assertEquals(7, BuilderProperties.keepDays(null));
    }

    @Test
    void shouldReturnExplicitFlagWhenSet() {
        assertTrue(BuilderProperties.flag(true, "metrics.opt.direct"));
        assertFalse(BuilderProperties.flag(false, "metrics.opt.direct"));
    }

    @Test
    void shouldReturnPropertyFlagWhenExplicitNotSet() {
        System.setProperty("metrics.opt.direct", "true");
        assertTrue(BuilderProperties.flag(null, "metrics.opt.direct"));
    }

    @Test
    void shouldReturnFalseFlagWhenNeitherSet() {
        assertFalse(BuilderProperties.flag(null, "metrics.opt.direct"));
    }
}
