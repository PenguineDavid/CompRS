package io.github.penguinedavid.comprs.common.util;

/**
 * Pure logic for comparator-signal barrels: parsing the requested strength and
 * working out how much redstone a barrel needs so a comparator reads it.
 * No Minecraft classes here so it can be unit tested directly.
 *
 * A comparator reads a container as floor(1 + fullness * 14) for a non-empty
 * container, where fullness is the sum of (count / maxStackSize) over all slots,
 * divided by the slot count. Redstone stacks to 64 and a barrel has 27 slots, so
 * strength s (1-14) needs at least (s - 1) / 14 of 27 * 64 items, and 15 needs
 * the barrel completely full.
 */
public final class BarrelSignal {

    public static final int MIN_SIGNAL = 1;
    public static final int MAX_SIGNAL = 15;
    public static final int BARREL_SLOTS = 27;
    public static final int REDSTONE_STACK_SIZE = 64;
    public static final int OVERLOADED_MIN_SIGNAL = 16;
    public static final int OVERLOADED_MAX_SIGNAL = REDSTONE_STACK_SIZE * 14 + 1;

    private BarrelSignal() {
    }

    /** Thrown when the signal strength is not a whole number in 1-15. */
    public static class BarrelSignalException extends RuntimeException {
        public BarrelSignalException(String message) {
            super(message);
        }
    }

    /** Parses and range-checks a signal strength typed by the player. */
    public static int parse(String text) {
        int signal;
        try {
            signal = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new BarrelSignalException("'" + text + "' is not a whole number. Usage: ,barrel <signal strength "
                + MIN_SIGNAL + "-" + MAX_SIGNAL + ">");
        }
        if (signal < MIN_SIGNAL || signal > MAX_SIGNAL) {
            throw new BarrelSignalException("Signal strength must be between " + MIN_SIGNAL + " and " + MAX_SIGNAL);
        }
        return signal;
    }

    /** Total redstone items needed in a barrel for a comparator to read exactly {@code signal}. */
    public static int redstoneItemCount(int signal) {
        if (signal < MIN_SIGNAL || signal > MAX_SIGNAL) {
            throw new IllegalArgumentException("signal out of range: " + signal);
        }
        int capacity = BARREL_SLOTS * REDSTONE_STACK_SIZE;
        if (signal == MAX_SIGNAL) {
            return capacity;
        }
        return Math.max(1, (int) Math.ceil((signal - 1) * (double) capacity / 14.0));
    }

    /** The signal a comparator would read for a barrel holding {@code items} redstone. Used to verify the math. */
    public static int comparatorSignal(int items) {
        if (items <= 0) {
            return 0;
        }
        double fullness = items / (double) (BARREL_SLOTS * REDSTONE_STACK_SIZE);
        return (int) Math.floor(1 + fullness * 14);
    }

    public static int parseOverloaded(String text) {
        int signal;
        try {
            signal = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new BarrelSignalException("'" + text + "' is not a whole number. Signal must be between "
                + OVERLOADED_MIN_SIGNAL + " and " + OVERLOADED_MAX_SIGNAL);
        }
        if (signal < OVERLOADED_MIN_SIGNAL || signal > OVERLOADED_MAX_SIGNAL) {
            throw new BarrelSignalException("Signal must be between " + OVERLOADED_MIN_SIGNAL + " and "
                + OVERLOADED_MAX_SIGNAL);
        }
        if (overloadedItemCountOrZero(signal) == 0) {
            throw new BarrelSignalException("Signal " + signal + " is not reachable with a 27-slot barrel");
        }
        return signal;
    }

    public static int overloadedItemCount(int signal) {
        if (signal < OVERLOADED_MIN_SIGNAL || signal > OVERLOADED_MAX_SIGNAL) {
            throw new IllegalArgumentException("signal out of range: " + signal);
        }
        int itemCount = overloadedItemCountOrZero(signal);
        if (itemCount == 0) {
            throw new IllegalArgumentException("signal is not reachable: " + signal);
        }
        return itemCount;
    }

    public static int overloadedComparatorSignal(int items) {
        if (items <= 0) {
            return 0;
        }
        float fullness = items / (float) BARREL_SLOTS;
        return (int) Math.floor(1 + fullness * 14);
    }

    private static int overloadedItemCountOrZero(int signal) {
        int capacity = BARREL_SLOTS * REDSTONE_STACK_SIZE;
        for (int items = 1; items <= capacity; items++) {
            if (overloadedComparatorSignal(items) == signal) {
                return items;
            }
        }
        return 0;
    }
}