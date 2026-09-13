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
        System.clearProperty("metrics.interval");
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
    void shouldReturnExplicitIntervalWhenSet() {
        final Duration explicit = Duration.ofMillis(20L);
        assertEquals(explicit, BuilderProperties.interval(explicit));
    }

    @Test
    void shouldReturnPropertyIntervalWhenExplicitNotSet() {
        System.setProperty("metrics.interval", "5");
        assertEquals(Duration.ofMinutes(5L), BuilderProperties.interval(null));
    }

    @Test
    void shouldReturnDefaultIntervalWhenNeitherSet() {
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.interval(null));
    }

    @Test
    void shouldReturnDefaultIntervalWhenPropertyIsNotANumber() {
        System.setProperty("metrics.interval", "not-a-number");
        assertEquals(Duration.ofMinutes(60L), BuilderProperties.interval(null));
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
