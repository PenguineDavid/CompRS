package io.github.penguinedavid.comprs.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrelSignalTest {

    @Test
    void parseAcceptsEveryValidStrengthAndIgnoresSurroundingWhitespace() {
        for (int signal = BarrelSignal.MIN_SIGNAL; signal <= BarrelSignal.MAX_SIGNAL; signal++) {
            assertEquals(signal, BarrelSignal.parse(String.valueOf(signal)));
        }
        assertEquals(7, BarrelSignal.parse("  7 "));
    }

    @Test
    void parseRejectsOutOfRangeValues() {
        for (String bad : new String[] {"0", "16", "-1", "100"}) {
            BarrelSignal.BarrelSignalException e =
                assertThrows(BarrelSignal.BarrelSignalException.class, () -> BarrelSignal.parse(bad));
            assertTrue(e.getMessage().contains("between 1 and 15"), "message for " + bad + ": " + e.getMessage());
        }
    }

    @Test
    void parseRejectsNonWholeNumbers() {
        for (String bad : new String[] {"abc", "7.5", "", "1e1", "0x7"}) {
            assertThrows(BarrelSignal.BarrelSignalException.class, () -> BarrelSignal.parse(bad));
        }
    }

    @Test
    void redstoneCountMakesAComparatorReadExactlyTheRequestedStrength() {
        for (int signal = BarrelSignal.MIN_SIGNAL; signal <= BarrelSignal.MAX_SIGNAL; signal++) {
            int items = BarrelSignal.redstoneItemCount(signal);
            assertEquals(signal, BarrelSignal.comparatorSignal(items), "signal " + signal);
        }
    }

    @Test
    void redstoneCountIsTheMinimumNeededForEachStrength() {
        for (int signal = BarrelSignal.MIN_SIGNAL; signal <= BarrelSignal.MAX_SIGNAL; signal++) {
            int items = BarrelSignal.redstoneItemCount(signal);
            assertEquals(signal - 1, BarrelSignal.comparatorSignal(items - 1),
                "one fewer item should drop below signal " + signal);
        }
    }

    @Test
    void redstoneCountNeverExceedsBarrelCapacityAndMaxStrengthFillsIt() {
        int capacity = BarrelSignal.BARREL_SLOTS * BarrelSignal.REDSTONE_STACK_SIZE;
        for (int signal = BarrelSignal.MIN_SIGNAL; signal <= BarrelSignal.MAX_SIGNAL; signal++) {
            assertTrue(BarrelSignal.redstoneItemCount(signal) <= capacity);
        }
        assertEquals(capacity, BarrelSignal.redstoneItemCount(BarrelSignal.MAX_SIGNAL));
    }

    @Test
    void redstoneCountRejectsOutOfRangeSignals() {
        assertThrows(IllegalArgumentException.class, () -> BarrelSignal.redstoneItemCount(0));
        assertThrows(IllegalArgumentException.class, () -> BarrelSignal.redstoneItemCount(16));
    }
}