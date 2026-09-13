package io.github.guranxp.metricstofile.internal.collect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryUnitsTest {

    @Test
    void shouldConvertBytesToMb() {
        assertEquals(1L, MemoryUnits.toMb(1024L * 1024L));
        assertEquals(2L, MemoryUnits.toMb(2L * 1024L * 1024L));
        assertEquals(0L, MemoryUnits.toMb(0L));
    }

    @Test
    void shouldConvertUndefinedValueToMinusOne() {
        assertEquals(-1L, MemoryUnits.toMb(-1L));
    }
}
